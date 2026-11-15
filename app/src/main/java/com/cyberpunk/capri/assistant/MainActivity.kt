package com.cyberpunk.capri.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    
    // Cliente HTTP para falar com o Colab
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val PREFS_NAME = "CapriVoicePrefs"
        private const val KEY_API_URL = "gradio_api_url"
        // Link inicial do Colab fornecido
        private const val DEFAULT_URL = "https://0c1fbc51cca37cb12b.gradio.live"

        fun obterUrl(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_API_URL, DEFAULT_URL) ?: DEFAULT_URL
        }

        fun salvarUrl(context: Context, novaUrl: String) {
            val urlLimpa = novaUrl.trim().removeSuffix("/")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_API_URL, urlLimpa).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(48, 64, 48, 64)
        }

        val badge = TextView(this).apply {
            text = "ARASAKA SOULKILLER ENGRAM // v3.8"
            setTextColor(Color.parseColor("#FF2A6D"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        root.addView(badge)

        val iconeAppView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(220, 220).apply {
                setMargins(0, 16, 0, 16)
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(iconeAppView)

        Thread {
            try {
                val url = java.net.URL("https://instasize.com/api/image/138d45b9f00a27db3ee8248b78c22cbc43d8abbe0441da3d684f9373a33ac667.png")
                val bmp = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                Handler(Looper.getMainLooper()).post {
                    if (bmp != null) iconeAppView.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        val title = TextView(this).apply {
            text = "CAPRICORN"
            setTextColor(Color.parseColor("#05D9E8"))
            textSize = 28f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
            
            // 💡 SEGREDO: Mantém o dedo em cima do título "CAPRICORN" para abrir a janela de trocar o link do Colab a qualquer momento!
            setOnLongClickListener {
                abrirJanelaConfigurarLink()
                true
            }
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Rede neural amalgamada pronta. (Segure em 'CAPRICORN' para ajustar a URL do Colab)."
            setTextColor(Color.parseColor("#D1F7FF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(subtitle)

        val btnOverlay = criarBotaoCyber("1. PERMITIR SOBREPOSIÇÃO DE TELA", "#1A1A2E", "#05D9E8") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Sobreposição já ativada, choom!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnOverlay)

        val btnMic = criarBotaoCyber("2. PERMITIR MICROFONE (VOZ)", "#1A1A2E", "#05D9E8") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            } else {
                Toast.makeText(this, "Microfone já liberado!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnMic)

        val btnAcc = criarBotaoCyber("3. ATIVAR OLHOS NA TELA (ACESSIBILIDADE)", "#1A1A2E", "#05D9E8") {
            Toast.makeText(this, "Ative 'Capricorn' na lista de Acessibilidade", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(btnAcc)

        val btnStart = criarBotaoCyber("⚡ CONECTAR CAPRICORN NA TELA", "#FF2A6D", "#FFFFFF") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Ative a Permissão 1 (Sobreposição) primeiro, choom!", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, CapriOverlayService::class.java))
                Toast.makeText(this, "Capricorn conectada à rede!", Toast.LENGTH_SHORT).show()
                
                // Exemplo prático: Testa a voz da Capri assim que conecta!
                testarVozCapri("Olá, choom! Sistema da Capri online e conectado com sucesso.")
            }
        }
        root.addView(btnStart)

        val btnStop = criarBotaoCyber("DESCONECTAR CAPRICORN", "#111118", "#888899") {
            stopService(Intent(this, CapriOverlayService::class.java))
            Toast.makeText(this, "Capricorn desconectada.", Toast.LENGTH_SHORT).show()
        }
        root.addView(btnStop)

        setContentView(root)
    }

    // Janela flutuante para trocar o link do Colab direto no telemóvel sem mexer no GitHub
    private fun abrirJanelaConfigurarLink() {
        val inputUrl = EditText(this).apply {
            hint = "https://xxxx.gradio.live"
            setText(obterUrl(this@MainActivity))
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Configurar Link da Capri (Colab)")
            .setMessage("Cole abaixo a nova URL do Gradio gerada no Colab:")
            .setView(inputUrl)
            .setPositiveButton("Salvar") { _, _ ->
                val novoLink = inputUrl.text.toString()
                if (novoLink.startsWith("http")) {
                    salvarUrl(this, novoLink)
                    Toast.makeText(this, "Link atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "URL inválida! Precisa começar com https://", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Função pública para enviar texto para a API do Applio e reproduzir a voz da Capri
    fun testarVozCapri(texto: String) {
        val baseUrl = obterUrl(this)
        val endpointApi = "$baseUrl/api/predict"

        val dataArray = JSONArray().apply {
            put(texto)
            put("pt-BR-FranciscaNeural")
            put(0)
            put("Capri_10e_30s.pth")
            put("Capri.index")
            put(0.75)
            put("rmvpe")
        }

        val jsonBody = JSONObject().apply {
            put("data", dataArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(endpointApi).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.LENGTH_LONG.let {
                        Toast.makeText(this@MainActivity, "Erro de conexão com o Colab. Verifique o link!", it).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@MainActivity, "Link expirado (${response.code}). Segure em 'CAPRICORN' para atualizar!", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                try {
                    val respostaString = response.body?.string() ?: return
                    val jsonResposta = JSONObject(respostaString)
                    val dataRetorno = jsonResposta.getJSONArray("data")
                    val primeiroItem = dataRetorno.get(0)
                    val caminhoAudioRemoto = if (primeiroItem is JSONObject) {
                        primeiroItem.getString("name")
                    } else {
                        primeiroItem.toString()
                    }

                    val urlDownloadAudio = "$baseUrl/file=$caminhoAudioRemoto"
                    descarregarETocarAudio(urlDownloadAudio)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun descarregarETocarAudio(urlAudio: String) {
        val requestAudio = Request.Builder().url(urlAudio).build()
        client.newCall(requestAudio).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val bytesAudio = response.body?.bytes() ?: return
                try {
                    val arquivoTemp = File(cacheDir, "fala_capri.wav")
                    FileOutputStream(arquivoTemp).use { it.write(bytesAudio) }

                    Handler(Looper.getMainLooper()).post {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(arquivoTemp.absolutePath)
                            prepare()
                            start()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun criarBotaoCyber(texto: String, corFundo: String, corTexto: String, acao: () -> Unit): Button {
        return Button(this).apply {
            text = texto
            setBackgroundColor(Color.parseColor(corFundo))
            setTextColor(Color.parseColor(corTexto))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(24, 32, 24, 32)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 14, 0, 14)
            }
            layoutParams = params
            setOnClickListener { acao() }
        }
    }
}
