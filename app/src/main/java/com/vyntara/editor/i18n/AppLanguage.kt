package com.vyntara.editor.i18n

enum class AppLanguage(val code: String, val displayName: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: SPANISH
    }
}
