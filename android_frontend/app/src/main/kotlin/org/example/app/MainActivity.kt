package org.example.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * PUBLIC_INTERFACE
 * MainActivity: The main screen for VoiceCalc Pro.
 * - Provides calculator keypad and text input.
 * - Microphone button for voice input with real-time transcription.
 * - Results area for showing computed result or error.
 * - Navigation drawer to access History.
 * - Integrates with AI backend over HTTP for evaluation.
 *
 * Env variables required (provided via manifestPlaceholders mapping by orchestrator):
 * - AI_BASE_URL: Base URL for the AI backend.
 * - AI_API_KEY: Optional API key for authorization header.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_RECORD_AUDIO = 1001
        private const val TIMEOUT_MS = 15000L
        private const val HTTP_TIMEOUT_MS = 15000L
    }

    private lateinit var viewModel: CalcViewModel
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    private lateinit var inputEditText: EditText
    private lateinit var resultText: TextView
    private lateinit var transcriptionText: TextView
    private lateinit var micButton: Button
    private lateinit var equalsButton: Button
    private lateinit var keypad: View
    private lateinit var historyRecycler: RecyclerView
    private lateinit var historyContainer: LinearLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var drawerList: ListView

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme preference before setContentView (kept simple, no dark theme switch to avoid material deps)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[CalcViewModel::class.java]

        toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        drawerList = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.nav_open_drawer,
            R.string.nav_close_drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Simple drawer list items (Calculator, History)
        val items = listOf(getString(R.string.menu_calculator), getString(R.string.menu_history))
        drawerList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        drawerList.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> showMainContent()
                1 -> showHistory()
            }
            drawerLayout.closeDrawers()
        }

        inputEditText = findViewById(R.id.inputEditText)
        resultText = findViewById(R.id.resultText)
        transcriptionText = findViewById(R.id.transcriptionText)
        micButton = findViewById(R.id.micButton)
        equalsButton = findViewById(R.id.equalsButton)
        keypad = findViewById(R.id.keypadContainer)
        historyRecycler = findViewById(R.id.historyRecycler)
        historyContainer = findViewById(R.id.historyContainer)
        mainContent = findViewById(R.id.mainContent)

        // History list setup
        historyRecycler.layoutManager = LinearLayoutManager(this)
        historyRecycler.adapter = HistoryAdapter(viewModel.history) { item ->
            inputEditText.setText(item.expression)
            resultText.text = item.result
            drawerLayout.closeDrawers()
        }

        // Input listeners
        equalsButton.setOnClickListener {
            computeExpression()
        }
        inputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                computeExpression()
                true
            } else false
        }
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                resultText.text = ""
            }
        })

        setupKeypad()

        micButton.setOnClickListener {
            startVoiceInputWithPermission()
        }

        showMainContent()
    }

    private fun setupKeypad() {
        val ids = listOf(
            R.id.key0 to "0",
            R.id.key1 to "1",
            R.id.key2 to "2",
            R.id.key3 to "3",
            R.id.key4 to "4",
            R.id.key5 to "5",
            R.id.key6 to "6",
            R.id.key7 to "7",
            R.id.key8 to "8",
            R.id.key9 to "9",
            R.id.keyDot to ".",
            R.id.keyPlus to "+",
            R.id.keyMinus to "-",
            R.id.keyMultiply to "*",
            R.id.keyDivide to "/",
            R.id.keyOpenParen to "(",
            R.id.keyCloseParen to ")",
            R.id.keyPow to "^",
            R.id.keySin to "sin(",
            R.id.keyCos to "cos(",
            R.id.keyTan to "tan(",
            R.id.keySqrt to "sqrt(",
            R.id.keyClear to "CLEAR",
            R.id.keyDel to "DEL"
        )
        ids.forEach { (id, symbol) ->
            findViewById<Button>(id).setOnClickListener {
                when (symbol) {
                    "CLEAR" -> inputEditText.setText("")
                    "DEL" -> {
                        val txt = inputEditText.text?.toString() ?: ""
                        if (txt.isNotEmpty()) {
                            inputEditText.setText(txt.substring(0, txt.length - 1))
                            inputEditText.setSelection(inputEditText.text?.length ?: 0)
                        }
                    }
                    else -> {
                        val cur = inputEditText.text?.toString() ?: ""
                        val updated = cur + symbol
                        inputEditText.setText(updated)
                        inputEditText.setSelection(updated.length)
                    }
                }
            }
        }
    }

    private fun startVoiceInputWithPermission() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO
            )
            return
        }
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, getString(R.string.speech_not_available), Toast.LENGTH_LONG).show()
            return
        }
        if (listening) {
            stopListening()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                transcriptionText.text = getString(R.string.listening)
            }
            override fun onBeginningOfSpeech() {
                transcriptionText.text = ""
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                transcriptionText.text = getString(R.string.speech_error_generic, error)
                Toast.makeText(this@MainActivity, getString(R.string.speech_error_generic, error), Toast.LENGTH_LONG).show()
                stopListening()
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    transcriptionText.text = text
                    inputEditText.setText(text)
                    inputEditText.setSelection(text.length)
                    computeExpression()
                } else {
                    transcriptionText.text = getString(R.string.no_transcription)
                }
                stopListening()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    transcriptionText.text = text
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
            listening = true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.speech_not_available), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.speech_error_message, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopListening() {
        try { speechRecognizer?.stopListening() } catch (_: Exception) { }
        try { speechRecognizer?.destroy() } catch (_: Exception) { }
        speechRecognizer = null
        listening = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMainContent() {
        historyContainer.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun showHistory() {
        historyContainer.visibility = View.VISIBLE
        mainContent.visibility = View.GONE
        supportActionBar?.title = getString(R.string.history_title)
    }

    private fun computeExpression() {
        val expression = inputEditText.text?.toString()?.trim() ?: ""
        if (expression.isBlank()) {
            Toast.makeText(this, getString(R.string.empty_expression), Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val result = AiClient(this).evaluateExpression(expression)
            runOnUiThread {
                if (result.error != null) {
                    resultText.setTextColor(ContextCompat.getColor(this, R.color.secondaryDark))
                    resultText.text = getString(R.string.error_prefix, result.error)
                    Toast.makeText(this, getString(R.string.error_prefix, result.error), Toast.LENGTH_LONG).show()
                } else {
                    resultText.setTextColor(ContextCompat.getColor(this, R.color.secondary))
                    resultText.text = result.value ?: ""
                    viewModel.addHistory(HistoryItem(expression, result.value ?: ""))
                    historyRecycler.adapter?.notifyDataSetChanged()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}

/**
 * ViewModel for storing calculation history during Activity lifecycle.
 */
class CalcViewModel : ViewModel() {
    val history: MutableList<HistoryItem> = mutableListOf()

    // PUBLIC_INTERFACE
    fun addHistory(item: HistoryItem) {
        /** Adds a new HistoryItem to the in-memory list. */
        history.add(0, item)
    }
}

/**
 * PUBLIC_INTERFACE
 * HistoryItem: Represents a single calculation history entry.
 */
data class HistoryItem(
    val expression: String,
    val result: String
)

/**
 * Simple RecyclerView Adapter for history list.
 */
class HistoryAdapter(
    private val items: List<HistoryItem>,
    private val onClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val expr: TextView = view.findViewById(R.id.historyExpr)
        val res: TextView = view.findViewById(R.id.historyRes)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.expr.text = item.expression
        holder.res.text = item.result
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

/**
 * PUBLIC_INTERFACE
 * AiClient: Handles communication with AI backend.
 * Uses environment variables mapped as manifest placeholders by orchestrator to avoid hardcoding.
 * Expected env placeholders:
 *  - AI_BASE_URL
 *  - AI_API_KEY (optional)
 */
class AiClient(private val activity: Activity) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15000, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class EvalResult(val value: String? = null, val error: String? = null)

    // PUBLIC_INTERFACE
    fun evaluateExpression(expression: String): EvalResult {
        /** Sends the expression to the AI backend and returns the evaluated result or an error. */
        val baseUrl = Env.get(activity, "AI_BASE_URL")
        if (baseUrl.isNullOrBlank()) {
            return EvalResult(error = activity.getString(R.string.env_missing, "AI_BASE_URL"))
        }
        val url = if (baseUrl.endsWith("/")) "${baseUrl}v1/calc" else "$baseUrl/v1/calc"

        val bodyJson = JSONObject().put("expression", expression).toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(mediaType))

        Env.get(activity, "AI_API_KEY")?.takeIf { it.isNotBlank() }?.let { key ->
            requestBuilder.addHeader("Authorization", "Bearer $key")
        }

        val request = requestBuilder.build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return EvalResult(error = "HTTP ${resp.code}")
                }
                val txt = resp.body?.string() ?: return EvalResult(error = "Empty body")
                val json = JSONObject(txt)
                val error = if (json.has("error") && !json.isNull("error")) json.optString("error") else null
                if (error != null && error.isNotBlank()) {
                    EvalResult(error = error)
                } else {
                    val value = json.optString("result", "")
                    EvalResult(value = value)
                }
            }
        } catch (io: IOException) {
            EvalResult(error = "Network error: ${io.message}")
        } catch (e: Exception) {
            EvalResult(error = "Parse error: ${e.message}")
        }
    }
}

/**
 * PUBLIC_INTERFACE
 * Env: Fetches environment variables through manifest placeholders or system env.
 * The orchestrator should map .env -> manifestPlaceholders at build time.
 */
object Env {
    fun get(activity: Activity, key: String): String? {
        return try {
            val ai = activity.packageManager.getApplicationInfo(activity.packageName, PackageManager.GET_META_DATA)
            val v = ai.metaData?.getString(key)
            if (!v.isNullOrBlank()) v else System.getenv(key)
        } catch (_: Exception) {
            System.getenv(key)
        }
    }
}
