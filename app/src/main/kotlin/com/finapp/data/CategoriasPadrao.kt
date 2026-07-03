package com.finapp.data

import com.finapp.data.db.entities.Categoria
import com.finapp.data.db.entities.Perfil
import com.finapp.data.db.entities.TipoTransacao

/**
 * Categorias criadas automaticamente na primeira vez que um perfil é usado.
 */
object CategoriasPadrao {

    private val GASTOS = listOf(
        "Alimentação" to "#F59E0B",
        "Transporte" to "#3B82F6",
        "Moradia" to "#8B5CF6",
        "Saúde" to "#EF4444",
        "Lazer" to "#EC4899",
        "Educação" to "#06B6D4",
        "Outros" to "#6B7280"
    )

    private val GANHOS = listOf(
        "Salário" to "#10B981",
        "Freelance" to "#84CC16",
        "Investimentos" to "#14B8A6",
        "Outros" to "#6B7280"
    )

    fun para(perfil: Perfil): List<Categoria> {
        val gastos = GASTOS.map { (nome, cor) ->
            Categoria(nome = nome, tipo = TipoTransacao.GASTO, cor = cor, perfil = perfil)
        }
        val ganhos = GANHOS.map { (nome, cor) ->
            Categoria(nome = nome, tipo = TipoTransacao.GANHO, cor = cor, perfil = perfil)
        }
        return gastos + ganhos
    }
}
