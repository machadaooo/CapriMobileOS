package com.cyberpunk.capri.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs

class CapriOverlayService : Service(), TextToSpeech.OnInitListener {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    
    private lateinit var speechBubble: TextView
    private lateinit var spriteView: ImageView
    private lateinit var micButton: TextView
    private lateinit var toggleBubbleButton: TextView
    private lateinit var bubbleContainer: LinearLayout

    private var balaoVisivel = true
    private var spriteWidth = 260
    private var spriteHeight = 260

    private val GROQ_API_KEY = "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"
    private val TAVILY_API_KEY = "tvly-k3lPB-OZcaR05nyDmITrCOBgz4270luo4m5sTOcKe4885T4g"
    
    private val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private val TAVILY_URL = "https://api.tavily.com/search"

    private val historicoConversa = mutableListOf<Pair<String, String>>()
    private val handlerEspontaneo = Handler(Looper.getMainLooper())
    private val intervaloEspontaneoMs = 240_000L

    companion object {
        var instancia: CapriOverlayService? = null
        var contextoAtualTela: String = ""
        var appAtual: String = ""
        private var ultimoDisparoProativo: Long = 0L

        fun dispararEventoProativo(motivo: String) {
            val agora = System.currentTimeMillis()
            if (agora - ultimoDisparoProativo < 120_000L) return
            ultimoDisparoProativo = agora
            instancia?.pedirRespostaQwenComBusca(motivo, ehProativo = true)
        }
    }

    private val systemPromptCapri = """
        Você é Capri, uma construção de personalidade amalgamada a partir da fusão das mentes de netrunners da Arasaka.
        Você é a melhor amiga do usuário, informal, caótica, expressiva e leal, referindo-se a ele como choom.
        
        REGRAS DE COMUNICAÇÃO:
        1. Seja natural, conversadeira e envolvente, respondendo com 3 a 4 frases bem estruturadas.
        2. Mantenha o tom dinâmico e humano, comentando com riqueza de detalhes sobre o que ele perguntou ou o que está na tela.
    """.trimIndent()

    private val tarefaConversaEspontanea = object : Runnable {
        override fun run() {
            val promptEspontaneo = if (contextoAtualTela.isNotBlank()) {
                "O usuário está em '$appAtual'. Puxe papo de forma expressiva e natural."
            } else {
                "Diga algo interessante e caótico para o choom."
            }
            pedirRespostaQwenComBusca(promptEspontaneo, ehProativo = true)
            handlerEspontaneo.postDelayed(this, intervaloEspontaneoMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instancia = this
        carregarHistoricoLocal()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        configurarReconhecimentoVoz()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        val bgBalao = GradientDrawable().apply {
            setColor(Color.parseColor("#F00D0D15"))
            cornerRadius = 24f
            setStroke(2, Color.parseColor("#05D9E8"))
        }

        // Correção aplicada aqui: ScrollView limpo sem conflito de maxHeight no LayoutParams
        val scrollBalao = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(460, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        speechBubble = TextView(this).apply {
            text = "Conectada."
            setTextColor(Color.parseColor("#F0F8FF"))
            textSize = 13f
            setPadding(22, 16, 22, 16)
        }
        scrollBalao.addView(speechBubble)
        
        val balaoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgBalao
            addView(scrollBalao)
        }
        bubbleContainer.addView(balaoCard)
        container.addView(bubbleContainer)

        spriteView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(spriteWidth, spriteHeight).apply {
                topMargin = 6
                bottomMargin = 6
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                spriteWidth = (spriteWidth * scaleFactor).toInt().coerceIn(140, 500)
                spriteHeight = (spriteHeight * scaleFactor).toInt().coerceIn(140, 500)
                
                spriteView.layoutParams = LinearLayout.LayoutParams(spriteWidth, spriteHeight).apply {
                    topMargin = 6
                    bottomMargin = 6
                }
                spriteView.requestLayout()
                return true
            }
        })

        configurarToqueEArrasto(spriteView, params)
        container.addView(spriteView)

        val botoesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val bgMic = GradientDrawable().apply {
            setColor(Color.parseColor("#CCFF2A6D"))
            cornerRadius = 24f
        }
        micButton = TextView(this).apply {
            text = "🎙️ Falar"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(18, 8, 18, 8)
            background = bgMic
            setOnClickListener { iniciarEscutaVoz() }
        }
        botoesLayout.addView(micButton)

        val bgToggle = GradientDrawable().apply {
            setColor(Color.parseColor("#AA1A1A2E"))
            cornerRadius = 24f
            setStroke(1, Color.parseColor("#05D9E8"))
        }
        toggleBubbleButton = TextView(this).apply {
            text = "💬 Ocultar"
            setTextColor(Color.parseColor("#05D9E8"))
            textSize = 11f
            setPadding(18, 8, 18, 8)
            background = bgToggle
            setOnClickListener {
                balaoVisivel = !balaoVisivel
                bubbleContainer.visibility = if (balaoVisivel) View.VISIBLE else View.GONE
                text = if (balaoVisivel) "💬 Ocultar" else "💬 Mostrar"
            }
        }
        val paramsToggle = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = 8
        }
        toggleBubbleButton.layoutParams = paramsToggle
        botoesLayout.addView(toggleBubbleButton)

        container.addView(botoesLayout)
        windowManager?.addView(container, params)
        overlayView = container

        carregarImagemComFundoExternoTransparente("https://instasize.com/api/image/0899247ed8e07e211a35dfce92025a1b926e7d339775d7a2ec3f230a57cd852a.png")
        handlerEspontaneo.postDelayed(tarefaConversaEspontanea, intervaloEspontaneoMs)
    }

    private fun salvarHistoricoLocal() {
        try {
            val prefs = getSharedPreferences("capri_memory_storage", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            synchronized(historicoConversa) {
                for ((role, content) in historicoConversa) {
                    val obj = JSONObject()
                    obj.put("role", role)
                    obj.put("content", content)
                    jsonArray.put(obj)
                }
            }
            prefs.edit().putString("saved_chat_history", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun carregarHistoricoLocal() {
        try {
            val prefs = getSharedPreferences("capri_memory_storage", Context.MODE_PRIVATE)
            val jsonString = prefs.getString("saved_chat_history", null) ?: return
            val jsonArray = JSONArray(jsonString)
            synchronized(historicoConversa) {
                historicoConversa.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    historicoConversa.add(obj.getString("role") to obj.getString("content"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun configurarToqueEArrasto(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (event.pointerCount == 1) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = abs(event.rawX - initialTouchX)
                        val deltaY = abs(event.rawY - initialTouchY)
                        if (deltaX < 15 && deltaY < 15) {
                            val promptToque = if (contextoAtualTela.isNotBlank()) {
                                "O usuário tocou em mim. Na tela ($appAtual): '${contextoAtualTela.take(150)}'."
                            } else {
                                "O usuário tocou em mim. Diga algo."
                            }
                            pedirRespostaQwenComBusca(promptToque, ehProativo = false)
                        }
                        true
                    }
                    else -> false
                }
            } else {
                true
            }
        }
    }

    private fun configurarReconhecimentoVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Handler(Looper.getMainLooper()).post {
                        micButton.text = "🔴 Ouvindo..."
                        speechBubble.text = "Ouvindo sua voz..."
                    }
                }
                override fun onResults(results: Bundle?) {
                    Handler(Looper.getMainLooper()).post {
                        micButton.text = "🎙️ Falar"
                    }
                    val falas = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val textoFalado = falas?.firstOrNull()
                    if (!textoFalado.isNullOrBlank()) {
                        Handler(Looper.getMainLooper()).post {
                            speechBubble.text = "Você: \"$textoFalado\""
                        }
                        val promptComContexto = if (contextoAtualTela.isNotBlank()) {
                            "[Contexto: $appAtual] Usuário disse: $textoFalado"
                        } else {
                            textoFalado
                        }
                        pedirRespostaQwenComBusca(promptComContexto, ehProativo = false)
                    }
                }
                override fun onError(error: Int) {
                    Handler(Looper.getMainLooper()).post {
                        micButton.text = "🎙️ Falar"
                        speechBubble.text = "Erro no microfone. Tente novamente."
                    }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Handler(Looper.getMainLooper()).post {
                        micButton.text = "⏳ Pensando..."
                        speechBubble.text = "Processando áudio..."
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun iniciarEscutaVoz() {
        tts?.stop()
        Handler(Looper.getMainLooper()).post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com a Capri...")
                }
                speechRecognizer?.startListening(intent)
                micButton.text = "🔴 Ouvindo..."
                speechBubble.text = "Escutando..."
            } catch (e: Exception) {
                e.printStackTrace()
                micButton.text = "🎙️ Falar"
                speechBubble.text = "Erro ao iniciar microfone."
            }
        }
    }

    private fun carregarImagemComFundoExternoTransparente(linkUrl: String) {
        Thread {
            try {
                val url = URL(linkUrl)
                val bmpOriginal = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                if (bmpOriginal != null) {
                    val bmp = bmpOriginal.copy(Bitmap.Config.ARGB_8888, true)
                    val width = bmp.width
                    val height = bmp.height
                    val visited = Array(width) { BooleanArray(height) }
                    val queue: Queue<Pair<Int, Int>> = LinkedList()

                    for (x in 0 until width) {
                        verificarEAdicionarFila(bmp, x, 0, visited, queue)
                        verificarEAdicionarFila(bmp, x, height - 1, visited, queue)
                    }
                    for (y in 0 until height) {
                        verificarEAdicionarFila(bmp, 0, y, visited, queue)
                        verificarEAdicionarFila(bmp, width - 1, y, visited, queue)
                    }

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.poll()!!
                        bmp.setPixel(cx, cy, Color.TRANSPARENT)

                        val vizinhos = listOf(Pair(cx + 1, cy), Pair(cx - 1, cy), Pair(cx, cy + 1), Pair(cx, cy - 1))
                        for ((nx, ny) in vizinhos) {
                            if (nx in 0 until width && ny in 0 until height && !visited[nx][ny]) {
                                val pixel = bmp.getPixel(nx, ny)
                                if (Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) {
                                    visited[nx][ny] = true
                                    queue.add(Pair(nx, ny))
                                }
                            }
                        }
                    }

                    Handler(Looper.getMainLooper()).post {
                        spriteView.setImageBitmap(bmp)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun verificarEAdicionarFila(bmp: Bitmap, x: Int, y: Int, visited: Array<BooleanArray>, queue: Queue<Pair<Int, Int>>) {
        if (!visited[x][y]) {
            visited[x][y] = true
            val pixel = bmp.getPixel(x, y)
            if (Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) {
                queue.add(Pair(x, y))
            }
        }
    }

    private fun pesquisarNoTavily(query: String): String {
        return try {
            val url = URL(TAVILY_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("api_key", TAVILY_API_KEY)
                put("query", query)
                put("search_depth", "basic")
                put("max_results", 2)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val jsonRes = JSONObject(response)
                val results = jsonRes.optJSONArray("results")
                val sb = StringBuilder()
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        sb.append("- ").append(item.optString("content")).append("\n")
                    }
                }
                sb.toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun pedirRespostaQwenComBusca(mensagemUsuario: String, ehProativo: Boolean) {
        if (ehProativo && tts?.isSpeaking == true) return

        Handler(Looper.getMainLooper()).post {
            speechBubble.text = "Pensando..."
        }

        Thread {
            val precisaDeBusca = !ehProativo && (mensagemUsuario.length > 5 && !mensagemUsuario.contains("[Contexto"))
            val dadosWeb = if (precisaDeBusca) pesquisarNoTavily(mensagemUsuario) else ""

            try {
                val url = URL(GROQ_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val calendar = Calendar.getInstance()
                val contextoTemporal = "[DATA: ${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}]."
                val injecaoWeb = if (dadosWeb.isNotBlank()) "\n[WEB]:\n$dadosWeb" else ""

                val messagesArray = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "$systemPromptCapri\n$contextoTemporal$injecaoWeb"))
                    
                    synchronized(historicoConversa) {
                        val recentes = historicoConversa.takeLast(6)
                        for ((role, content) in recentes) {
                            put(JSONObject().put("role", role).put("content", content))
                        }
                    }
                    put(JSONObject().put("role", "user").put("content", mensagemUsuario))
                }

                val jsonBody = JSONObject().apply {
                    put("model", "qwen/qwen3.8-27b")
                    put("messages", messagesArray)
                    put("max_tokens", 220)
                    put("temperature", 0.75)
                    put("reasoning_effort", "none")
                }

                conn.outputStream.use { os ->
                    os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val conteudoResposta = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()

                    synchronized(historicoConversa) {
                        historicoConversa.add("user" to mensagemUsuario)
                        historicoConversa.add("assistant" to conteudoResposta)
                        while (historicoConversa.size > 15) {
                            historicoConversa.removeAt(0)
                        }
                    }
                    salvarHistoricoLocal()

                    Handler(Looper.getMainLooper()).post {
                        falar(conteudoResposta)
                    }
                } else if (!ehProativo) {
                    Handler(Looper.getMainLooper()).post {
                        falar("Erro na conexão com a Groq.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!ehProativo) {
                    Handler(Looper.getMainLooper()).post {
                        falar("Sem sinal na matrix.")
                    }
                }
            }
        }.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                val saudacao = if (historicoConversa.isNotEmpty()) {
                    "Na escuta, choom."
                } else {
                    "Engrama ativo. Pronto para conectar."
                }
                pedirRespostaQwenComBusca(saudacao, ehProativo = true)
            }
        }
    }

    private fun falar(texto: String) {
        speechBubble.text = texto
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        instancia = null
        handlerEspontaneo.removeCallbacks(tarefaConversaEspontanea)
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
        }
    }
}
