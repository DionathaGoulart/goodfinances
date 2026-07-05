package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao
import com.finapp.data.db.entities.Transacao
import com.finapp.data.repository.FinanceRepository
import com.finapp.data.sync.CasaManager
import com.finapp.data.sync.SyncManager
import com.finapp.utils.fluxoDataAtual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Resumo do mês de um membro da casa. Valores em centavos. */
data class ResumoMembro(
    val nome: String,
    val ganhos: Long,
    val gastos: Long
) {
    val saldo: Long get() = ganhos - gastos
}

/**
 * Visão "Membros" da Casa: os lançamentos PESSOAIS de cada membro que
 * ativou o compartilhamento — os meus (baldes pessoais locais) + os dos
 * outros (espelho CASA_MEMBROS, alimentado pelo sync). Somente leitura.
 */
@HiltViewModel
class MembrosViewModel @Inject constructor(
    repository: FinanceRepository,
    perfilManager: PerfilManager,
    casaManager: CasaManager,
    syncManager: SyncManager
) : ViewModel() {

    /** Se EU estou compartilhando (mostra aviso quando não). */
    val compartilhando: StateFlow<Boolean> = syncManager.compartilharCasaAtivado

    private val _filtroMembro = MutableStateFlow<String?>(null)
    val filtroMembro: StateFlow<String?> = _filtroMembro.asStateFlow()

    private val dataAtual = fluxoDataAtual()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /** Meus lançamentos pessoais, com meu nome — só se eu compartilho. */
    private val meus = combine(
        repository.observarTransacoes(Perfil.PESSOA_FISICA),
        repository.observarTransacoes(Perfil.MEI_PESSOAL),
        casaManager.usuario,
        syncManager.compartilharCasaAtivado
    ) { pf, meiPessoal, usuario, compartilhando ->
        if (!compartilhando) {
            emptyList()
        } else {
            val nome = usuario?.nome?.substringBefore(' ')?.ifBlank { null } ?: "Você"
            (pf + meiPessoal).map { it.copy(criadoPor = "$nome (você)") }
        }
    }

    /** Lançamentos do mês corrente de todos que compartilham. */
    val transacoesMes: StateFlow<List<Transacao>> = combine(
        meus,
        repository.observarTransacoes(Perfil.CASA_MEMBROS),
        dataAtual,
        _filtroMembro
    ) { minhas, dosOutros, hoje, filtro ->
        val mes = YearMonth.from(hoje)
        (minhas + dosOutros)
            .filter { YearMonth.from(it.data) == mes }
            .filter { filtro == null || it.criadoPor == filtro }
            .sortedWith(compareByDescending<Transacao> { it.data }.thenByDescending { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nomes para os chips de filtro (todos que têm lançamento no mês). */
    val membros: StateFlow<List<String>> = combine(
        meus,
        repository.observarTransacoes(Perfil.CASA_MEMBROS),
        dataAtual
    ) { minhas, dosOutros, hoje ->
        val mes = YearMonth.from(hoje)
        (minhas + dosOutros)
            .filter { YearMonth.from(it.data) == mes }
            .map { it.criadoPor.ifBlank { "Sem nome" } }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Resumo do mês por membro (independe do filtro ativo). */
    val resumos: StateFlow<List<ResumoMembro>> = combine(
        meus,
        repository.observarTransacoes(Perfil.CASA_MEMBROS),
        dataAtual
    ) { minhas, dosOutros, hoje ->
        val mes = YearMonth.from(hoje)
        (minhas + dosOutros)
            .filter { YearMonth.from(it.data) == mes }
            .groupBy { it.criadoPor.ifBlank { "Sem nome" } }
            .map { (nome, lista) ->
                ResumoMembro(
                    nome = nome,
                    ganhos = lista.filter { it.tipo == TipoTransacao.GANHO }.sumOf { it.valor },
                    gastos = lista.filter { it.tipo == TipoTransacao.GASTO }.sumOf { it.valor }
                )
            }
            .sortedBy { it.nome }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null limpa o filtro. */
    fun filtrarMembro(nome: String?) {
        _filtroMembro.value = nome
    }
}
