package com.finapp.data.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.finapp.MainActivity
import com.finapp.R
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Frequencia
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoEmpresa
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.repository.FinanceRepository
import com.finapp.utils.Formatadores
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Avaliação dos eventos financeiros e disparo de notificações locais.
 * Roda no [NotificacaoWorker] (WorkManager) três vezes por dia (manhã, tarde
 * e noite); não depende de o app estar aberto. Toda notificação é deduplicada
 * por período em prefs para não repetir (ex: orçamento estourado avisa uma
 * vez no mês; vencimentos repetem por faixa do dia, de propósito).
 *
 * Gatilhos:
 *  - Vencimentos: gasto pendente/fatura de cartão avisa TODO DIA (3x) a
 *    partir de 5 dias antes e enquanto estiver atrasado.
 *  - DAS (empresa): lembrete dias 18/19/20.
 *  - Orçamento de categoria: avisa em 80% e em 100% do teto do mês.
 *  - Limite do MEI: avisa quando o faturamento do ano passa de 80% de R$ 81 mil.
 *  - Recorrente de GANHO (salário): heads-up no dia em que entra.
 *  - Inatividade: lembrete gentil após 3 dias sem lançar nada.
 */
@Singleton
class NotificacaoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FinanceRepository,
    private val perfilManager: PerfilManager
) {
    private val prefs = context.getSharedPreferences("finapp_prefs", Context.MODE_PRIVATE)

    private val _ativado = MutableStateFlow(prefs.getBoolean(CHAVE_ATIVO, true))
    /** Preferência do usuário (Configurações › Notificações). */
    val ativado: StateFlow<Boolean> = _ativado.asStateFlow()

    fun alternar(ativo: Boolean) {
        prefs.edit { putBoolean(CHAVE_ATIVO, ativo) }
        _ativado.value = ativo
    }

    /** Cria os canais de notificação (idempotente). */
    fun garantirCanais() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerenciador = context.getSystemService(NotificationManager::class.java) ?: return
        gerenciador.createNotificationChannel(
            NotificationChannel(
                CANAL_ALERTAS,
                "Alertas financeiros",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Orçamentos, limite do MEI e vencimento do DAS" }
        )
        gerenciador.createNotificationChannel(
            NotificationChannel(
                CANAL_LEMBRETES,
                "Lembretes",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Recorrências do dia e lembretes de registro" }
        )
    }

    /**
     * Avalia todos os gatilhos e posta as notificações pertinentes.
     * Idempotente por dia/mês graças às marcas em prefs. Nunca lança —
     * falha de leitura de um balde não derruba os demais avisos.
     */
    suspend fun avaliar(hoje: LocalDate = LocalDate.now()) {
        if (!_ativado.value || !temPermissao()) return
        garantirCanais()

        val baldes = baldesDoModo()
        val mesTag = "${hoje.year}-${hoje.monthValue}"

        limparChavesAntigas(hoje, mesTag)
        runCatching { avaliarVencimentos(baldes, hoje) }
        runCatching { avaliarRecorrentes(baldes, hoje) }
        runCatching { avaliarContas(baldes, hoje) }
        runCatching { avaliarOrcamentos(baldes, hoje, mesTag) }
        runCatching { avaliarDas(hoje, mesTag) }
        runCatching { avaliarLimiteMei(hoje, mesTag) }
        runCatching { avaliarInatividade(baldes, hoje) }
    }

    // ---------- Gatilhos ----------

    /**
     * Lembretes de vencimento dos GASTOS pendentes (gasto frequente, fatura
     * de cartão, parcela): avisa TODO DIA a partir de [DIAS_AVISO_VENCIMENTO]
     * dias antes e continua avisando enquanto estiver atrasado — uma vez por
     * faixa do dia (manhã/tarde/noite), acompanhando as rodadas do worker.
     * Compras no crédito viram um aviso único por fatura (cartão + vencimento).
     */
    private suspend fun avaliarVencimentos(baldes: List<Perfil>, hoje: LocalDate) {
        val periodo = PeriodoDia.deHora(java.time.LocalTime.now().hour).rotulo
        val limite = hoje.plusDays(DIAS_AVISO_VENCIMENTO)
        baldes.forEach { balde ->
            val pendentes = repository.listarGastosPendentesAte(balde, limite)
            if (pendentes.isEmpty()) return@forEach
            val (noCartao, avulsos) = pendentes.partition { it.cartaoUuid.isNotBlank() }

            val cartoes = repository.listarCartoes(balde)
            noCartao.groupBy { it.cartaoUuid to it.data }.forEach { (chave, itens) ->
                val (uuid, vencimento) = chave
                if (!marcarSeNovo("notif_venc_fat_${uuid}_${vencimento}_${hoje}_$periodo")) {
                    return@forEach
                }
                val nome = cartoes.firstOrNull { it.uuid == uuid }?.nome ?: "Cartão"
                val dias = ChronoUnit.DAYS.between(hoje, vencimento)
                postar(
                    id = ID_VENCIMENTO + (uuid + vencimento).hashCode(),
                    canal = CANAL_ALERTAS,
                    titulo = if (dias < 0) "Fatura ATRASADA" else "Fatura do cartão",
                    texto = "Fatura do $nome ${mensagemPrazo(dias)} — " +
                        Formatadores.moeda(itens.sumOf { it.valor })
                )
            }

            avulsos.forEach { pendente ->
                if (!marcarSeNovo("notif_venc_${pendente.uuid}_${hoje}_$periodo")) {
                    return@forEach
                }
                val dias = ChronoUnit.DAYS.between(hoje, pendente.data)
                val nome = pendente.descricao.ifBlank { pendente.categoria }
                postar(
                    id = ID_VENCIMENTO + pendente.uuid.hashCode(),
                    canal = CANAL_ALERTAS,
                    titulo = if (dias < 0) "Conta ATRASADA" else "Conta a pagar",
                    texto = "$nome ${mensagemPrazo(dias)} — " +
                        Formatadores.moeda(pendente.valor)
                )
            }
        }
    }

    /**
     * Recorrências de GANHO (salário, entradas) que entram hoje. Os GASTOS
     * recorrentes ficam de fora: a ocorrência pendente deles já é coberta
     * pelos lembretes de vencimento ([avaliarVencimentos]).
     */
    private suspend fun avaliarRecorrentes(baldes: List<Perfil>, hoje: LocalDate) {
        baldes.forEach { balde ->
            repository.listarRecorrentesAtivas(balde)
                .filter { it.tipo == TipoTransacao.GANHO }
                .filter { rec ->
                    // Recorrência mensal é materializada meses à frente (o
                    // cursor proximoLancamento fica longe): o lembrete sai
                    // no dia do vencimento/recebimento, pelo diaMensal
                    val duraAteVencido = rec.terminaEm != null && hoje > rec.terminaEm
                    when {
                        duraAteVencido -> false
                        rec.frequencia == Frequencia.MENSAL && rec.diaMensal in 1..31 ->
                            hoje.dayOfMonth == rec.diaMensal.coerceAtMost(hoje.lengthOfMonth())
                        else -> rec.proximoLancamento == hoje
                    }
                }
                .forEach { rec ->
                    val chave = "notif_rec_${rec.uuid}_$hoje"
                    if (marcarSeNovo(chave)) {
                        val titulo = if (rec.tipo == TipoTransacao.GANHO) {
                            "Entrada de hoje"
                        } else {
                            "Conta que vence hoje"
                        }
                        val nome = rec.descricao.ifBlank { rec.categoria }
                        postar(
                            id = ID_RECORRENTE + rec.uuid.hashCode(),
                            canal = CANAL_LEMBRETES,
                            titulo = titulo,
                            texto = "$nome — ${Formatadores.moeda(rec.valor)}"
                        )
                    }
                }
        }
    }

    /** Contas agendadas vencidas, vencendo hoje ou amanhã (re-avisa por dia até baixar). */
    private suspend fun avaliarContas(baldes: List<Perfil>, hoje: LocalDate) {
        val limite = hoje.plusDays(1)
        baldes.forEach { balde ->
            repository.listarContasPendentesAte(balde, limite).forEach { conta ->
                if (!marcarSeNovo("notif_conta_${conta.uuid}_$hoje")) return@forEach
                val dias = ChronoUnit.DAYS.between(hoje, conta.vencimento)
                val quando = when {
                    dias < 0 -> "venceu"
                    dias == 0L -> "vence hoje"
                    else -> "vence amanhã"
                }
                val titulo = if (conta.tipo == TipoTransacao.GASTO) {
                    "Conta a pagar"
                } else {
                    "Valor a receber"
                }
                postar(
                    id = ID_CONTA + conta.uuid.hashCode(),
                    canal = CANAL_ALERTAS,
                    titulo = titulo,
                    texto = "${conta.descricao} $quando — ${Formatadores.moeda(conta.valor)}"
                )
            }
        }
    }

    /** Orçamentos por categoria: avisa em 80% e 100% do teto do mês. */
    private suspend fun avaliarOrcamentos(baldes: List<Perfil>, hoje: LocalDate, mesTag: String) {
        val mes = YearMonth.from(hoje)
        val inicio = mes.atDay(1)
        val fim = mes.atEndOfMonth()
        baldes.forEach { balde ->
            val comOrcamento = repository.listarCategorias(balde).filter {
                it.tipo == TipoTransacao.GASTO && it.orcamentoMensal > 0 && !it.arquivada && !it.deletado
            }
            if (comOrcamento.isEmpty()) return@forEach

            val gastoPorCategoria = repository.listarTransacoes(balde)
                .filter {
                    it.tipo == TipoTransacao.GASTO &&
                        it.categoria != FinanceRepository.NOME_TRANSFERENCIA &&
                        !it.data.isBefore(inicio) && !it.data.isAfter(fim)
                }
                .groupBy { it.categoria }
                .mapValues { (_, lista) -> lista.sumOf { it.valor } }

            comOrcamento.forEach { cat ->
                val gasto = gastoPorCategoria[cat.nome] ?: 0L
                val fracao = gasto.toDouble() / cat.orcamentoMensal
                when {
                    fracao >= 1.0 && marcarSeNovo("notif_orc100_${cat.uuid}_$mesTag") -> postar(
                        id = ID_ORCAMENTO + cat.uuid.hashCode(),
                        canal = CANAL_ALERTAS,
                        titulo = "Orçamento estourado: ${cat.nome}",
                        texto = "Você já gastou ${Formatadores.moeda(gasto)} de " +
                            Formatadores.moeda(cat.orcamentoMensal)
                    )
                    fracao >= 0.8 && fracao < 1.0 &&
                        marcarSeNovo("notif_orc80_${cat.uuid}_$mesTag") -> postar(
                        id = ID_ORCAMENTO + cat.uuid.hashCode(),
                        canal = CANAL_ALERTAS,
                        titulo = "Atenção ao orçamento: ${cat.nome}",
                        texto = "${(fracao * 100).roundToInt()}% do teto usado " +
                            "(${Formatadores.moeda(gasto)} de ${Formatadores.moeda(cat.orcamentoMensal)})"
                    )
                }
            }
        }
    }

    /** Vencimento do DAS: dias 18/19/20, uma vez por mês. */
    private fun avaliarDas(hoje: LocalDate, mesTag: String) {
        val tipoEmpresa = perfilManager.tipoEmpresa.value ?: return
        if (baldeEmpresa() == null) return
        if (hoje.dayOfMonth !in 18..20) return
        if (!marcarSeNovo("notif_das_$mesTag")) return
        val guia = if (tipoEmpresa == TipoEmpresa.MEI) "DAS-MEI" else "DAS (Simples Nacional)"
        val texto = if (hoje.dayOfMonth == 20) {
            "$guia vence HOJE (dia 20)"
        } else {
            "$guia vence dia 20 — faltam ${20 - hoje.dayOfMonth} dias"
        }
        postar(ID_DAS, CANAL_ALERTAS, "Imposto do mês", texto)
    }

    /** Limite anual do MEI (R$ 81.000): avisa a partir de 80% do teto. */
    private suspend fun avaliarLimiteMei(hoje: LocalDate, mesTag: String) {
        if (perfilManager.tipoEmpresa.value != TipoEmpresa.MEI) return
        val balde = baldeEmpresa() ?: return
        val faturamento = repository.somarPorTipo(
            balde, TipoTransacao.GANHO, hoje.withDayOfYear(1), hoje.withDayOfYear(hoje.lengthOfYear())
        )
        val fracao = faturamento.toDouble() / LIMITE_MEI_CENTAVOS
        if (fracao < 0.8) return
        // Dedup POR NÍVEL: quem já recebeu o aviso de 80% ainda precisa
        // receber o de 100% se estourar no mesmo mês
        val nivel = if (fracao >= 1.0) "100" else "80"
        if (!marcarSeNovo("notif_mei_${mesTag}_$nivel")) return
        val texto = if (fracao >= 1.0) {
            "Limite estourado — procure seu contador sobre o desenquadramento"
        } else {
            "${(fracao * 100).roundToInt()}% do limite anual do MEI " +
                "(${Formatadores.moeda(faturamento)} de ${Formatadores.moeda(LIMITE_MEI_CENTAVOS)})"
        }
        postar(ID_MEI, CANAL_ALERTAS, "Faturamento do MEI", texto)
    }

    /** Lembrete gentil após 3 dias sem nenhum lançamento (no máx. 1 a cada 3 dias). */
    private suspend fun avaliarInatividade(baldes: List<Perfil>, hoje: LocalDate) {
        val ultima = baldes
            .flatMap { repository.listarTransacoes(it) }
            // Datas futuras (parcela, fatura, recorrência adiantada) não são
            // atividade do usuário — sem o corte, uma parcela em dezembro
            // suprimiria o lembrete o ano inteiro
            .map { it.data }
            .filter { !it.isAfter(hoje) }
            .maxOrNull() ?: return // sem dados nenhum: nada a lembrar
        val diasParado = ChronoUnit.DAYS.between(ultima, hoje)
        if (diasParado < 3) return
        val ultimoAviso = prefs.getLong(CHAVE_INATIVO, 0L)
        if (System.currentTimeMillis() - ultimoAviso < TRES_DIAS_MILLIS) return
        prefs.edit { putLong(CHAVE_INATIVO, System.currentTimeMillis()) }
        postar(
            id = ID_INATIVO,
            canal = CANAL_LEMBRETES,
            titulo = "Que tal registrar seus gastos?",
            texto = "Faz $diasParado dias sem um lançamento. Toque para adicionar."
        )
    }

    // ---------- Infra ----------

    /** Baldes do modo de uso atual (a Casa fica de fora para não gerar ruído). */
    private fun baldesDoModo(): List<Perfil> = when (perfilManager.perfilAtivo.value) {
        Perfil.MEI -> listOf(Perfil.MEI_PESSOAL, Perfil.MEI_NEGOCIO)
        Perfil.CNPJ -> listOf(Perfil.CNPJ)
        else -> listOf(Perfil.PESSOA_FISICA)
    }

    /** Balde de empresa do modo atual (null quando o modo é só pessoal). */
    private fun baldeEmpresa(): Perfil? = when (perfilManager.perfilAtivo.value) {
        Perfil.MEI -> Perfil.MEI_NEGOCIO
        Perfil.CNPJ -> Perfil.CNPJ
        else -> null
    }

    /** True e marca a chave se ainda não foi usada — dedup de notificação. */
    private fun marcarSeNovo(chave: String): Boolean {
        if (prefs.getBoolean(chave, false)) return false
        prefs.edit { putBoolean(chave, true) }
        return true
    }

    /**
     * Remove as chaves de dedup de dias/meses passados — uma por
     * recorrência/conta por dia, cresceriam para sempre nas prefs.
     */
    private fun limparChavesAntigas(hoje: LocalDate, mesTag: String) {
        val sufixoDia = "_$hoje"
        val sufixoMes = "_$mesTag"
        val vencidas = prefs.all.keys.filter { chave ->
            when {
                chave == CHAVE_INATIVO -> false // timestamp, não é dedup
                chave.startsWith("notif_rec_") || chave.startsWith("notif_conta_") ->
                    !chave.endsWith(sufixoDia)
                // Vencimentos carregam dia + faixa: "..._2026-07-14_manha"
                chave.startsWith("notif_venc") -> !chave.contains(sufixoDia)
                chave.startsWith("notif_orc") || chave.startsWith("notif_das_") ->
                    !chave.contains(sufixoMes)
                chave.startsWith("notif_mei_") -> !chave.contains(sufixoMes)
                else -> false
            }
        }
        if (vencidas.isNotEmpty()) {
            prefs.edit { vencidas.forEach { remove(it) } }
        }
    }

    private fun temPermissao(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun postar(id: Int, canal: String, titulo: String, texto: String) {
        if (!temPermissao()) return
        val abrir = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, id, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notificacao) }
    }

    private companion object {
        const val CHAVE_ATIVO = "notificacoes_ativas"
        const val CHAVE_INATIVO = "notif_inativo_ultimo"

        const val CANAL_ALERTAS = "financas_alertas"
        const val CANAL_LEMBRETES = "financas_lembretes"

        // Bases de id (per-item soma o hash do uuid para não colidir)
        const val ID_RECORRENTE = 1_000
        const val ID_ORCAMENTO = 2_000
        const val ID_CONTA = 3_000
        const val ID_VENCIMENTO = 4_000
        const val ID_DAS = 10
        const val ID_MEI = 11
        const val ID_INATIVO = 12

        /** Limite anual de faturamento do MEI, em centavos (R$ 81.000,00). */
        const val LIMITE_MEI_CENTAVOS = 8_100_000L
        const val TRES_DIAS_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
