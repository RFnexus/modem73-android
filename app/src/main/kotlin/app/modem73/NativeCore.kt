package app.modem73

object NativeCore {
    init {
        System.loadLibrary("modem73")
    }

    @Volatile
    var pttHandler: ((Boolean) -> Boolean)? = null

    @JvmStatic
    fun onExternalPtt(on: Boolean): Boolean {
        return pttHandler?.invoke(on) ?: false
    }

    external fun version(): String
    external fun hamlibRigList(): String
    external fun ptyCreate(): String
    external fun ptyPush(data: ByteArray)
    external fun ptyClose()

    @Volatile
    var rigWriteHandler: ((ByteArray) -> Unit)? = null

    @JvmStatic
    fun onRigData(data: ByteArray) {
        rigWriteHandler?.invoke(data)
    }
    external fun buildInfo(): String
    external fun loopbackSelfTest(): String
    external fun engineStart(configJson: String, homeDir: String): Boolean
    external fun engineStop()
    external fun engineRunning(): Boolean
    external fun lastError(): String
    external fun statusJson(): String
    external fun snapshotJson(): String
    external fun configJson(): String
    external fun setConfigJson(json: String): String
    external fun queueData(data: ByteArray, operMode: Int)
    external fun sendChat(text: String)
    external fun rigctlCommand(cmd: String): String
    external fun setRigPoll(on: Boolean)
    external fun rigRefresh()
    external fun alcTune(): Float
    external fun takeWaterfall(): FloatArray
    external fun waterfallBins(): Int
    external fun resetStats()
    external fun audioDevicesJson(): String
    external fun debugRecord(seconds: Int): String
}
