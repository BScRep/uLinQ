package nl.taalinq.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * De brug tussen de web-app en Android. In de pagina heet hij window.TaalApp.
 * Elke functie met @JavascriptInterface kan de pagina aanroepen.
 *
 * Voorlezen gaat via het spraakprogramma van de telefoon (Google of Samsung).
 * Lukt een stem niet, dan probeert de brug zelf de volgende: eerst de gekozen stem,
 * dan de standaardstem van de taal, dan de gedownloade stemmen van die taal.
 * Pas als niets lukt, krijgt de pagina "fout:<code>" te horen.
 */
class TaalBridge(private val act: Activity, private val web: WebView) {

    private var tts: TextToSpeech? = null
    @Volatile private var klaar = false
    @Volatile private var programma = ""          // pakketnaam van het spraakprogramma; leeg = standaard
    private var generatie = 0
    private var herstartBijTerugkeer = false

    // Wat er nu voorgelezen wordt, en welke stemmen we nog kunnen proberen als het misgaat.
    private var tekst = ""
    private var taal = "es"
    private var tempo = 1f
    private val nogTeProberen = ArrayDeque<Voice?>()   // null = standaardstem van de taal
    private var huidigId = ""

    init {
        start("")
    }

    /** (Her)start het spraakprogramma. Moet op de hoofd-thread. */
    private fun start(pakket: String) {
        klaar = false
        try { tts?.shutdown() } catch (_: Exception) { }
        programma = pakket
        val mijn = ++generatie
        val luister = TextToSpeech.OnInitListener { status -> klaarGezet(mijn, status) }
        tts = if (pakket.isEmpty()) TextToSpeech(act.applicationContext, luister)
              else TextToSpeech(act.applicationContext, luister, pakket)
    }

    private fun klaarGezet(mijn: Int, status: Int) {
        if (mijn != generatie) return
        val t = tts ?: return
        klaar = status == TextToSpeech.SUCCESS
        if (klaar) {
            if (programma.isEmpty()) programma = t.defaultEngine ?: ""
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = meld("start")
                override fun onDone(utteranceId: String?) = meld("klaar")
                override fun onError(utteranceId: String?, errorCode: Int) = mislukt(utteranceId, errorCode)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = mislukt(utteranceId, -1)
            })
        }
        meld(if (klaar) "gereed" else "fout:init")
    }

    /** Laat de pagina weten wat de voorleesstem doet (gereed, start, klaar, fout:<code>). */
    private fun meld(wat: String) {
        act.runOnUiThread {
            web.evaluateJavascript("window.taalinqTts && window.taalinqTts('$wat')", null)
        }
    }

    private fun mislukt(id: String?, code: Int) {
        synchronized(this) {
            if (id != huidigId) return
            if (nogTeProberen.isNotEmpty()) {
                volgendePoging()
                return
            }
        }
        meld("fout:$code")
    }

    private fun locale(t: String): Locale = Locale.forLanguageTag(if (t == "es") "es-ES" else "nl-NL")

    private fun stemmenVan(t: String): List<Voice> {
        val land = if (t == "es") "ES" else "NL"
        return try {
            tts?.voices
                ?.filter { it.locale.language == t }
                ?.sortedWith(compareBy(
                    { it.locale.country != land },
                    { !geinstalleerd(it) },
                    { it.isNetworkConnectionRequired },
                    { it.name }))
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun geinstalleerd(v: Voice): Boolean =
        !(v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ?: false)

    @JavascriptInterface
    fun gereed(): Boolean = klaar

    /** De stemmen voor een taal ("es" of "nl"), als JSON-lijst. Spanje/Nederland en gedownloade stemmen eerst. */
    @JavascriptInterface
    fun stemmen(t: String): String {
        val lijst = JSONArray()
        if (!klaar) return lijst.toString()
        stemmenVan(t).forEach { v ->
            lijst.put(
                JSONObject()
                    .put("id", v.name)
                    .put("lang", v.locale.toLanguageTag())
                    .put("offline", !v.isNetworkConnectionRequired)
                    .put("geinstalleerd", geinstalleerd(v))
            )
        }
        return lijst.toString()
    }

    /** De spraakprogramma's op de telefoon (bijvoorbeeld Google en Samsung), als JSON-lijst. */
    @JavascriptInterface
    fun programmas(): String {
        val lijst = JSONArray()
        val t = tts ?: return lijst.toString()
        val standaard = try { t.defaultEngine } catch (_: Exception) { null }
        try {
            t.engines.forEach { e ->
                lijst.put(JSONObject().put("id", e.name).put("naam", e.label).put("standaard", e.name == standaard))
            }
        } catch (_: Exception) { }
        return lijst.toString()
    }

    @JavascriptInterface
    fun programma(): String = programma

    /** Wisselt van spraakprogramma. De pagina hoort "gereed" als het nieuwe programma klaar is. */
    @JavascriptInterface
    fun kiesProgramma(pakket: String) {
        act.runOnUiThread { start(pakket) }
    }

    /** Leest tekst voor. Geeft "ok", "geen" (stem nog niet klaar) of "taal" (geen enkele stem voor deze taal) terug. */
    @JavascriptInterface
    fun spreek(tekst: String, taal: String, tempo: Float, stem: String): String {
        if (!klaar) return "geen"
        val alle = stemmenVan(taal)
        synchronized(this) {
            this.tekst = tekst
            this.taal = taal
            this.tempo = tempo
            nogTeProberen.clear()
            alle.firstOrNull { it.name == stem }?.let { nogTeProberen.addLast(it) }
            nogTeProberen.addLast(null)
            alle.filter { geinstalleerd(it) && !it.isNetworkConnectionRequired && it.name != stem }
                .take(3)
                .forEach { nogTeProberen.addLast(it) }
        }
        val beschikbaar = try { tts?.isLanguageAvailable(locale(taal)) ?: TextToSpeech.LANG_NOT_SUPPORTED }
                          catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
        if (alle.isEmpty() && beschikbaar < TextToSpeech.LANG_AVAILABLE) return "taal"
        synchronized(this) { volgendePoging() }
        return "ok"
    }

    /** Probeert de volgende stem uit de lijst. Aanroepen binnen synchronized(this). */
    private fun volgendePoging() {
        val t = tts ?: return
        val stem = nogTeProberen.removeFirst()
        try {
            if (stem != null) {
                t.setVoice(stem)
            } else {
                t.setLanguage(locale(taal))
            }
            t.setSpeechRate(tempo)
            huidigId = "taalinq-" + System.nanoTime()
            val r = t.speak(tekst, TextToSpeech.QUEUE_FLUSH, null, huidigId)
            if (r == TextToSpeech.ERROR && nogTeProberen.isNotEmpty()) volgendePoging()
            else if (r == TextToSpeech.ERROR) meld("fout:speak")
        } catch (_: Exception) {
            if (nogTeProberen.isNotEmpty()) volgendePoging() else meld("fout:speak")
        }
    }

    @JavascriptInterface
    fun stop() {
        if (klaar) try { tts?.stop() } catch (_: Exception) { }
    }

    /** Opent het scherm van Android om een stem (taal) te downloaden. Bij terugkomst laden we de stemmen opnieuw. */
    @JavascriptInterface
    fun installeerStem() {
        act.runOnUiThread {
            herstartBijTerugkeer = true
            val installeer = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            if (programma.isNotEmpty()) installeer.setPackage(programma)
            try {
                act.startActivity(installeer)
            } catch (_: Exception) {
                try {
                    act.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                } catch (_: Exception) {
                    herstartBijTerugkeer = false
                }
            }
        }
    }

    /** Aangeroepen door MainActivity als de app weer in beeld komt. */
    fun hervat() {
        if (herstartBijTerugkeer) {
            herstartBijTerugkeer = false
            start(programma)
        }
    }

    /** Zet tekst op het klembord. */
    @JavascriptInterface
    fun kopieer(tekst: String) {
        act.runOnUiThread {
            val klembord = act.getSystemService(ClipboardManager::class.java)
            klembord?.setPrimaryClip(ClipData.newPlainText("taaLinQ", tekst))
        }
    }

    /** Opent het deelmenu (WhatsApp, mail, Signal...) met de woordenlijst als tekst. */
    @JavascriptInterface
    fun deel(tekst: String, titel: String) {
        act.runOnUiThread {
            val stuur = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, titel)
                .putExtra(Intent.EXTRA_TEXT, tekst)
            act.startActivity(Intent.createChooser(stuur, "Woordenlijst delen"))
        }
    }

    @JavascriptInterface
    fun versie(): String = BuildConfig.VERSION_NAME

    fun sluit() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) { }
    }
}
