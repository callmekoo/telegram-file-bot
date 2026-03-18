package tgFileBot

import com.github.kotlintelegrambot.entities.Chat

class Text {
    companion object {
        const val GIVE_COMMAND = "дай"
        const val HELP_COMMAND = "хелб"

        fun getGreetingMessage(fileLimit: Int): String = """
                👋 привет! я бот для отправки случайных файлов.
            """.trimIndent() + "\n\n" + getAvailableCommands(fileLimit)

        fun getAvailableCommands(fileLimit: Int): String = """
            📌 доступные команды:
            дай — получить случайный файл (максимум $fileLimit раз)
            хелб — показать список команд
        """.trimIndent()

        const val LIMIT_EXCEEDED_MESSAGE = "Лимит в 5 файлов исчерпан!"
        const val OUT_OF_FILES_MESSAGE = "Файлы кончились! Напиши тому, кто дал тебе адрес этого бота."
        const val OUT_OF_FILES_ADMIN_MESSAGE = "Файлы кончились! Надо добавить еще."

        fun Chat.getUserWithId(): String {
            return "${this.username} (id ${this.id})"
        }
    }
}