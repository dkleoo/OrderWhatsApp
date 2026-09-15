package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.CartItem
import com.example.bakendorderwhatsapp.domain.model.ChatFlowState
import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.IncomingWhatsAppMessage
import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.repository.CartItemRepository
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.service.ProductCatalog
import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class HandleWhatsAppWebhookUseCase(
    private val settingsRepository: WhatsAppSettingsRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val cartItemRepository: CartItemRepository,
    private val productCatalog: ProductCatalog,
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

        val phoneId = settings.phoneId.ifBlank { message.phoneNumberId }
        val receiverPhone = settings.whatsappPhone.ifBlank { message.displayPhoneNumber.orEmpty() }

        val touch = touchChatSession(
            senderPhone = message.from,
            receiverPhone = receiverPhone,
            phoneId = phoneId,
            whatsappBusinessId = settings.whatsappBusinessId,
            establishmentId = settings.establishmentId
        )
        var session = touch.session
        val token = whatsappAccessToken
        val to = message.from
        val storeName = session.establishmentName.ifBlank { settings.establishmentName }.ifBlank { "la tienda" }

        if (touch.isNew) {
            sendText(
                phoneId, token, to,
                "Bienvenido/a a $storeName 👋\nPara agregar productos, escribe el *nombre* del producto que buscas."
            )
            return
        }

        when (session.flowState) {
            ChatFlowState.AWAITING_PRODUCT_NAME -> {
                val name = message.text?.trim().orEmpty()
                if (name.isBlank()) {
                    sendText(phoneId, token, to, "Por favor escribe el *nombre* del producto. No puedo buscar con el nombre vacío.")
                    return
                }
                handleProductSearch(session, phoneId, token, to, name)
            }

            ChatFlowState.AWAITING_PRODUCT_SELECTION -> {
                val productId = message.interactiveReplyId
                if (productId.isNullOrBlank()) {
                    sendText(phoneId, token, to, "Selecciona un producto de la lista, o escribe un nuevo nombre para buscar.")
                    if (!message.text.isNullOrBlank()) {
                        handleProductSearch(session, phoneId, token, to, message.text.trim())
                    }
                    return
                }
                val products = decodeSearch(session.lastSearchJson)
                val selected = products.firstOrNull { it.id == productId }
                if (selected == null) {
                    sendText(phoneId, token, to, "No encontré esa opción. Escribe el nombre del producto para buscar de nuevo.")
                    session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                    return
                }
                askAddConfirmation(session, selected, phoneId, token, to)
            }

            ChatFlowState.AWAITING_ADD_CONFIRMATION -> {
                when (message.interactiveReplyId) {
                    "confirm_yes" -> addPendingToCart(session, phoneId, token, to)
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
                        sendText(phoneId, token, to, "Ok, no lo agregué. Escribe el nombre de otro producto.")
                    }
                    else -> sendYesNo(
                        phoneId, token, to,
                        "¿Confirmas agregar *${session.pendingProductName}* al carrito?\n" +
                            "Precio: ${money(session.pendingProductPrice)} | Stock: ${session.pendingProductStock}"
                    )
                }
            }

            ChatFlowState.AWAITING_MORE_PRODUCTS -> {
                when (message.interactiveReplyId) {
                    "more_yes" -> {
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
                        sendText(phoneId, token, to, "Perfecto. Escribe el *nombre* del siguiente producto.")
                    }
                    "more_no" -> {
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_ADDRESS))
                        sendText(
                            phoneId, token, to,
                            cartSummary(session) + "\n\nPor último, escribe la *dirección de domicilio* para la entrega."
                        )
                    }
                    else -> sendYesNo(
                        phoneId, token, to,
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
                    sendText(phoneId, token, to, "Necesito la *dirección de domicilio* para continuar.")
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
                    phoneId, token, to,
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
                        phoneId, token, to,
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
                    phoneId, token, to,
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
                    sendText(phoneId, token, to, "Escribe el *nombre* del producto que buscas.")
                } else {
                    handleProductSearch(session, phoneId, token, to, name)
                }
            }
        }
    }

    private suspend fun handleProductSearch(
        session: ChatSession,
        phoneId: String,
        token: String,
        to: String,
        name: String
    ) {
        if (name.isBlank()) {
            sendText(phoneId, token, to, "El nombre no puede ir vacío. Escribe el producto que buscas.")
            return
        }

        val products = productCatalog.searchByName(
            establishmentId = session.establishmentId,
            name = name,
            pageNumber = 1,
            pageSize = 10
        )

        when {
            products.isEmpty() -> {
                saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                sendText(
                    phoneId, token, to,
                    "No encontré productos con \"$name\". Intenta con otro nombre."
                )
            }
            products.size == 1 -> askAddConfirmation(session, products.first(), phoneId, token, to)
            else -> {
                saveSession(
                    session.copy(
                        flowState = ChatFlowState.AWAITING_PRODUCT_SELECTION,
                        lastSearchJson = encodeSearch(products)
                    )
                )
                messageSender.sendProductList(
                    phoneNumberId = phoneId,
                    accessToken = token,
                    to = to,
                    bodyText = "Encontré ${products.size} productos. Selecciona uno:",
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
            StoredProduct(it.id, it.name, it.price, it.stock)
        })

    private fun decodeSearch(raw: String): List<ProductSummary> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredProduct>>(raw).map {
                ProductSummary(it.id, it.name, it.price, it.stock)
            }
        }.getOrDefault(emptyList())
    }

    private fun money(value: Double): String =
        if (value % 1.0 == 0.0) "$${value.toInt()}" else "$${"%.2f".format(value)}"

    @Serializable
    private data class StoredProduct(
        val id: String,
        val name: String,
        val price: Double,
        val stock: Int
    )
}
