# Arquitetura

## Visão geral

MVVM + Repository, reativo de ponta a ponta: a UI coleta `Flow`s do Room e **nunca recarrega dados manualmente** — qualquer escrita no banco atualiza todas as telas sozinha.

```
UI (Compose) ── collectAsState ──> ViewModel (StateFlow)
                                        │ flatMapLatest(baldes do contexto)
                                        ▼
                                  FinanceRepository  ◄── única porta de escrita
                                        │
                                        ▼
                                  Room (DAOs + Flow) ◄──► SyncManager ◄──► Firestore
```

## Camadas

| Pacote | Responsabilidade |
|---|---|
| `ui/screen` | Home, Análise, Ônibus, Config + seleção de perfil |
| `ui/component` | Componentes reutilizáveis: modal de transação, linha do histórico, grupos de cartão, cards, gráficos em Canvas |
| `viewmodel` | 1 por aba + `CasaViewModel`, `MembrosViewModel` e `PerfilViewModel`; expõem `StateFlow` e um `SharedFlow<String>` de mensagens (viram snackbar/toast) |
| `data/repository` | `FinanceRepository` — única porta de acesso aos dados; carimba `atualizadoEm` em toda escrita |
| `data/db` | Room: 7 entidades, DAOs, migrações. Agregações (saldo, somas) são feitas **no SQL** |
| `data/io` | Export CSV/JSON/PDF, parser de importação (puro, testável), backups, notas fiscais, backup no Google Drive |
| `data/notif` | Gatilhos financeiros (`NotificacaoManager`, `Vencimentos`) + `NotificacaoWorker` (3 rodadas por dia) |
| `data/sync` | `CasaManager` (login/casa) e `SyncManager` (motor de sincronização) |
| `utils` | Formatação pt-BR, filtros de período, aparência, cartões, mescla de baldes, `fluxoDataAtual()` |

## Contextos e baldes (`PerfilManager`)

Conceito central do app. O perfil escolhido define o layout; a coluna `perfil` de cada linha define **em qual balde o dado vive** (um único banco, não um por perfil).

- **`perfilAtivo`** — o que o usuário escolheu (Pessoa Física / MEI / CNPJ). Controla o layout do dashboard e quantas abas existem.
- **`perfilDados`** — o balde **privado** do contexto ativo (`MEI_PESSOAL` / `MEI_NEGOCIO` conforme a aba). É o destino de escrita padrão e a âncora das telas mono-balde (Config, backup, export).
- **`baldesFinanceiros`** — o que entra em **saldo, somas e pendências**: o balde privado + `CASA`.
- **`baldesVisiveis`** — o que as **listas** leem: `baldesFinanceiros` + o espelho `CASA_MEMBROS`. A diferença é justamente o espelho: o gasto pessoal de outro membro aparece na lista (para você saber o que rolou) mas **nunca entra no seu saldo**.

**A Casa não é uma aba.** Pessoal e Casa são uma visão só; o que separa é o *dono* de cada lançamento. As abas são no máximo `Pessoal | Empresa`.

`utils/Fluxos.kt` (`mesclarListas`/`somarBaldes`) une os baldes — e curto-circuita a coleção vazia de propósito: `combine` de uma lista vazia nunca emite e travaria a UI no valor inicial.

## Dono do lançamento

`Dono` = **de quem é** o gasto (`Dono.Casa` ou `Dono.Pessoa(uid)`), gravado em `Transacao.pessoaUid`/`pessoaNome`. Não confundir com `criadoPor*`, que é **quem digitou** — o ponto da feature é poder lançar o gasto do outro.

Numa casa, **tudo grava em `Perfil.CASA`**, inclusive o atribuído a uma pessoa: no balde privado de quem digitou, o outro nunca veria a linha e a atribuição não seria mútua. Consequência explícita: numa casa, gasto atribuído a alguém é visível para os dois. Fora de uma casa não há escolha (balde privado, `pessoaUid` vazio).

Comparação de `Dono`/`FiltroDono` é sempre **por uid** (`mesmoQue`), nunca por `==`: o nome gravado na transação pode divergir do publicado pela casa.

## Modelo de dados (banco v17)

Entidades: `Transacao`, `Categoria`, `ConfiguracaoPerfil`, `TransacaoRecorrente`, `Cartao`, `Meta`, `ContaAgendada`.

Campos de sincronização (em todas as entidades sincronizadas):

| Campo | Papel |
|---|---|
| `uuid` | Identidade global (índice único) — ids autoincrement colidem entre aparelhos |
| `atualizadoEm` | Epoch millis da última modificação — "última edição vence" nos conflitos |
| `deletado` | **Tombstone**: deletar é marcar, nunca apagar — a deleção se propaga no sync |
| `criadoPor` / `criadoPorUid` | (Transacao) quem lançou — exibido na Casa e usado no guard de edição |
| `pessoaUid` / `pessoaNome` | (Transacao) de quem é o lançamento, quando atribuído a alguém da casa |

Regras que o código inteiro segue:
- **Deletar = `deletado = true`** via repository (nem `deletarTodasTransacoes` usa `@Delete` — senão o sync ressuscita a linha; só o espelho local `CASA_MEMBROS` apaga físico)
- **Toda escrita passa pelo repository**, que carimba `atualizadoEm` — exceto o `SyncManager`, que escreve direto nos DAOs para **preservar** o carimbo remoto
- **Toda leitura filtra `deletado = 0`**
- Desfazer uma deleção = limpar o tombstone (não re-inserir)
- Na Casa, **só o autor edita/apaga** (`podeSerEditadaPor`, também garantido nas regras do Firestore)

## Pendências, recorrências e cartões

- **`Transacao.pago = false` é uma pendência** (compra no crédito até pagar a fatura, ocorrência de gasto recorrente, parcela futura): não conta em `observarSaldoTotal` até ser marcada como paga. A Home mostra "A pagar" e "A receber" separados, e **Atrasado** — estado derivado, não coluna: pendência de GASTO com data no passado.
- **Recorrência mensal materializa 12 meses à frente** (`Recorrencias.kt`, puro e testado): cada ocorrência nasce `pago = false` no dia do vencimento, com **uuid determinístico** (reprocessar nunca duplica; o tombstone bloqueia ressurreição) e `recorrenciaUuid` ligando à regra. O dia desejado fica em `diaMensal` — mês curto lança no último dia e **volta** ao dia pedido no mês seguinte (nunca reagendar com `plusMonths` encadeado, que trunca 31→28 para sempre).
- **Encerrar uma recorrência** (`encerrarRecorrenteComOcorrencias`) desativa a regra e tombstona **toda** ocorrência ainda não paga, inclusive as atrasadas; o que já foi pago fica como histórico.
- **Cartão é global**, não pertence a um contexto: o que separa é o gasto, pelo balde da transação. Cartões pessoais são **espelhados na Casa** (`Cartao.origemUuid`, uuid determinístico), one-way original→espelho e read-only do lado da Casa. `utils/Cartoes.kt` centraliza a canonicalização — agrupar sem canonicalizar quebra a fatura em dois grupos.

## Decisões de design

- **Dinheiro em centavos (`Long`)** — `R$ 12,34` = `1234`. Elimina erro de ponto flutuante em somas. Exibição sempre via `Formatadores.moeda(Long)`. Nos gráficos, converter com `toDouble()` antes de dividir (divisão inteira!).
- **Categorias nunca são deletadas** — são *arquivadas* (somem de novos lançamentos, preservam o histórico). Renomear propaga o novo nome para transações e recorrências (a referência é por nome).
- **Gráficos em Canvas puro** — sem biblioteca externa (MPAndroidChart etc.): APK menor, dark mode e animações nativas do Compose.
- **Salário fixo é materializado como recorrência** — configurar salário cria/atualiza uma `TransacaoRecorrente` mensal; recorrências vencidas são lançadas ao abrir o app (recupera dias em que o app ficou fechado).
- **Datas** — `LocalDate` convertido para epoch day (`Long`) no SQLite, o que faz `BETWEEN` funcionar direto. Telas que dependem de "hoje" usam `fluxoDataAtual()` (re-emite à meia-noite) para não mostrarem o mês velho na virada.
- **Ônibus desconta sozinho** nos dias de rotina (`calcularDescontosOnibus`, pura e testada), com cursor de último dia processado — idempotente por design, nunca fica negativo.
- **Storage Access Framework** para export/import — zero permissões de armazenamento em qualquer versão do Android.
