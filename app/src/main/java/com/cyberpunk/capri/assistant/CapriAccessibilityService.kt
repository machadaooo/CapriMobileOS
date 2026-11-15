package com.cyberpunk.capri.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

class CapriAccessibilityService : AccessibilityService() {

    private var ultimoAppAberto: String = ""
    private var ultimoResumoObservado: String = ""
    private var ultimoComentarioMs: Long = 0L
    private var inicioSessaoAtualMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pacote = event.packageName?.toString() ?: return
        if (pacote == packageName || pacote == "com.android.systemui") return

        val rootNode: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val textoBuilder = StringBuilder()
        extrairTextos(rootNode, textoBuilder)
        rootNode.recycle()

        val textoTela = textoBuilder.toString().trim()
        val nomeApp = traduzirNomeApp(pacote)
        val agora = System.currentTimeMillis()
        val horaAtual = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (textoTela.isNotBlank()) {
            CapriOverlayService.contextoAtualTela = textoTela.take(450)
        }
        CapriOverlayService.appAtual = nomeApp

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pacote != ultimoAppAberto) {
            ultimoAppAberto = pacote
            inicioSessaoAtualMs = agora
            ultimoComentarioMs = agora

            val trechoTela = if (textoTela.isNotBlank()) "Na tela aparece: '${textoTela.take(250)}'." else ""
            CapriOverlayService.dispararEventoProativo(
                "[CONTEXTO ATUAL] São ${horaAtual}h. O usuário acabou de abrir '$nomeApp'. $trechoTela Reaja de forma espontânea, humana e variada ao que ele está fazendo ou vendo."
            )
            return
        }

        val resumoAtual = textoTela.take(180)
        val conteudoMudouBastante = resumoAtual.length > 20 && resumoAtual != ultimoResumoObservado

        if (conteudoMudouBastante && (agora - ultimoComentarioMs > 75_000L)) {
            ultimoResumoObservado = resumoAtual
            ultimoComentarioMs = agora
            val minutosNoApp = ((agora - inicioSessaoAtualMs) / 60_000L).toInt()
            val pistaTempo = if (minutosNoApp >= 25) "Ele já está nesse app há uns $minutosNoApp minutos." else ""

            CapriOverlayService.dispararEventoProativo(
                "[CONTEXTO ATUAL] São ${horaAtual}h. O usuário está em '$nomeApp'. $pistaTempo O que aparece na tela agora é: '${textoTela.take(320)}'. Reaja livremente a qualquer detalhe interessante, engraçado, curioso ou aleatório disso."
            )
        }
    }

    private fun extrairTextos(node: AccessibilityNodeInfo, builder: StringBuilder) {
        if (builder.length > 500) return
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
