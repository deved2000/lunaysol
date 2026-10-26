package io.github.lunasolcal

/** Links und Texte. Passe REPO_URL an dein GitHub-Repository an. */
object AppInfo {
    const val REPO_URL = "https://github.com/deved2000/lunaysol"
    const val GLOSSARY_URL = "$REPO_URL/blob/main/docs/GLOSSAR.md"
    const val FAQ_URL = "$REPO_URL/blob/main/docs/FAQ.md"

    val PRIVACY_TEXT = """
        Luna y Sol sammelt keine persönlichen Daten und enthält weder Werbung noch Tracking.

        Alle astronomischen Berechnungen laufen vollständig offline auf deinem Gerät.

        Gespeichert werden nur deine Orte und Einstellungen – lokal auf dem Gerät.

        Nur wenn du es auslöst, werden Daten an Open-Meteo (open-meteo.com) gesendet:
        • bei der Ortssuche der eingegebene Suchbegriff,
        • beim Hinzufügen eines Ortes bzw. bei GPS die Koordinaten, um die Zeitzone zu ermitteln.
        Diese Online-Dienste kannst du in den Einstellungen abschalten.

        Die GPS-Position wird nur nach deiner Freigabe und nur bei „Position aktual. (GPS)“ abgefragt.
    """.trimIndent()

    fun aboutText() = """
        Luna y Sol ${BuildConfig.VERSION_NAME}

        Sonnen- und Mondkalender für jeden Ort der Welt: Auf- und Untergänge, Dämmerung, Mondphasen, Perigäum/Apogäum, Jahreszeiten sowie Sonnen- und Mondfinsternisse.

        Berechnungsgrundlagen:
        • Sonne: gekürzte VSOP87-Theorie (J. Meeus, Astronomical Algorithms)
        • Mond: gekürzte ELP-2000/82-Theorie (Meeus, Kapitel 47)
        • Ortssuche & Zeitzonen: Open-Meteo Geocoding API

        Quellcode (Open Source, MIT-Lizenz):
        $REPO_URL
    """.trimIndent()
}
