package tgFileBot

import io.github.oshai.kotlinlogging.KLogger
import java.io.File

class FileLimitProvider(
    val fileLimitsFile: String,
    val fileLimit: Int,
    val log: KLogger,
) {
    private var dbFile = File(fileLimitsFile)
    private var userIdFileCounts = mutableMapOf<String, Int>()

    private val separator = ","
    private val idIndex = 0

    fun init() {
        if (!dbFile.exists()) {
            dbFile.createNewFile()
            log.info { "Limits file created" }
        } else {
            userIdFileCounts = dbFile
                .readLines()
                .map { it.split(separator)[idIndex] }
                .groupingBy { it }
                .eachCount()
                .toMutableMap()
            log.info { "Limits loaded" }
        }
    }

    fun logLimit(userId: Long, filename: String) {
        userIdFileCounts[userId.toString()] = userIdFileCounts.getOrDefault(key = userId.toString(), defaultValue = 0) + 1
        dbFile.appendText(listOf(userId, filename).joinToString(separator))
        log.info { "Log file $filename for $userId" }
    }

    fun isLimitExceeded(userId: Long): Boolean {
        return userIdFileCounts.getOrDefault(userId.toString(), 0) >= fileLimit
    }

    fun getUserIds(): Set<String> {
        return userIdFileCounts.keys
    }

    fun getUserIdFileCounts(): MutableMap<String, Int> {
        return userIdFileCounts
    }
}