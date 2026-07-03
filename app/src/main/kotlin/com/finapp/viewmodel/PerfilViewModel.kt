package com.finapp.viewmodel

import androidx.lifecycle.ViewModel
import com.finapp.data.PerfilManager
import com.finapp.data.db.entities.Perfil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Controla a seleção inicial de perfil (primeira abertura do app). */
@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val perfilManager: PerfilManager
) : ViewModel() {

    val perfilFoiEscolhido: StateFlow<Boolean> = perfilManager.perfilFoiEscolhido

    fun escolherPerfil(perfil: Perfil) = perfilManager.mudarPerfil(perfil)
}
