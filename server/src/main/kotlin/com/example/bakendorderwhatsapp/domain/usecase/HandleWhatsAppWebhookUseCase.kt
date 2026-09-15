package com.example.bakendorderwhatsapp.domain.usecase

import com.example.bakendorderwhatsapp.domain.model.ChatFlowState
import com.example.bakendorderwhatsapp.domain.model.ChatSession
import com.example.bakendorderwhatsapp.domain.model.IncomingWhatsAppMessage
import com.example.bakendorderwhatsapp.domain.model.OrderHeader
import com.example.bakendorderwhatsapp.domain.model.ProductSummary
import com.example.bakendorderwhatsapp.domain.repository.ChatSessionRepository
import com.example.bakendorderwhatsapp.domain.repository.OrderRepository
import com.example.bakendorderwhatsapp.domain.repository.ProductRepository
import com.example.bakendorderwhatsapp.domain.repository.WhatsAppSettingsRepository
import com.example.bakendorderwhatsapp.domain.service.WhatsAppMessageSender
import com.example.bakendorderwhatsapp.domain.util.ChatMessageIntent
import com.example.bakendorderwhatsapp.domain.util.ProductQueryNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class HandleWhatsAppWebhookUseCase(
    private val settingsRepository: WhatsAppSettingsRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val orderRepository: OrderRepository,
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
            val firstRequest = message.text?.trim().orEmpty()
            val productQuery = ChatMessageIntent.extractProductQuery(firstRequest)
            if (productQuery != null) {
                handleProductSearch(session, phoneId, graphToken, to, productQuery)
            }
            return
        }

        when (session.flowState) {
            ChatFlowState.AWAITING_PRODUCT_NAME -> {
                val name = message.text?.trim().orEmpty()
                if (name.isBlank()) {
                    sendText(
                        phoneId, graphToken, to,
                        "¡Claro! Dime el *nombre* del producto que quieres y te ayudo a armar tu pedido."
                    )
                    return
                }
                val productQuery = ChatMessageIntent.extractProductQuery(name)
                if (productQuery == null) {
                    if (ChatMessageIntent.isGreetingOnly(name)) {
                        sendText(
                            phoneId, graphToken, to,
                            "¡Hola! 😊 ¿Qué producto te gustaría ordenar hoy? Escríbeme el nombre."
                        )
                    } else {
                        sendText(
                            phoneId, graphToken, to,
                            "Te leí. Para armar tu pedido necesito el *nombre* del producto, por ejemplo: *leche*."
                        )
                    }
                    return
                }
                handleProductSearch(session, phoneId, graphToken, to, productQuery)
            }

            ChatFlowState.AWAITING_PRODUCT_SELECTION -> {
                val productId = message.interactiveReplyId
                if (!productId.isNullOrBlank()) {
                    val products = decodeSearch(session.lastSearchJson)
                    val selected = products.firstOrNull { it.id == productId }
                    if (selected == null) {
                        sendText(
                            phoneId, graphToken, to,
                            "No encontré esa opción. Escribe el nombre de otro producto y con gusto te ayudo a pedirlo."
                        )
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                        return
                    }
                    offerProductOrExplainNoStock(session, selected, phoneId, graphToken, to)
                    return
                }

                // Si no selecciona de la lista y escribe otro nombre, buscar ese producto.
                val typed = message.text?.trim().orEmpty()
                if (typed.isNotBlank()) {
                    val productQuery = ChatMessageIntent.extractProductQuery(typed)
                    if (productQuery == null) {
                        if (ChatMessageIntent.isGreetingOnly(typed)) {
                            sendText(
                                phoneId, graphToken, to,
                                "¡Hola! 😊 Selecciona un producto de la lista, o escribe el nombre de lo que quieres pedir."
                            )
                        } else {
                            sendText(
                                phoneId, graphToken, to,
                                "Selecciona un producto de la lista, o escribe el *nombre* de otro producto."
                            )
                        }
                    } else {
                        handleProductSearch(session, phoneId, graphToken, to, productQuery)
                    }
                } else {
                    sendText(
                        phoneId, graphToken, to,
                        "Selecciona un producto de la lista, o escribe otro nombre para buscar. ¡Estoy listo para tu pedido!"
                    )
                }
            }

            ChatFlowState.AWAITING_ADD_CONFIRMATION -> {
                when (message.interactiveReplyId) {
                    "confirm_yes" -> {
                        if (session.pendingProductStock <= 0) {
                            val unavailable = session.pendingProductName.ifBlank { "ese producto" }
                            session = saveSession(
                                session.copy(
                                    flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                                    pendingProductId = "",
                                    pendingProductName = "",
                                    pendingProductPrice = 0.0,
                                    pendingProductStock = 0
                                )
                            )
                            sendText(
                                phoneId, graphToken, to,
                                "Lamentablemente *$unavailable* se quedó sin stock y no puedo venderlo ahora.\n" +
                                    "¿Qué otro producto te gustaría pedir? Escríbeme el nombre 🛒"
                            )
                            return
                        }
                        session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_QUANTITY))
                        sendText(
                            phoneId, graphToken, to,
                            "¿Qué *cantidad* deseas de *${session.pendingProductName}*?\n" +
                                "Stock disponible: ${session.pendingProductStock}\n" +
                                "Escribe un número (ejemplo: 2)."
                        )
                    }
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

            ChatFlowState.AWAITING_QUANTITY -> {
                if (session.pendingProductStock <= 0 || session.pendingProductId.isBlank()) {
                    val unavailable = session.pendingProductName.ifBlank { "ese producto" }
                    session = saveSession(
                        session.copy(
                            flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                            pendingProductId = "",
                            pendingProductName = "",
                            pendingProductPrice = 0.0,
                            pendingProductStock = 0
                        )
                    )
                    sendText(
                        phoneId, graphToken, to,
                        "Ya no hay stock de *$unavailable*, así que no puedo venderlo.\n" +
                            "Dime otro producto y armamos tu pedido 🛒"
                    )
                    return
                }
                val qty = message.text?.trim()?.toIntOrNull()
                when {
                    qty == null || qty <= 0 -> {
                        sendText(
                            phoneId, graphToken, to,
                            "Ingresa una cantidad válida (número entero mayor a 0).\n" +
                                "Stock disponible: ${session.pendingProductStock}"
                        )
                    }
                    qty > session.pendingProductStock -> {
                        sendText(
                            phoneId, graphToken, to,
                            "Solo hay *${session.pendingProductStock}* en stock. Escribe una cantidad menor o igual."
                        )
                    }
                    else -> addPendingToCart(session, phoneId, graphToken, to, quantity = qty)
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
                            orderSummary(session) + "\n\nEscribe la *dirección de domicilio* para la entrega."
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
                orderRepository.updateDraftCheckout(
                    senderPhone = session.senderPhone,
                    phoneId = session.phoneId,
                    deliveryAddress = address
                )
                session = saveSession(
                    session.copy(
                        deliveryAddress = address,
                        flowState = ChatFlowState.AWAITING_CUSTOMER_NAME
                    )
                )
                sendText(
                    phoneId, graphToken, to,
                    "Dirección guardada ✅\nAhora escribe el *nombre de la persona* que recibe el pedido."
                )
            }

            ChatFlowState.AWAITING_CUSTOMER_NAME -> {
                val customerName = message.text?.trim().orEmpty()
                if (customerName.isBlank() || ChatMessageIntent.isGreetingOnly(customerName)) {
                    sendText(
                        phoneId, graphToken, to,
                        "Necesito el *nombre de la persona* para el pedido (ejemplo: *María Pérez*)."
                    )
                    return
                }
                orderRepository.updateDraftCheckout(
                    senderPhone = session.senderPhone,
                    phoneId = session.phoneId,
                    customerName = customerName
                )
                session = saveSession(
                    session.copy(
                        customerName = customerName,
                        flowState = ChatFlowState.AWAITING_PAYMENT
                    )
                )
                sendYesNo(
                    phoneId, graphToken, to,
                    "Nombre guardado ✅ (*$customerName*)\nAhora elige la *forma de pago*:",
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
                val completed = orderRepository.completeDraft(
                    senderPhone = session.senderPhone,
                    phoneId = session.phoneId,
                    customerName = session.customerName,
                    deliveryAddress = session.deliveryAddress,
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
                    "✅ Pedido finalizado\n" +
                        orderSummary(completed) +
                        "\n\nGracias por comprar en *$storeName*. Si quieres otro pedido, escribe el nombre de un producto."
                )
                saveSession(
                    session.copy(
                        flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                        customerName = "",
                        deliveryAddress = "",
                        paymentMethod = ""
                    )
                )
            }

            ChatFlowState.COMPLETED -> {
                session = saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
                val name = message.text?.trim().orEmpty()
                val productQuery = ChatMessageIntent.extractProductQuery(name)
                when {
                    name.isBlank() -> sendText(
                        phoneId, graphToken, to,
                        "Escribe el *nombre* del producto que buscas."
                    )
                    productQuery == null && ChatMessageIntent.isGreetingOnly(name) -> sendText(
                        phoneId, graphToken, to,
                        "¡Hola de nuevo! 😊 ¿Qué producto quieres pedir ahora?"
                    )
                    productQuery == null -> sendText(
                        phoneId, graphToken, to,
                        "Dime el *nombre* del producto y te ayudo con el pedido."
                    )
                    else -> handleProductSearch(session, phoneId, graphToken, to, productQuery)
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
                "Te leí, pero no capté un producto claro. Escribe el *nombre*, por ejemplo: *leche*, y te ayudo a pedirlo."
            )
            return
        }

        val matches = productRepository.searchByName(
            establishmentId = session.establishmentId,
            name = name,
            limit = 10
        )
        val available = matches.filter { it.stock > 0 }
        val outOfStock = matches.filter { it.stock <= 0 }

        when {
            available.isEmpty() && outOfStock.isNotEmpty() -> {
                saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                val mentioned = outOfStock.take(3).joinToString(", ") { "*${it.name}*" }
                sendText(
                    phoneId, graphToken, to,
                    "Entiendo que buscas $mentioned; por ahora *no hay stock* y no puedo venderlo 😕\n" +
                        "¿Qué otro producto te gustaría ordenar? Escríbeme el nombre y lo agregamos a tu pedido."
                )
            }
            available.isEmpty() -> {
                saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME, lastSearchJson = ""))
                sendText(
                    phoneId, graphToken, to,
                    "Escuché que buscas *\"$name\"*, pero no lo tengo en el catálogo ahora.\n" +
                        "¿Probamos con otro nombre? Dime qué quieres pedir y te ayudo 🛒"
                )
            }
            available.size == 1 -> offerProductOrExplainNoStock(session, available.first(), phoneId, graphToken, to)
            else -> {
                saveSession(
                    session.copy(
                        flowState = ChatFlowState.AWAITING_PRODUCT_SELECTION,
                        lastSearchJson = encodeSearch(available)
                    )
                )
                messageSender.sendProductList(
                    phoneNumberId = phoneId,
                    accessToken = graphToken,
                    to = to,
                    bodyText = "Encontré ${available.size} opciones con stock para \"$name\". Selecciona una, o escribe otro nombre:",
                    products = available
                )
            }
        }
    }

    private suspend fun offerProductOrExplainNoStock(
        session: ChatSession,
        product: ProductSummary,
        phoneId: String,
        token: String,
        to: String
    ) {
        if (product.stock <= 0) {
            saveSession(
                session.copy(
                    flowState = ChatFlowState.AWAITING_PRODUCT_NAME,
                    pendingProductId = "",
                    pendingProductName = "",
                    pendingProductPrice = 0.0,
                    pendingProductStock = 0,
                    lastSearchJson = ""
                )
            )
            sendText(
                phoneId, token, to,
                "Vi que te interesa *${product.name}*, pero *no hay stock* y no puedo venderlo en este momento.\n" +
                    "¿Qué otro producto quieres ordenar? Escríbeme el nombre 🛒"
            )
            return
        }
        askAddConfirmation(session, product, phoneId, token, to)
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
                "¿Deseas este producto?"
        )
    }

    private suspend fun addPendingToCart(
        session: ChatSession,
        phoneId: String,
        token: String,
        to: String,
        quantity: Int
    ) {
        if (session.pendingProductId.isBlank()) {
            saveSession(session.copy(flowState = ChatFlowState.AWAITING_PRODUCT_NAME))
            sendText(phoneId, token, to, "No hay producto pendiente. Escribe un nombre para buscar.")
            return
        }

        orderRepository.addOrIncrementDetail(
            senderPhone = session.senderPhone,
            phoneId = session.phoneId,
            establishmentId = session.establishmentId,
            productId = session.pendingProductId,
            productName = session.pendingProductName,
            quantity = quantity,
            unitPrice = session.pendingProductPrice
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
            "✅ Agregado: *${session.pendingProductName}* x$quantity\n${orderSummary(updated)}\n\n¿Quieres agregar otro producto?",
            yesId = "more_yes",
            noId = "more_no",
            yesTitle = "Sí, otro",
            noTitle = "Finalizar"
        )
    }

    private suspend fun orderSummary(session: ChatSession): String {
        val order = orderRepository.findDraft(session.senderPhone, session.phoneId)
        return orderSummary(order)
    }

    private fun orderSummary(order: OrderHeader?): String {
        if (order == null || order.details.isEmpty()) return "Carrito vacío."

        val headerLines = buildList {
            add("🧾 *Pedido*")
            if (order.customerName.isNotBlank()) add("Cliente: ${order.customerName}")
            if (order.deliveryAddress.isNotBlank()) add("Dirección: ${order.deliveryAddress}")
            if (order.paymentMethod.isNotBlank()) add("Pago: ${order.paymentMethod}")
            add("Total: ${money(order.total)}")
        }

        val detailLines = order.details.mapIndexed { index, item ->
            "${index + 1}. ${item.productName} x${item.quantity} — ${money(item.lineTotal)}"
        }

        return headerLines.joinToString("\n") +
            "\n\n*Detalle:*\n" +
            detailLines.joinToString("\n")
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
