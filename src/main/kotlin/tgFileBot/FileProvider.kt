package tgFileBot

import io.github.oshai.kotlinlogging.KLogger
import java.io.File
import java.util.*
import kotlin.random.Random

class FileProvider(
    val filesDirectory: String,
    val log: KLogger,
) {
    fun tryGetFile(): File? {
        val directory = File(filesDirectory)
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (!files.isNullOrEmpty()) {
                return files[random.nextInt(until = files.count())]
            }
        }
        return null
    }

    fun deleteFile(file: File) {
        if (file.exists() && file.isFile) {
            file.delete()
            log.info { "Deleted file: ${file.name}" }
        } else {
            log.error { "Cannot delete file: ${file.name}" }
        }
    }

    private val random = Random(Date().time)
}