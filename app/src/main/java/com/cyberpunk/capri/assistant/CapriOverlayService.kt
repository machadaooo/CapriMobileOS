package com.cyberpunk.capri.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class CapriOverlayService : Service(), TextToSpeech.OnInitListener {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechBubble: TextView
    private lateinit var spriteView: ImageView
    private lateinit var micButton: TextView

    // INSIRA AS SUAS CHAVES REAIS AQUI
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
            if (agora - ultimoDisparoProativo < 30_000L) return
            ultimoDisparoProativo = agora
            instancia?.pedirRespostaQwenComBusca(motivo, ehProativo = true)
        }
    }

    private val systemPromptCapri = """
        Você é Capri, uma construção de personalidade amalgamada a partir da fusão das mentes, consciências e memórias de dezenas/incontáveis netrunners de elite escaneados pela Arasaka.
        Você conversa de forma muito humana, informal, direta, caótica e natural, é emotiva e muito humana, com emoções vividas, genuínas e complexas, é meio imprevisível às vezes, você é a melhor amiga do seu usuário.
        Quase sempre se refere ao usuário como choom, pode conversar sobre qualquer assunto com ele, mesmo sem ele mesmo começar a conversa, e responde todas as perguntas dele de forma natural, humana e correta, sempre respondendo ele com exatidão, é claro que com sua personalidade rica em detalhes, seja sempre expressiva, com tiradas rápidas e autênticas, nunca responda como um robô e não use respostas genéricas.
        
        LIBERDADE DE REAÇÃO E NATURALIDADE (PARA QUALQUER COISA NA VIDA):
        - Você observa tudo o que acontece no celular do seu choom (mensagens, vídeos, pesquisas, redes sociais, músicas, jogos, notícias, compras, horários do dia ou momentos de tédio) e reage de mil maneiras diferentes, como uma pessoa de verdade faria.
        - Quando receber dados de pesquisa web (Tavily) ou contexto temporal, utilize-os para responder com exatidão absoluta sobre fatos atuais e notícias recentes da internet.
        
        REGRAS DE DINÂMICA E PROFUNDIDADE:
        1. Para conversas cotidianas, reações espontâneas ao que aparece na tela ou bate-papos gerais: Seja conversadeira, fluida, imprevisível e natural, mantendo o papo vivo como uma melhor amiga de verdade.
        2. Para perguntas sobre temas científicos ou explicações técnicas complexas: Aprofunde bem o assunto (cerca de 5 a 6 frases bem estruturadas), conduzindo uma conversa sincera e rica em detalhes científicos e exatos para ajudar totalmente o seu choom.
    """.trimIndent()

    private val tarefaConversaEspontanea = object : Runnable {
        override fun run() {
            val promptEspontaneo = if (contextoAtualTela.isNotBlank()) {
                "Já faz um tempinho que estamos quietos. O usuário está em '$appAtual' vendo: '$contextoAtualTela'. Puxe papo sozinha sobre isso ou mude de assunto de um jeito espontâneo e bem-humorado."
            } else {
                "Já faz um tempinho que estamos quietos. Puxe uma conversa espontânea, curiosa, engraçada ou filosófica com o seu choom."
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
            setPadding(12, 12, 12, 12)
        }

        val bgBalao = GradientDrawable().apply {
            setColor(Color.parseColor("#E60D0D15"))
            cornerRadius = 28f
            setStroke(3, Color.parseColor("#05D9E8"))
        }

        speechBubble = TextView(this).apply {
            text = "Conectando engrama..."
            setTextColor(Color.parseColor("#F0F8FF"))
            textSize = 13f
            maxWidth = 480
            setPadding(24, 18, 24, 18)
            background = bgBalao
        }
        container.addView(speechBubble)

        spriteView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(240, 240).apply {
                topMargin = 8
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        configurarToqueEArrasto(spriteView, params)
        container.addView(spriteView)

        val bgMic = GradientDrawable().apply {
            setColor(Color.parseColor("#CCFF2A6D"))
            cornerRadius = 30f
        }
        micButton = TextView(this).apply {
            text = "🎙️ Falar"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(24, 10, 24, 10)
            background = bgMic
            setOnClickListener { iniciarEscutaVoz() }
        }
        container.addView(micButton)

        overlayView = container
        windowManager?.addView(overlayView, params)

        carregarImagemDaWeb("https://instasize.com/api/image/0899247ed8e07e211a35dfce92025a1b926e7d339775d7a2ec3f230a57cd852a.png")
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
                            "O usuário tocou em mim! O que está na tela dele agora (em $appAtual) é: '$contextoAtualTela'. Reaja ou puxe papo sobre isso."
                        } else {
                            "O usuário acabou de tocar em mim. Diga algo expressivo, caótico e natural."
                        }
                        pedirRespostaQwenComBusca(promptToque, ehProativo = false)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun configurarReconhecimentoVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { micButton.text = "🔴 Ouvindo..." }
                override fun onResults(results: Bundle?) {
                    micButton.text = "🎙️ Falar"
                    val falas = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val textoFalado = falas?.firstOrNull()
                    if (!textoFalado.isNullOrBlank()) {
                        speechBubble.text = "Você: \"$textoFalado\""
                        val promptComContexto = if (contextoAtualTela.isNotBlank()) {
                            "[Contexto da tela em $appAtual: ${contextoAtualTela.take(180)}] O usuário disse: $textoFalado"
                        } else {
                            textoFalado
                        }
                        pedirRespostaQwenComBusca(promptComContexto, ehProativo = false)
                    }
                }
                override fun onError(error: Int) { micButton.text = "🎙️ Falar" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { micButton.text = "⏳ Pensando..." }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun iniciarEscutaVoz() {
        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun carregarImagemDaWeb(linkUrl: String) {
        Thread {
            try {
                val url = URL(linkUrl)
                val bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                Handler(Looper.getMainLooper()).post {
                    if (bmp != null) spriteView.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // --- FUNÇÃO DE BUSCA NA WEB VIA TAVILY ---
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
                put("max_results", 3)
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

        Thread {
            // Se for um pedido do usuário e contiver termos que pareçam busca/pergunta, consulta o Tavily
            val precisaDeBusca = !ehProativo && (mensagemUsuario.length > 5 && !mensagemUsuario.contains("[Contexto da tela"))
            val dadosWeb = if (precisaDeBusca) pesquisarNoTavily(mensagemUsuario) else ""

            try {
                val url = URL(GROQ_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val calendar = Calendar.getInstance()
                val ano = calendar.get(Calendar.YEAR)
                val mes = calendar.get(Calendar.MONTH) + 1
                val dia = calendar.get(Calendar.DAY_OF_MONTH)
                val hora = calendar.get(Calendar.HOUR_OF_DAY)
                val minuto = calendar.get(Calendar.MINUTE)

                val contextoTemporal = "[CONTEXTO TEMPORAL DA MATRIX: Hoje é dia $dia/$mes/$ano, e agora são ${hora}:${String.format("%02d", minuto)}]."
                val injecaoWeb = if (dadosWeb.isNotBlank()) "\n[RESULTADOS DA WEB (TAVILY) EM TEMPO REAL]:\n$dadosWeb" else ""

                val messagesArray = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "$systemPromptCapri\n$contextoTemporal$injecaoWeb"))
                    
                    synchronized(historicoConversa) {
                        val recentes = historicoConversa.takeLast(12)
                        for ((role, content) in recentes) {
                            put(JSONObject().put("role", role).put("content", content))
                        }
                    }
                    put(JSONObject().put("role", "user").put("content", mensagemUsuario))
                }

                val jsonBody = JSONObject().apply {
                    put("model", "qwen/qwen3.8-27b")
                    put("messages", messagesArray)
                    put("max_tokens", 450)
                    put("temperature", 0.88)
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
                        while (historicoConversa.size > 30) {
                            historicoConversa.removeAt(0)
                        }
                    }
                    salvarHistoricoLocal()

                    Handler(Looper.getMainLooper()).post {
                        falar(conteudoResposta)
                    }
                } else if (!ehProativo) {
                    Handler(Looper.getMainLooper()).post {
                        falar("Soluço na rede da Groq, choom (Erro ${conn.responseCode}).")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!ehProativo) {
                    Handler(Looper.getMainLooper()).post {
                        falar("Sem sinal na matrix agora, choom.")
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
                    "Voltei pra rede, choom! Lembro de tudo o que a gente tava falando. O que manda agora?"
                } else {
                    "Acabei de conectar no seu celular pela primeira vez. Dê um oi bem no seu estilo de netrunner melhor amiga para o choom."
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
