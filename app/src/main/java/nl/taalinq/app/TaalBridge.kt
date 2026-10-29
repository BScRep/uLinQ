package nl.taalinq.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * De brug tussen de web-app en Android. In de pagina heet hij window.TaalApp.
 * Elke functie met @JavascriptInterface kan de pagina aanroepen.
 */
class TaalBridge(private val act: Activity, private val web: WebView) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(act.applicationContext, this)
    @Volatile private var klaar = false

    override fun onInit(status: Int) {
        klaar = status == TextToSpeech.SUCCESS
        if (klaar) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = meld("start")
                override fun onDone(utteranceId: String?) = meld("klaar")
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = meld("fout")
            })
        }
        meld(if (klaar) "gereed" else "geen")
    }

    /** Laat de pagina weten wat de voorleesstem doet (gereed, start, klaar, fout). */
    private fun meld(wat: String) {
        act.runOnUiThread {
            web.evaluateJavascript("window.taalinqTts && window.taalinqTts('$wat')", null)
        }
    }

    @JavascriptInterface
    fun gereed(): Boolean = klaar

    /** De stemmen voor een taal ("es" of "nl"), als JSON-lijst. Stemmen uit Spanje of Nederland eerst. */
    @JavascriptInterface
    fun stemmen(taal: String): String {
        val lijst = JSONArray()
        if (!klaar) return lijst.toString()
        try {
            val land = if (taal == "es") "ES" else "NL"
            tts.voices
                ?.filter { it.locale.language == taal }
                ?.sortedWith(compareBy({ it.locale.country != land }, { it.isNetworkConnectionRequired }, { it.name }))
                ?.forEach { v ->
                    lijst.put(
                        JSONObject()
                            .put("id", v.name)
                            .put("lang", v.locale.toLanguageTag())
                            .put("offline", !v.isNetworkConnectionRequired)
                    )
                }
        } catch (_: Exception) {
        }
        return lijst.toString()
    }

    /** Leest tekst voor. Geeft "ok", "geen" (stem nog niet klaar) of "taal" (taal niet aanwezig) terug. */
    @JavascriptInterface
    fun spreek(tekst: String, taal: String, tempo: Float, stem: String): String {
        if (!klaar) return "geen"
        val gekozen = if (stem.isNotEmpty()) {
            try { tts.voices?.firstOrNull { it.name == stem } } catch (_: Exception) { null }
        } else null
        if (gekozen != null) {
            tts.voice = gekozen
        } else {
            val uitkomst = tts.setLanguage(Locale.forLanguageTag(if (taal == "es") "es-ES" else "nl-NL"))
            if (uitkomst == TextToSpeech.LANG_MISSING_DATA || uitkomst == TextToSpeech.LANG_NOT_SUPPORTED) return "taal"
        }
        tts.setSpeechRate(tempo)
        tts.speak(tekst, TextToSpeech.QUEUE_FLUSH, null, "taalinq-" + System.nanoTime())
        return "ok"
    }

    @JavascriptInterface
    fun stop() {
        if (klaar) tts.stop()
    }

    /** Opent het scherm van Android om een stem (taal) te downloaden. */
    @JavascriptInterface
    fun installeerStem() {
        act.runOnUiThread {
            try {
                act.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (_: Exception) {
                try {
                    act.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                } catch (_: Exception) {
                }
            }
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
        tts.stop()
        tts.shutdown()
    }
}
