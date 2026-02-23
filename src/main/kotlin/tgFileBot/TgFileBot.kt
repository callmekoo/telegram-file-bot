package tgFileBot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import tgFileBot.Text.Companion.GIVE_COMMAND
import tgFileBot.Text.Companion.HELP_COMMAND
import tgFileBot.Text.Companion.LIMIT_EXCEEDED_MESSAGE
import tgFileBot.Text.Companion.OUT_OF_FILES_ADMIN_MESSAGE
import tgFileBot.Text.Companion.OUT_OF_FILES_MESSAGE
import tgFileBot.Text.Companion.getUserWithId
import java.io.File
import java.io.InputStream
import java.util.*

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
                        text = Text.greetingMessage,
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

                command("ask") {
                    if (message.chat.id == adminUserId.toLong()) {
                        fileLimitProvider.getUserIds().forEach { userId ->
                            bot.sendMessage(
                                chatId = ChatId.fromId(userId.toLong()),
                                text = "Пожалуйста дайте денежков будьте так добры",
                            )
                            log.info { "Sent donate message to ${message.chat.getUserWithId()}" }
                        }
                    } else {
                        log.info { "User ${message.chat.getUserWithId()} tried to run ask commands" }
                    }
                }

                text(GIVE_COMMAND) {
                    log.info { "User ${message.chat.getUserWithId()} requested file" }

                    if (fileLimitProvider.isLimitExceeded(message.chat.id)) {
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = LIMIT_EXCEEDED_MESSAGE,
                        )
                        return@text
                    }

                    val file = fileProvider.tryGetFile()
                    if (file == null) {
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = OUT_OF_FILES_MESSAGE,
                        )
                        bot.sendMessage(
                            chatId = ChatId.fromId(adminUserId.toLong()),
                            text = OUT_OF_FILES_ADMIN_MESSAGE,
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
                        chatId = ChatId.fromId(message.chat.id),
                        text = Text.availableCommands,
                    )
                }
            }
        }

        bot.startPolling()
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
            listOf(KeyboardButton(text = GIVE_COMMAND)),
            listOf(KeyboardButton(text = HELP_COMMAND)),
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