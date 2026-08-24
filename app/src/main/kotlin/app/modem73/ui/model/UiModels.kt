package app.modem73.ui.model

enum class Page(val label: String) { STATUS("STATUS"), CONFIG("CONFIG"), UTILS("UTILS"), RIG("RIG") }

enum class ChatDirection { SENT, RECEIVED, SYSTEM }

data class ChatLine(
    val direction: ChatDirection,
    val callsign: String,
    val text: String,
    val time: String,
    val snrDb: Float? = null
)

data class UtilsUiState(
    val chatLines: List<ChatLine>,
    val draft: String,
    val randomSizes: List<Int>,
    val selectedRandomSize: Int,
    val maxPayloadBytes: Int,
    val logLines: List<String>
)
