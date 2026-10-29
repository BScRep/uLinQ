# taaLinQ voor Android

Woordjes overhoren in twee richtingen, waarbij elke juiste vertaling telt, per les en bladzijde van je lesmethode (Camino). Deze app is een schil om de web-app heen, met één extra: hij gebruikt de **voorleesstem van Android** zelf. Die werkt wel, terwijl de ingebouwde browser van de Claude-app stil blijft.

## Wat zit waar

| Bestand | Wat het doet |
|---|---|
| `app/src/main/assets/www/index.html` | De hele web-app. Hier zit het meeste werk. |
| `app/src/main/java/nl/taalinq/app/MainActivity.kt` | Opent het scherm, laadt de pagina, regelt de terugknop en het kiezen van een bestand. |
| `app/src/main/java/nl/taalinq/app/TaalBridge.kt` | De brug: voorlezen, stemmen opvragen, kopiëren en delen. In de pagina heet hij `window.TaalApp`. |
| `app/src/main/AndroidManifest.xml` | Het "paspoort": naam, icoon en wat de app mag. |
| `app/src/main/res/` | Icoon en kleuren. |
| `app/build.gradle.kts` | Bouwinstellingen: versienummer en Android-versies. |
| `app/taalinq.keystore` | De vaste sleutel (zie onder). |
| `web/taalinq.html` | Dezelfde pagina voor de online versie (zonder `<html>`-kop). |
| `.github/workflows/apk-bouwen.yml` | Laat GitHub de APK voor je bouwen. |

## De APK bouwen via GitHub

1. Maak op github.com een nieuwe repository, bijvoorbeeld `taalinq`, en kies **Private**.
2. Klik op **uploading an existing file**. Pak de zip uit, open de map `taalinq-android`, selecteer alles erin en **sleep** het naar het uploadvak. Klik op **Commit changes**.
3. Controleer of de map `.github` erin staat. Ontbreekt hij (dat gebeurt vaak), maak het bestand dan zelf aan: **Add file → Create new file**, typ als naam `.github/workflows/apk-bouwen.yml` en plak de inhoud van het blok onderaan deze LEESMIJ. Klik op **Commit changes**.
4. Ga naar **Actions**. De bouw duurt 3 tot 5 minuten.
5. Bij een groen vinkje staat de APK klaar onder **Releases → Laatste build** (rechts op de hoofdpagina van de repository). Open die pagina op je telefoon en tik op `taaLinQ.apk`.

## Installeren

1. Tik op het gedownloade `taaLinQ.apk`. Sta eenmalig toe dat je browser apps installeert.
2. Waarschuwt Play Protect dat de app onbekend is, kies dan *Toch installeren*.

**Een nieuwe versie:** verhoog `versionCode` in `app/build.gradle.kts` (1 → 2 → 3…), stuur de wijziging naar GitHub en installeer de nieuwe APK over de oude heen. **Verwijder de app niet**, want dan is je woordenlijst weg.

## Je woordenlijst overzetten

De app bewaart zijn eigen lijst, los van de online versie. Ga online naar **Delen → Kopiëren**, en plak de tekst in de app bij **Delen → Lijst inlezen → Samenvoegen**. In de app kun je een lijst ook met **Delen…** via WhatsApp, mail of Signal naar iemand anders sturen.

## Voorlezen

De app gebruikt de stem van Android (meestal *Spraakservices van Google*). Hoor je niets, of meldt de app dat er geen Spaanse stem is:

- Tik op **Stem installeren**, of ga naar *Instellingen → Toegankelijkheid → Tekst-naar-spraak* en download daar Spaans (Spanje).
- Zet het **mediavolume** omhoog. Voorlezen gaat niet via het belvolume.
- Kies bij de instellingen van de overhoring een stem zonder "online" ervoor als je ook zonder internet wilt oefenen.

## De sleutel

Android accepteert een update alleen als die met dezelfde sleutel is ondertekend. Die sleutel zit in `app/taalinq.keystore`, met wachtwoord `taalinq`. Voor een app die alleen jij gebruikt is dit prima. Houd de repository wel **privé**.

## Zelf aanpassen

- **Scherm of werking:** in `assets/www/index.html`. Die kun je ook in Chrome op je computer openen om snel iets uit te proberen; alleen de brug werkt daar niet.
- **Fouten zoeken op je telefoon:** sluit je telefoon met USB aan, open in Chrome op je computer `chrome://inspect` en klik bij taaLinQ op *inspect*.
- **Een functie aan de brug toevoegen:** zet in `TaalBridge.kt` een functie met `@JavascriptInterface` erboven, en roep hem in de pagina aan met `window.TaalApp.jouwFunctie()`.

Loop je vast, plak dan de foutmelding in een gesprek met Claude.

## Inhoud van `.github/workflows/apk-bouwen.yml`

```yaml
# Bouwt de APK op de computers van GitHub, bij elke wijziging die je naar GitHub stuurt.
# Daarna staat hij klaar onder: Releases -> "Laatste build" (direct te downloaden op je telefoon)
# en onder: Actions -> de laatste run -> Artifacts -> taaLinQ-apk
name: APK bouwen

on:
  push:
  workflow_dispatch:   # knop "Run workflow" om handmatig te bouwen

permissions:
  contents: write      # nodig om de APK bij Releases te zetten

jobs:
  bouwen:
    runs-on: ubuntu-latest
    steps:
      - name: Code ophalen
        uses: actions/checkout@v4

      - name: Java 17 installeren
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Gradle klaarzetten
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.11.1"

      - name: APK bouwen
        run: gradle assembleDebug --no-daemon

      - name: APK een duidelijke naam geven
        run: cp app/build/outputs/apk/debug/app-debug.apk taaLinQ.apk

      - name: APK beschikbaar maken onder Actions
        uses: actions/upload-artifact@v4
        with:
          name: taaLinQ-apk
          path: taaLinQ.apk

      - name: APK bij Releases zetten
        uses: softprops/action-gh-release@v2
        with:
          tag_name: laatste-build
          name: Laatste build
          body: "Automatisch gebouwd. Download taaLinQ.apk op je telefoon en installeer hem over de vorige versie heen."
          files: taaLinQ.apk
          make_latest: true
```
