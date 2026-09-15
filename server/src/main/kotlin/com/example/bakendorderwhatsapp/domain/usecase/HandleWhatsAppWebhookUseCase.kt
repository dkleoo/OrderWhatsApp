package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.CartItem
import com.example.bakendorderwhatsapp.domain.model.ChatFlowState
import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.IncomingWhatsAppMessage
import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.repository.CartItemRepository
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.repository.ProductRepository
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import com.example.bakendorderwhatsapp.domain.util.ProductQueryNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class HandleWhatsAppWebhookUseCase(
    private val settingsRepository: WhatsAppSettingsRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val syncEstablishmentProducts: SyncEstablishmentProductsUseCase,
    private val messageSender: WhatsAppMessageSender,
    private val touchChatSession: TouchChatSessionUseCase,
    private val whatsappAccessToken: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val log = LoggerFactory.getLogger(HandleWhatsAppWebhookUseCase::class.java)

    suspend operator fun invoke(messages: List<IncomingWhatsAppMessage>) {
        log.info("Handling {} WhatsApp message(s)", messages.size)
        messages.forEach { message ->
            runCatching { processMessage(message) }
                .onFailure { log.error("Unhandled error from={}", message.from, it) }
        }
    }

    private suspend fun processMessage(message: IncomingWhatsAppMessage) {
        val settings = settingsRepository.getByPhoneId(message.phoneNumberId) ?: run {
            log.warn("No WhatsApp settings for phoneId={}", message.phoneNumberId)
            return
        }
        if (whatsappAccessToken.isBlank()) {
            log.warn("WHATSAPP_ACCESS_TOKEN missing")
            return
        }
        if (settings.token.isBlank()) {
            log.warn("whatsapp_settings.token is blank for phoneId={} (needed as Bearer for establishment products)", message.phoneNumberId)
        }

        val phoneId = settings.phoneId.ifBlank { message.phoneNumberId }
        val receiverPhone = settings.whatsappPhone.ifBlank { message.displayPhoneNumber.orEmpty() }
        val apiToken = settings.token.trim()

        val touch = touchChatSession(
            senderPhone = message.from,
            receiverPhone = receiverPhone,
            phoneId = phoneId,
            whatsappBusinessId = settings.whatsappBusinessId,
            establishmentId = settings.establishmentId
        )
        var session = touch.session
        val graphToken = whatsappAccessToken
        val to = message.from
        val storeName = session.establishmentName.ifBlank { settings.establishmentName }.ifBlank { "la tienda" }

        // Siempre asegurar catálogo: en chat nuevo fuerza sync; si no hay productos, también sync.
        val productCount = productRepository.countByEstablishment(settings.establishmentId)
        if (touch.isNew || productCount == 0) {
            val synced = runCatching {
                syncEstablishmentProducts(
                    accessToken = apiToken,
                    establishmentId = settings.establishmentId,
                    force = touch.isNew || productCount == 0
                )
            }.getOrElse {
                log.error("Failed syncing products for establishmentId={}", settings.establishmentId, it)
                0
            }
            log.info(
                "Product sync result={} isNewSession={} previousCount={} establishmentId={}",
                synced,
                touch.isNew,
                productCount,
                settings.establishmentId
            )
        }

        if (touch.isNew) {
            sendText(
                phoneId, graphToken, to,
                "¡Bienvenido/a a *$storeName*! 🎉\n" +
                    "Estoy aquí para ayudarte con mucho gusto.\n" +
                    "¿En qué te puedo ayudar hoy? Escribe el *nombre* del producto que deseas y te muestro precio y disponibilidad."
            )
            return
        }

        when (session.flowState) {
            ChatFlowState.AWAITING_PRODUCT_NAME -> {
                val name = message.text?.trim().orEmpty()
                if (name.isBlank()) {
                    sendText(phoneId, graphToken, to, "Por favor escribe el *nombre* del producto. No puedo buscar con el nombre vacío.")
                    return
                }
                handleProductSearch(session, phoneId, graphToken, to, name)
            }

            ChatFlowState.AWAITING_PRODUCT_SELECTION -> {
                val productId = message.interactiveReplyId
                if (!productId.isNullOrBlank()) {
                    val products = decodeSearch(session.lastSearchJson)
                    val selected = products.firstOrNull { it.id == productId }
                    if (selected == null) {
                        sendText(phoneId, graphToken, to, "No encontré esa opción. Escribe el nombre del producto para buscar de nuevo.")
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                        return
                    }
                    askAddConfirmation(session, selected, phoneId, graphToken, to)
                    return
                }

                // Si no selecciona de la lista y escribe otro nombre, buscar ese producto.
                val typed = message.text?.trim().orEmpty()
                if (typed.isNotBlank()) {
                    handleProductSearch(session, phoneId, graphToken, to, typed)
                } else {
                    sendText(phoneId, graphToken, to, "Selecciona un producto de la lista, o escribe otro nombre para buscar.")
                }
            }

            ChatFlowState.AWAITING_ADD_CONFIRMATION -> {
                when (message.interactiveReplyId) {
                    "confirm_yes" -> addPendingToCart(session, phoneId, graphToken, to)
                    "confirm_no" -> {
                        session = saveSession(
                            session.copy(
                                flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                                pendingProductId = "",
                                pendingProductName = "",
                                pendingProductPrice = 0.0,
                                pendingProductStock = 0
                            )
                        )
                        sendText(phoneId, graphToken, to, "Ok, no lo agregué. Escribe el nombre de otro producto.")
                    }
                    else -> sendYesNo(
                        phoneId, graphToken, to,
                        "¿Confirmas agregar *${session.pendingProductName}* al carrito?\n" +
                            "Precio: ${money(session.pendingProductPrice)} | Stock: ${session.pendingProductStock}"
                    )
                }
            }

            ChatFlowState.AWAITING_MORE_PRODUCTS -> {
                when (message.interactiveReplyId) {
                    "more_yes" -> {
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
                        sendText(phoneId, graphToken, to, "Perfecto. Escribe el *nombre* del siguiente producto.")
                    }
                    "more_no" -> {
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_ADDRESS))
                        sendText(
                            phoneId, graphToken, to,
                            cartSummary(session) + "\n\nPor último, escribe la *dirección de domicilio* para la entrega."
                        )
                    }
                    else -> sendYesNo(
                        phoneId, graphToken, to,
                        "¿Quieres agregar otro producto?",
                        yesId = "more_yes",
                        noId = "more_no",
                        yesTitle = "Sí, otro",
                        noTitle = "Finalizar"
                    )
                }
            }

            ChatFlowState.AWAITING_ADDRESS -> {
                val address = message.text?.trim().orEmpty()
                if (address.isBlank()) {
                    sendText(phoneId, graphToken, to, "Necesito la *dirección de domicilio* para continuar.")
                    return
                }
                cartItemRepository.updateCheckoutInfo(
                    senderPhone = session.senderPhone,
                    phoneId = session.phoneId,
                    deliveryAddress = address
                )
                session = saveSession(
                    session.copy(
                        deliveryAddress = address,
                        flowState = ChatFlowState.AWAITING_PAYMENT
                    )
                )
                sendYesNo(
                    phoneId, graphToken, to,
                    "Dirección guardada ✅\nAhora elige la *forma de pago*:",
                    yesId = "pay_cash",
                    noId = "pay_transfer",
                    yesTitle = "Efectivo",
                    noTitle = "Transferencia"
                )
            }

            ChatFlowState.AWAITING_PAYMENT -> {
                val payment = when (message.interactiveReplyId) {
                    "pay_cash" -> "Efectivo"
                    "pay_transfer" -> "Transferencia"
                    else -> message.text?.trim().orEmpty()
                }
                if (payment.isBlank()) {
                    sendYesNo(
                        phoneId, graphToken, to,
                        "Elige la forma de pago:",
                        yesId = "pay_cash",
                        noId = "pay_transfer",
                        yesTitle = "Efectivo",
                        noTitle = "Transferencia"
                    )
                    return
                }
                cartItemRepository.updateCheckoutInfo(
                    senderPhone = session.senderPhone,
                    phoneId = session.phoneId,
                    paymentMethod = payment
                )
                session = saveSession(
                    session.copy(
                        paymentMethod = payment,
                        flowState = ChatFlowState.COMPLETED
                    )
                )
                sendText(
                    phoneId, graphToken, to,
                    "✅ Pedido listo\n" +
                        cartSummary(session) +
                        "\nDirección: ${session.deliveryAddress}\n" +
                        "Pago: $payment\n\n" +
                        "Gracias por comprar en $storeName. Si quieres otro pedido, escribe el nombre de un producto."
                )
                saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
            }

            ChatFlowState.COMPLETED -> {
                session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
                val name = message.text?.trim().orEmpty()
                if (name.isBlank()) {
                    sendText(phoneId, graphToken, to, "Escribe el *nombre* del producto que buscas.")
                } else {
                    handleProductSearch(session, phoneId, graphToken, to, name)
                }
            }
        }
    }

    private suspend fun handleProductSearch(
        session: ChatSession,
        phoneId: String,
        graphToken: String,
        to: String,
        rawName: String
    ) {
        val name = ProductQueryNormalizer.normalize(rawName)
        if (name.isBlank()) {
            sendText(
                phoneId, graphToken, to,
                "No entendí el producto. Escribe solo el *nombre*, por ejemplo: *leche*."
            )
            return
        }

        val products = productRepository.searchByName(
            establishmentId = session.establishmentId,
            name = name,
            limit = 10
        )

        when {
            products.isEmpty() -> {
                saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                sendText(
                    phoneId, graphToken, to,
                    "No encontré productos con \"$name\". ¿Me das otro nombre?"
                )
            }
            products.size == 1 -> askAddConfirmation(session, products.first(), phoneId, graphToken, to)
            else -> {
                saveSession(
                    session.copy(
                        flowState = ChatFlowState.AWAITING_PRODUCT_SELECTION,
                        lastSearchJson = encodeSearch(products)
                    )
                )
                messageSender.sendProductList(
                    phoneNumberId = phoneId,
                    accessToken = graphToken,
                    to = to,
                    bodyText = "Encontré ${products.size} opciones para \"$name\". Selecciona una, o escribe otro nombre:",
                    products = products
                )
            }
        }
    }

    private suspend fun askAddConfirmation(
        session: ChatSession,
        product: ProductSummary,
        phoneId: String,
        token: String,
        to: String
    ) {
        saveSession(
            session.copy(
                flowState = ChatFlowState.AWAITING_ADD_CONFIRMATION,
                pendingProductId = product.id,
                pendingProductName = product.name,
                pendingProductPrice = product.price,
                pendingProductStock = product.stock
            )
        )
        sendYesNo(
            phoneId, token, to,
            "Encontré *${product.name}*\n" +
                "Precio: ${money(product.price)}\n" +
                "Stock: ${product.stock}\n\n" +
                "¿Lo agrego al carrito?"
        )
    }

    private suspend fun addPendingToCart(
        session: ChatSession,
        phoneId: String,
        token: String,
        to: String
    ) {
        if (session.pendingProductId.isBlank()) {
            saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
            sendText(phoneId, token, to, "No hay producto pendiente. Escribe un nombre para buscar.")
            return
        }

        cartItemRepository.add(
            CartItem(
                senderPhone = session.senderPhone,
                phoneId = session.phoneId,
                establishmentId = session.establishmentId,
                productId = session.pendingProductId,
                productName = session.pendingProductName,
                quantity = 1,
                price = session.pendingProductPrice,
                deliveryAddress = session.deliveryAddress,
                paymentMethod = session.paymentMethod
            )
        )

        val updated = saveSession(
            session.copy(
                flowState = ChatFlowState.AWAITING_MORE_PRODUCTS,
                pendingProductId = "",
                pendingProductName = "",
                pendingProductPrice = 0.0,
                pendingProductStock = 0,
                lastSearchJson = ""
            )
        )

        sendYesNo(
            phoneId, token, to,
            "✅ Agregado al carrito.\n${cartSummary(updated)}\n\n¿Quieres agregar otro producto?",
            yesId = "more_yes",
            noId = "more_no",
            yesTitle = "Sí, otro",
            noTitle = "Finalizar"
        )
    }

    private suspend fun cartSummary(session: ChatSession): String {
        val items = cartItemRepository.listBySenderAndPhoneId(session.senderPhone, session.phoneId)
        if (items.isEmpty()) return "Carrito vacío."
        val lines = items.mapIndexed { index, item ->
            "${index + 1}. ${item.productName} x${item.quantity} — ${money(item.price)}"
        }
        val total = items.sumOf { it.price * it.quantity }
        return "🛒 Carrito:\n" + lines.joinToString("\n") + "\nTotal: ${money(total)}"
    }

    private suspend fun saveSession(session: ChatSession): ChatSession =
        chatSessionRepository.update(
            session.copy(lastActivityAt = System.currentTimeMillis())
        )

    private suspend fun sendText(phoneId: String, token: String, to: String, body: String) {
        messageSender.sendTextMessage(phoneId, token, to, body)
    }

    private suspend fun sendYesNo(
        phoneId: String,
        token: String,
        to: String,
        body: String,
        yesId: String = "confirm_yes",
        noId: String = "confirm_no",
        yesTitle: String = "Sí",
        noTitle: String = "No"
    ) {
        messageSender.sendYesNoButtons(
            phoneNumberId = phoneId,
            accessToken = token,
            to = to,
            bodyText = body,
            yesId = yesId,
            noId = noId,
            yesTitle = yesTitle,
            noTitle = noTitle
        )
    }

    private fun encodeSearch(products: List<ProductSummary>): String =
        json.encodeToString(products.map {
            SearchHitDto(it.id, it.name, it.price, it.stock)
        })

    private fun decodeSearch(raw: String): List<ProductSummary> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<SearchHitDto>>(raw).map {
                ProductSummary(it.id, it.name, it.price, it.stock)
            }
        }.getOrDefault(emptyList())
    }

    private fun money(value: Double): String =
        if (value % 1.0 == 0.0) "$${value.toInt()}" else "$${"%.2f".format(value)}"

    @Serializable
    private data class SearchHitDto(
        val id: String,
        val name: String,
        val price: Double,
        val stock: Int
    )
}
