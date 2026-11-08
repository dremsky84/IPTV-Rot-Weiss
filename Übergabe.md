# Übergabe – IPTV Rot-Weiss (Android)

Stand: 08.11.2026 · Version 1.1 (versionCode 2) · Eigentümer: Dirk Remsky

## Zweck
Android-IPTV-App (Xtream Codes) für einen 1.-FC-Köln-Fan (andere Person, eigener Zugang). Variante von **XtreamTV** (Repo `dremsky84/XtreamTV`, gleiche Basis ohne FC-Teil). Handy **und** Amazon FireTV (nur Fernbedienung). Keine Benachrichtigungen.

## Repo / Build
- Repo: `github.com/dremsky84/IPTV-Rot-Weiss`, Branch `main`. Paket `de.remsky.rotweiss`.
- **Lokal nicht baubar in der Cloud-Sandbox** (Google/Maven blockiert). Build läuft über GitHub Actions: `.github/workflows/build-apk.yml` – Push auf `main` → `gradle assembleDebug` (Gradle 8.9, JDK 17) → Release-Tag `latest` mit `IPTV-Rot-Weiss.apk`. Bei Fehlern gibt der Schritt „Fehler anzeigen" Compilerfehler als Annotations aus.
- **Dirks Regel: APK/Build (= Push auf `main`) nur, wenn er „bauen" sagt.** Zwischenstände auf Branch `vorschau` sichern (löst keinen Build aus).
- Dirk will künftig lokal bauen: mit Android Studio am PC `./gradlew assembleDebug` (oder Projekt öffnen, Gradle-Sync, Run).
- Signierung: fester Keystore `app/rotweiss.keystore` (Passwort `rotweiss`, Alias `rotweiss`), damit Updates über die alte Version installieren. Nicht ändern!
- Git: Autor `Claude <noreply@anthropic.com>`; Commits enden mit `Co-Authored-By: …`-Trailer (optional bei anderem Modell/Tool).

## Technik
Kotlin, Jetpack Compose (BOM 2024.09.00, Material3 1.3.0), Media3 ExoPlayer 1.4.1 (HLS/TS), OkHttp 4.12, Coil 2.7, EncryptedSharedPreferences, org.json. minSdk 23, targetSdk/compileSdk 34. Cleartext-HTTP erlaubt.

## Code-Übersicht (`app/src/main/java/de/remsky/rotweiss/`)
- `MainActivity.kt` – Einstieg, TV-Erkennung (`TvMode`).
- `PlayerActivity.kt` – Vollbild-Player; Fernbedienung: Hoch/CH+ = nächster Kanal, Runter = vorheriger, Links/Rechts = Spulen 10/30/60 s, OK = Info/Pause, Medientasten; PiP nur auf Nicht-TV.
- `data/XtreamApi.kt` – player_api.php (Live, VOD, Serien, Kurz-EPG, Katch-up/Timeshift-URLs).
- `data/FootballApi.kt` – OpenLigaDB (`getmatchdata/{bl1|bl2}/{Saison}`, `getbltable`), Verein per Name „FC"+„Köln" erkannt, Liga automatisch.
- `data/LocalStore.kt` (Favoriten, Verlauf, PIN/gesperrte Kategorien, FC-Kanäle), `data/CredentialStore.kt` (verschlüsselte Zugangsdaten), `player/PlayQueue.kt`.
- `ui/AppViewModel.kt` (Tabs FC/LIVE/MOVIES/SERIES/FAVORITES, Routes), `MainScreen.kt` (Bottom-Bar am Handy, NavigationRail am TV), `FcScreen.kt` (FC-Zentrale: Hero mit Foto, nächstes/letztes Spiel, Spielplan, Tabelle, FC-Kanäle), `LiveScreen/MoviesScreen/SeriesScreen/FavoritesScreen/SettingsScreen/LoginScreen.kt`, `Common.kt` (Farben FcRed #E3141B, TvTheme, `focusHighlight`, `GuardedTextField`: am TV erst nach Klick editierbar).

## Ressourcen
- Launcher-Icon: vom Nutzer geliefertes Bild, als `mipmap-*/ic_launcher.png` (abgerundet, kein Adaptive-Icon, `mipmap-anydpi-v26` bewusst entfernt).
- TV-Banner `drawable-xhdpi/banner.png`; `drawable-nodpi/`: `fan_photo.jpg`, `fan_faces.jpg`, `fc_stadium.png`, `fc_dom.png`.
- Markenrecht: kein offizielles FC-Wappen, kein Hennes; Dirk hat das aktuelle Icon (Ziegenbock/Dom/Schild) selbst geliefert und freigegeben.

## Erledigt
Login (Xtream), Live-TV mit EPG/Catch-up, Filme, Serien, Favoriten, Verlauf, Kindersicherung (PIN), FC-Zentrale mit OpenLigaDB, FireTV-Optimierung (D-Pad, Fokus, Rail), Klick-zum-Editieren bei Textfeldern, FC-Kopf mit Foto + Scroll-Fix, neues Icon.

## Offen / Ideen
- Icon in höherer Auflösung (Original ~380 px, wirkt auf hochauflösenden Displays leicht weich); optional Adaptive-Icon.
- Auf echtem FireTV weiter testen (Fokusreihenfolge, Player-Tasten).
- Release-Build/Minify nicht eingerichtet (derzeit Debug-APK mit festem Key).

## Hinweise zur Zusammenarbeit
Dirk schreibt Deutsch, mag kurze Antworten, tokensparend. Konzept erst besprechen bei größeren Vorhaben; nach Änderungen Vorschau/Screenshot zeigen, dann auf „bauen" warten.
