package com.cyberpunk.capri.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

class CapriAccessibilityService : AccessibilityService() {

    private var ultimoAppAberto: String = ""
    private var ultimoResumoObservado: String = ""
    private var ultimoDisparoContextoMs: Long = 0L
    private var inicioSessaoAtualMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pacote = event.packageName?.toString() ?: return
        if (pacote == packageName || pacote == "com.android.systemui") return

        val agora = System.currentTimeMillis()

        // COOLDOWN DE 2 MINUTOS (120.000 ms): Silêncio obrigatório entre falas automáticas
        if (agora - ultimoDisparoContextoMs < 120_000L) return

        val rootNode: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val textoBuilder = StringBuilder()
        extrairTextos(rootNode, textoBuilder)
        rootNode.recycle()

        val textoTela = textoBuilder.toString().trim()
        val nomeApp = traduzirNomeApp(pacote)
        val horaAtual = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (textoTela.isNotBlank()) {
            CapriOverlayService.contextoAtualTela = textoTela.take(400)
        }
        CapriOverlayService.appAtual = nomeApp

        // 1. Mudança de App
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pacote != ultimoAppAberto) {
            ultimoAppAberto = pacote
            inicioSessaoAtualMs = agora
            ultimoDisparoContextoMs = agora

            val trechoTela = if (textoTela.isNotBlank()) "Na tela aparece: '${textoTela.take(200)}'." else ""
            CapriOverlayService.dispararEventoProativo(
                "[CONTEXTO] São ${horaAtual}h. O usuário abriu '$nomeApp'. $trechoTela Faça apenas uma tirada curta e direta, se achar necessário."
            )
            return
        }

        // 2. Mudança substancial de conteúdo dentro do mesmo app
        val resumoAtual = textoTela.take(150)
        val conteudoMudouBastante = resumoAtual.length > 30 && resumoAtual != ultimoResumoObservado

        if (conteudoMudouBastante) {
            ultimoResumoObservado = resumoAtual
            ultimoDisparoContextoMs = agora

            CapriOverlayService.dispararEventoProativo(
                "[CONTEXTO] O usuário está em '$nomeApp'. O que aparece agora é: '${textoTela.take(250)}'. Comente de forma ultra curta e direta."
            )
        }
    }

    private fun extrairTextos(node: AccessibilityNodeInfo, builder: StringBuilder) {
        if (builder.length > 400) return
        if (node.text != null && node.text.isNotBlank()) {
            builder.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extrairTextos(child, builder)
                child.recycle()
            }
        }
    }

    private fun traduzirNomeApp(pacote: String): String {
        return when {
            pacote.contains("whatsapp") -> "WhatsApp"
            pacote.contains("instagram") -> "Instagram"
            pacote.contains("youtube") -> "YouTube"
            pacote.contains("chrome") -> "Google Chrome"
            pacote.contains("tiktok") || pacote.contains("musically") -> "TikTok"
            pacote.contains("discord") -> "Discord"
            pacote.contains("spotify") -> "Spotify"
            pacote.contains("telegram") -> "Telegram"
            pacote.contains("twitter") || pacote.contains("x.android") -> "X (Twitter)"
            else -> pacote.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }
    }

    override fun onInterrupt() {}
}
