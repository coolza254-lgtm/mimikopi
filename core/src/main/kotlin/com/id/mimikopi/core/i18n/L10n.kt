package com.id.mimikopi.core.i18n

/** The languages the app is written in (the user asked for English and Japanese besides Thai, 2026-10-23). */
enum class Lang(val code: String, val label: String) {
    TH("th", "ไทย"),
    EN("en", "English"),
    JA("ja", "日本語");

    companion object {
        fun parse(code: String?): Lang = entries.firstOrNull { it.code == code } ?: TH
    }
}

/**
 * The language in use, for both apps. It is set once when an app starts (from its saved choice) and again when the
 * user picks another one; the apps then rebuild their screens, so text is read with [tr] at the moment it is shown.
 */
object L10n {
    /** The setting key, the same on the phone and the PC (the choice itself is not synced: each device keeps its own). */
    const val KEY = "ui.lang"

    @Volatile
    var lang: Lang = Lang.TH
}

/**
 * A text in the three languages, written side by side where it is used so a translation never drifts from its Thai.
 * Thai is the original; English and Japanese say the same thing in the words a piano learner would expect.
 */
fun tr(th: String, en: String, ja: String): String = when (L10n.lang) {
    Lang.TH -> th
    Lang.EN -> en
    Lang.JA -> ja
}
