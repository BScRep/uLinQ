package nl.taalinq.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.webkit.WebViewAssetLoader

/**
 * Het enige scherm van de app. Het laadt de web-app uit assets/www/index.html
 * en geeft die via [TaalBridge] toegang tot de voorleesstem van Android.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var brug: TaalBridge
    private var bestandTerug: ValueCallback<Array<Uri>>? = null

    // Een woordenlijst (JSON-bestand) kiezen bij "Lijst inlezen".
    private val kiesBestand = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        bestandTerug?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        bestandTerug = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val kader = FrameLayout(this)
        kader.setBackgroundColor(getColor(R.color.achtergrond))
        web = WebView(this)
        web.setBackgroundColor(getColor(R.color.achtergrond))
        kader.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(kader)

        // Houd de pagina vrij van statusbalk, navigatiebalk en toetsenbord.
        ViewCompat.setOnApplyWindowInsetsListener(kader) { v, insets ->
            val balk = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val toetsen = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(left = balk.left, top = balk.top, right = balk.right, bottom = maxOf(balk.bottom, toetsen.bottom))
            WindowInsetsCompat.CONSUMED
        }

        // De web-app draait op een vast, veilig adres binnen de app, zodat de opgeslagen lijst bewaard blijft.
        val lader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false

        brug = TaalBridge(this, web)
        web.addJavascriptInterface(brug, "TaalApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                lader.shouldInterceptRequest(request.url)

            // Links naar buiten (bijvoorbeeld Google Translate) openen in de browser van de telefoon.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == ADRES_HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {
                }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                terug: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                bestandTerug?.onReceiveValue(null)
                bestandTerug = terug
                val kies = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                return try {
                    kiesBestand.launch(Intent.createChooser(kies, "Woordenlijst kiezen"))
                    true
                } catch (_: Exception) {
                    bestandTerug = null
                    false
                }
            }
        }

        // Terugknop: eerst terug naar het tabblad Overhoren, daarna de app sluiten.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.taalinqTerug ? window.taalinqTerug() : false") { antwoord ->
                    if (antwoord != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        web.loadUrl("https://$ADRES_HOST/assets/www/index.html")
    }

    override fun onResume() {
        super.onResume()
        brug.hervat()   // na het installeren van een stem: stemmenlijst opnieuw ophalen
    }

    override fun onPause() {
        brug.stop()
        super.onPause()
    }

    override fun onDestroy() {
        brug.sluit()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val ADRES_HOST = "appassets.androidplatform.net"
    }
}
