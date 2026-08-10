package com.finapp.data.db.entities

// ATENÇÃO: os nomes destes enums são persistidos no banco e usados em
// literais SQL (ex: 'GANHO' no saldo). Nunca renomear valores existentes.

/** Tipo de movimentação financeira. Room persiste enums pelo nome. */
enum class TipoTransacao {
    GANHO,
    GASTO
}

/** Perfis (baldes de dados) do app. Cada perfil tem dados isolados. */
enum class Perfil(val rotulo: String, val descricao: String) {
    PESSOA_FISICA("Pessoal", "Só os gastos pessoais"),
    MEI("Pessoal + Empresa", "Separados em abas"),
    CNPJ("Empresa", "Só as finanças da empresa"),

    /** Sub-contextos de dados do modo Pessoal + Empresa — não selecionáveis. */
    MEI_PESSOAL("Pessoal", "Lado pessoal do modo misto"),
    MEI_NEGOCIO("Empresa", "Lado empresa do modo misto"),

    /** Carteira compartilhada da Casa — sincronizada entre os membros. */
    CASA("Casa", "Compartilhada entre os membros"),

    /**
     * Espelho local, somente leitura, dos lançamentos PESSOAIS dos outros
     * membros da casa (visão "Membros"). Alimentado pelo sync; nunca é um
     * contexto ativo nem entra em backup.
     */
    CASA_MEMBROS("Membros", "Lançamentos pessoais dos membros da casa");

    companion object {
        /** Modos de uso oferecidos na seleção inicial e nas Configurações. */
        val PRINCIPAIS = listOf(PESSOA_FISICA, MEI, CNPJ)

        /** Baldes próprios do usuário (MEI se divide em Pessoal/Negócio). */
        val BALDES_DADOS = listOf(PESSOA_FISICA, MEI_PESSOAL, MEI_NEGOCIO, CNPJ, CASA)

        /** Baldes pessoais — os únicos espelhados na visão Membros da casa. */
        val BALDES_PESSOAIS = listOf(PESSOA_FISICA, MEI_PESSOAL)

        /**
         * Tudo que o usuário pode VER, inclusive o espelho dos membros. Base
         * da aba Cartões, que mistura os contextos de propósito: a fatura do
         * cartão é uma só, venha o gasto de onde vier.
         */
        val BALDES_TODOS = BALDES_DADOS + CASA_MEMBROS
    }
}

/**
 * De quem é um lançamento: da casa (padrão) ou de uma pessoa específica.
 *
 * Numa casa, TODOS vão para o balde [Perfil.CASA] — inclusive os de uma
 * pessoa. É isso que deixa a atribuição ser mútua: se o lançamento "da
 * namorada" ficasse no balde privado de quem digitou, ela nunca o veria, e
 * ela não teria como atribuir um gasto a você. O preço é explícito: numa
 * casa, gasto atribuído a alguém é visível para os dois.
 *
 * Fora de uma casa não há escolha — tudo cai no balde privado.
 */
sealed interface Dono {
    data object Casa : Dono
    data class Pessoa(val uid: String, val nome: String) : Dono
}

/**
 * Igualdade por IDENTIDADE (uid), ignorando o nome. O nome de exibição pode
 * variar entre o que ficou gravado na transação e o que a casa publica —
 * comparar por `==` deixaria o chip certo sem marcar.
 */
fun Dono.mesmoQue(outro: Dono): Boolean = when {
    this is Dono.Casa && outro is Dono.Casa -> true
    this is Dono.Pessoa && outro is Dono.Pessoa -> uid == outro.uid
    else -> false
}

/**
 * Filtro "de quem" da lista unificada. [Pessoa] cobre tanto os lançamentos
 * atribuídos à pessoa na casa quanto os privados dela (os seus, do balde
 * pessoal; os dela, do espelho [Perfil.CASA_MEMBROS]) — para quem filtra,
 * "gastos da fulana" é uma coisa só, não três.
 */
sealed interface FiltroDono {
    data object Tudo : FiltroDono
    data object Casa : FiltroDono
    data class Pessoa(val uid: String, val nome: String) : FiltroDono
}

/** Balde que guarda dados da empresa: habilita nota fiscal e o tema empresa. */
val Perfil.ehEmpresa: Boolean
    get() = this == Perfil.CNPJ || this == Perfil.MEI_NEGOCIO

/**
 * Modo de uso escolhido pelo usuário (onboarding/Configurações).
 * Cada modo mapeia para um [Perfil] existente — os dados nunca mudam de balde.
 */
enum class ModoUso(val rotulo: String, val descricao: String, val perfil: Perfil) {
    SO_PESSOAL("Só pessoal", "Controlar meus gastos do dia a dia", Perfil.PESSOA_FISICA),
    SO_EMPRESA("Só empresa", "Controlar as finanças da minha empresa", Perfil.CNPJ),
    PESSOAL_E_EMPRESA(
        "Pessoal + Empresa",
        "Os dois, com contas separadas em abas",
        Perfil.MEI
    );

    /** True quando o modo inclui um contexto de empresa. */
    val temEmpresa: Boolean get() = this != SO_PESSOAL

    companion object {
        fun doPerfil(perfil: Perfil): ModoUso? = entries.firstOrNull { it.perfil == perfil }
    }
}

/** Tipo da empresa (informativo — rótulo e futuros recursos fiscais). */
enum class TipoEmpresa(val rotulo: String, val descricao: String) {
    MEI("MEI", "Microempreendedor Individual"),
    CNPJ("CNPJ", "ME, LTDA ou outra empresa")
}


/** Frequência de lançamento de uma transação recorrente. */
enum class Frequencia {
    DIARIA,
    SEMANAL,
    MENSAL,
    ANUAL
}
