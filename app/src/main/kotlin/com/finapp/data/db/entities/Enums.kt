package com.finapp.data.db.entities

// ATENÇÃO: os nomes destes enums são persistidos no banco e usados em
// literais SQL (ex: 'GANHO' no saldo). Nunca renomear valores existentes.

/** Tipo de movimentação financeira. Room persiste enums pelo nome. */
enum class TipoTransacao {
    GANHO,
    GASTO
}

/** Perfis de uso do app. Cada perfil tem dados isolados. */
enum class Perfil(val rotulo: String, val descricao: String) {
    PESSOA_FISICA("Pessoa Física", "Controle pessoal"),
    MEI("MEI", "Pessoal + Negócio"),
    CNPJ("CNPJ", "Empresa"),

    /** Sub-contextos de dados do MEI (abas Pessoal/Negócio) — não selecionáveis. */
    MEI_PESSOAL("MEI · Pessoal", "Finanças pessoais do MEI"),
    MEI_NEGOCIO("MEI · Negócio", "Finanças do negócio do MEI");

    companion object {
        /** Perfis que o usuário pode escolher na seleção. */
        val PRINCIPAIS = listOf(PESSOA_FISICA, MEI, CNPJ)

        /** Baldes que de fato guardam dados (MEI se divide em Pessoal/Negócio). */
        val BALDES_DADOS = listOf(PESSOA_FISICA, MEI_PESSOAL, MEI_NEGOCIO, CNPJ)
    }
}

/** Aba ativa dentro do perfil MEI. */
enum class ContextoMei(val rotulo: String) {
    PESSOAL("Pessoal"),
    NEGOCIO("Negócio")
}

/** Frequência de lançamento de uma transação recorrente. */
enum class Frequencia {
    DIARIA,
    SEMANAL,
    MENSAL,
    ANUAL
}
