# LunaSolCal 🌞🌙

Sonnen- und Mondkalender für Android – für **jeden Ort der Welt** und **jedes beliebige Datum und jede Uhrzeit**.
Alle Berechnungen laufen offline direkt auf dem Gerät.

## Funktionen

**Überblick**
- Sonnenauf-/-untergang und Tageslänge
- Mondauf-/-untergang, Alter, beleuchteter Anteil, Phase (inkl. exakter Zeit von Neumond, Vollmond, Vierteln)
- Ort mit Koordinaten und Zeitzone (inkl. Sommer-/Winterzeit), GPS-Schaltfläche
- Zweite Seite: Himmelskarte mit aktueller Position von Sonne und Mond

**Sonne** (4 Seiten, wischen)
1. Details: Aufgang/Untergang/Mittag mit Azimut und Höhe, Tageslänge (Vergleich zu gestern und den Sonnenwenden), bürgerliche/nautische/astronomische Dämmerung, echte Mitternacht, Zeitgleichung, Deklination, Distanz, nächstes Aphel/Perihel, nächste **am Ort sichtbare** partielle, ringförmige und totale Sonnenfinsternis
2. Höhendiagramm des Tages mit Vergleichskurven der Sonnenwenden, Uhrzeit-Schieberegler, Höhe/Azimut/Rektaszension/Deklination
3. Monatstabelle (Vor-, aktueller und Folgemonat)
4. Jahreszeiten (Äquinoktien und Solstitien) mit Jahres-Schieberegler

**Mond** (3 Seiten)
1. Details: Aufgang/Untergang/Kulmination, Phase, nächster Neu-/Vollmond, Alter/Sichtbarkeit, untere Kulmination, Distanz, Perigäum/Apogäum, nächste Mondfinsternisse (Kernschatten partiell/total, Halbschatten)
2. Höhendiagramm des Tages
3. Monatstabelle mit Mondbildern

**Orte**
- Liste mit Auf-/Untergangszeiten aller Orte für das gewählte Datum
- Langes Drücken: Auswählen, Umbenennen, Verschieben, Löschen
- Ort hinzufügen über Stadtsuche (Open-Meteo, ohne API-Schlüssel) oder direkte Eingabe der Koordinaten (Grad/Minuten/Sekunden)
- Import/Export als JSON-Datei

**Menü**: Position per GPS, Orte importieren/exportieren, Datum & Uhrzeit wählen, Zeitzone des Ortes ändern, Einstellungen, Datenschutz, Über.
Das Uhr-Symbol in der Leiste wird gelb, sobald ein anderes Datum/eine andere Zeit als „jetzt“ gewählt ist – antippen setzt zurück.

## Genauigkeit

| Größe | Methode | Genauigkeit (typisch) |
|---|---|---|
| Sonne | gekürzte VSOP87 (Meeus, Anhang III) | ~1″ |
| Mond | gekürzte ELP-2000/82 (Meeus, Kap. 47) | ~10″ |
| Auf-/Untergänge | Höhe −0,833° (Sonne) bzw. Parallaxe + Halbmesser (Mond), topozentrisch | < 1 min |
| Jahreszeiten, Mondphasen | Nullstellensuche der scheinbaren Länge/Elongation | ~1–2 min |
| Finsternisse | direkte Geometrie aus Sonnen-/Mondposition | Minuten (Fernzukunft: ΔT-Unsicherheit) |

Die Engine liegt komplett in `app/src/main/java/io/github/lunasolcal/astro/` und hat **keine Android-Abhängigkeiten** – sie lässt sich auch in anderen Kotlin/JVM-Projekten verwenden.
Unit-Tests (`app/src/test`) vergleichen mit Beispielen aus Meeus und Referenzwerten für Wien.

## Bauen

Voraussetzungen: Android Studio (Ladybug oder neuer) bzw. JDK 17 + Android SDK 35.

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # Tests der Astronomie-Engine
```

Oder einfach den Ordner in Android Studio öffnen und ▶ drücken.

Bei jedem Push baut GitHub Actions (`.github/workflows/build.yml`) automatisch eine APK – du findest sie im jeweiligen Workflow-Lauf unter **Artifacts**.

## Auf GitHub veröffentlichen

```bash
cd LunaSolCal
git init
git add .
git commit -m "LunaSolCal: erste Version"
git branch -M main
git remote add origin https://github.com/deved2000/lunaysol.git
git push -u origin main
```

Danach in `app/src/main/java/io/github/lunasolcal/AppInfo.kt` die Konstante `REPO_URL` auf dein Repository setzen (wird für „Glossar“, „FAQ“ und „Über“ verwendet).

## Fertige APK auf GitHub bereitstellen (Releases)

Der Workflow `.github/workflows/release.yml` baut bei jedem Versions-Tag eine signierte APK und hängt sie an ein öffentliches GitHub-Release. Download-Link für alle: `https://github.com/deved2000/lunaysol/releases/latest`

### 1. Einmalig: eigenen Signierschlüssel anlegen (empfohlen)

Android installiert ein Update nur, wenn es mit **demselben Schlüssel** signiert ist. Ohne eigenen Schlüssel signiert GitHub jedes Mal mit einem neuen Zufallsschlüssel – dann muss man die App vor jedem Update deinstallieren.

```bash
keytool -genkeypair -v -keystore release.jks -alias lunasolcal \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64     # macOS: base64 -i release.jks -o release.jks.b64
```

> ⚠️ `release.jks` und die Passwörter gut aufbewahren und **niemals** ins Repository committen (steht bereits in `.gitignore`). Geht der Schlüssel verloren, können Nutzer keine Updates mehr installieren.

Dann im Repository unter **Settings → Secrets and variables → Actions → New repository secret** vier Secrets anlegen:

| Name | Inhalt |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Inhalt der Datei `release.jks.b64` |
| `SIGNING_STORE_PASSWORD` | Keystore-Passwort |
| `SIGNING_KEY_ALIAS` | `lunasolcal` |
| `SIGNING_KEY_PASSWORD` | Schlüssel-Passwort (bei keytool meist gleich dem Keystore-Passwort) |

### 2. Version veröffentlichen

```bash
git tag v1.0.0
git push origin v1.0.0
```

Nach ein paar Minuten erscheint unter **Releases** die Version mit `LunaSolCal-1.0.0.apk`. Der Tag bestimmt den Versionsnamen in der App; die Versionsnummer zählt automatisch hoch.

### 3. Installieren

APK auf dem Handy herunterladen und öffnen. Android fragt einmalig, ob der Browser/Dateimanager „Apps aus unbekannten Quellen“ installieren darf.

## Projektstruktur

```
app/src/main/java/io/github/lunasolcal/
├── astro/          Astronomie-Engine (rein Kotlin/JVM)
│   ├── AstroMath.kt     Winkel, Julianisches Datum, ΔT
│   ├── Vsop87Earth.kt   Sonnentheorie
│   ├── MoonTheory.kt    Mondtheorie
│   ├── Ephemeris.kt     Nutation, Koordinaten, Topozentrik, Beleuchtung
│   ├── Events.kt        Auf-/Untergänge, Dämmerung, Phasen, Apsiden, Jahreszeiten
│   └── Eclipses.kt      Sonnen- und Mondfinsternisse
├── domain/         Verbindung Engine ↔ java.time, deutsche Formatierung
├── data/           Orte, Speicherung, Ortssuche/Zeitzone (Open-Meteo), GPS
└── ui/             Jetpack Compose: Überblick, Sonne, Mond, Orte, Dialoge
```

## Datenschutz

Keine Werbung, kein Tracking, keine Konten. Online-Anfragen (nur Ortssuche und Zeitzonen-Ermittlung) gehen an [Open-Meteo](https://open-meteo.com) und lassen sich in den Einstellungen abschalten.

## Lizenz

MIT – siehe [LICENSE](LICENSE).
