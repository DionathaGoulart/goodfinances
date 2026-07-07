package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.ModoUso
import com.finapp.data.db.entities.TipoEmpresa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Controla a seleção inicial do modo de uso (primeira abertura do app). */
@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val perfilManager: PerfilManager
) : ViewModel() {

    val perfilFoiEscolhido: StateFlow<Boolean> = perfilManager.perfilFoiEscolhido

    /** False até o usuário fechar as dicas iniciais (mostradas uma vez). */
    val dicasVistas: StateFlow<Boolean> = perfilManager.dicasVistas

    fun dispensarDicas() = perfilManager.marcarDicasVistas()

    /** Conclui o onboarding: modo de uso + tipo da empresa (quando houver). */
    fun escolherModo(modo: ModoUso, tipoEmpresa: TipoEmpresa?) {
        tipoEmpresa?.let(perfilManager::definirTipoEmpresa)
        perfilManager.mudarModo(modo)
    }
}
