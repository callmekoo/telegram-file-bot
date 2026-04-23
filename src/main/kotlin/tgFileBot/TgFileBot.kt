package tgFileBot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.*
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import tgFileBot.Text.Companion.GIVE_COMMAND
import tgFileBot.Text.Companion.HELP_COMMAND
import tgFileBot.Text.Companion.LIMIT_EXCEEDED_MESSAGE
import tgFileBot.Text.Companion.OUT_OF_FILES_ADMIN_MESSAGE
import tgFileBot.Text.Companion.OUT_OF_FILES_MESSAGE
import tgFileBot.Text.Companion.getUserWithId
import java.io.File
import java.io.InputStream
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class TgFileBot {
    private val log = KotlinLogging.logger { }

    private val config = loadProperties()

    private lateinit var telegramToken: String
    private lateinit var adminUserId: String

    private val filesDirectory = config.getProperty("filesDirectory")
    private val fileLimitsFile = config.getProperty("fileLimitsFile")
    private val fileLimit = config.getProperty("fileLimit").toInt()

    private val fileProvider = FileProvider(
        filesDirectory = filesDirectory,
        log = log,
    )
    private val fileLimitProvider = FileLimitProvider(
        fileLimitsFile = fileLimitsFile,
        fileLimit = fileLimit,
        log = log,
    )

    fun run() {
        fileLimitProvider.init()

        val dotenv = dotenv()
        telegramToken = dotenv["TOKEN"]
        adminUserId = dotenv["ADMIN_USER_ID"]

        if (telegramToken.isEmpty()) {
            log.error { "Token not set!" }
            return
        }
        if (adminUserId.isEmpty()) {
            log.error { "Admin user id not set!" }
            return
        }

        log.info { "Bot started" }

        val bot = bot {
            token = telegramToken
            logLevel = LogLevel.Error

            dispatch {
                command("start") {
                    log.info { "User ${message.chat.getUserWithId()} started the bot" }

                    val result = bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = Text.getGreetingMessage(fileLimit),
                        replyMarkup = KeyboardReplyMarkup(keyboard = generateButtons(), resizeKeyboard = true),
                    )

                    result.fold(
                        ifSuccess = {
                            log.info { "Sent hello message to ${message.chat.getUserWithId()}" }
                        },
                        ifError = {
                            log.info { "Failed to send hello message to ${message.chat.getUserWithId()}" }
                        }
                    )
                }

                command("all") {
                    if (message.chat.id == adminUserId.toLong()) {
                        val allMessage = args.joinToString()
                        if (allMessage.isEmpty()) {
                            bot.sendMessage(
                                userId = message.chat.id,
                                text = "There is no text apart from command!",
                                description = "empty ALL message error",
                            )
                            return@command
                        }
                        fileLimitProvider.getUserIds().forEach { userId ->
                            bot.sendMessage(
                                text = allMessage,
                                userId = userId.toLong(),
                                description = "ALL message",
                            )
                            delay(5000.milliseconds)
                        }

                    } else {
                        log.info { "User ${message.chat.getUserWithId()} tried to run ALL command" }
                    }
                }

                command("stats") {
                    if (message.chat.id == adminUserId.toLong()) {
                        var text = "```\n"
                        fileLimitProvider.getUserIdFileCounts().forEach { entry ->
                            text += "${entry.key},${entry.value}\n"
                        }
                        text += "```"

                        bot.sendMessage(
                            userId = adminUserId.toLong(),
                            text = text,
                            parseMode = ParseMode.MARKDOWN,
                            description = "stats message",
                        )
                    } else {
                        log.info { "User ${message.chat.getUserWithId()} tried to run stats command" }
                    }
                }

                text(GIVE_COMMAND) {
                    log.info { "User ${message.chat.getUserWithId()} requested file" }

                    if (fileLimitProvider.isLimitExceeded(message.chat.id)) {
                        bot.sendMessage(
                            userId = message.chat.id,
                            text = LIMIT_EXCEEDED_MESSAGE,
                            description = "limits exceeded message",
                        )
                        return@text
                    }

                    val file = fileProvider.tryGetFile()
                    if (file == null) {
                        bot.sendMessage(
                            userId = message.chat.id,
                            text = OUT_OF_FILES_MESSAGE,
                            description = "out of files message",
                        )
                        bot.sendMessage(
                            userId = adminUserId.toLong(),
                            text = OUT_OF_FILES_ADMIN_MESSAGE,
                            description = "out of files message",
                        )
                        return@text
                    }

                    bot.sendFile(message = message, file = file)

                    fileProvider.deleteFile(file)
                }

                text(HELP_COMMAND) {
                    log.info {
                        "User ${message.chat.getUserWithId()} requested help"
                    }

                    bot.sendMessage(
                        text = Text.getAvailableCommands(fileLimit),
                        userId = message.chat.id,
                        description = "help message",
                    )
                }
            }
        }

        bot.startPolling()
    }

    private fun Bot.sendMessage(text: String, userId: Long, parseMode: ParseMode? = null, description: String) {
        val result = this.sendMessage(
            chatId = ChatId.fromId(userId),
            text = text,
            parseMode = parseMode,
        )
        result.fold(
            ifSuccess = { log.info { "Sent $description to $userId" } },
            ifError = { log.info { "Failed to send $description to $userId" } },
        )
    }

    private fun Bot.sendFile(message: Message, file: File) {
        this.sendDocument(
            chatId = ChatId.fromId(message.chat.id),
            document = TelegramFile.ByFile(file),
        ).also {
            if (it.first?.isSuccessful ?: false) {
                fileLimitProvider.logLimit(userId = message.chat.id, filename = file.name)

                log.info {
                    "File ${file.name} sent successfully to ${message.chat.getUserWithId()}"
                }
            } else {
                log.error {
                    "Failed to send file ${file.name} to ${message.chat.getUserWithId()} with exception ${it.second.toString()}"
                }
            }
        }
    }

    private fun generateButtons(): List<List<KeyboardButton>> {
        return listOf(
            listOf(
                KeyboardButton(text = GIVE_COMMAND),
                KeyboardButton(text = HELP_COMMAND),
            ),
        )
    }

    private fun loadProperties(fileName: String = "application.properties"): Properties {
        val properties = Properties()
        val inputStream: InputStream = TgFileBot::class.java.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalStateException("Properties file not found: $fileName")
        properties.load(inputStream)
        return properties
    }
}