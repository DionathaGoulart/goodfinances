# Arquitetura

## Visão geral

MVVM + Repository, reativo de ponta a ponta: a UI coleta `Flow`s do Room e **nunca recarrega dados manualmente** — qualquer escrita no banco atualiza todas as telas sozinha.

```
UI (Compose) ── collectAsState ──> ViewModel (StateFlow)
                                        │ flatMapLatest(perfilDados)
                                        ▼
                                  FinanceRepository  ◄── única porta de escrita
                                        │
                                        ▼
                                  Room (DAOs + Flow) ◄──► SyncManager ◄──► Firestore
```

## Camadas

| Pacote | Responsabilidade |
|---|---|
| `ui/screen` | 4 telas (Home, Análise, Transações, Config) + seleção de perfil |
| `ui/component` | Componentes reutilizáveis: modal de transação, item com swipe, cards, gráficos em Canvas |
| `viewmodel` | 1 por tela + `CasaViewModel` e `PerfilViewModel`; expõem `StateFlow` e um `SharedFlow<String>` de mensagens (viram snackbar/toast) |
| `data/repository` | `FinanceRepository` — única porta de acesso aos dados; carimba `atualizadoEm` em toda escrita |
| `data/db` | Room: entidades, DAOs, migrações. Agregações (saldo, somas) são feitas **no SQL** |
| `data/io` | Export CSV/JSON/PDF, parser de importação (puro, testável), backups |
| `data/sync` | `CasaManager` (login/casa) e `SyncManager` (motor de sincronização) |
| `utils` | Formatação pt-BR, filtros de período, aparência, `fluxoDataAtual()` |

## Sistema de perfis

Conceito central do app. `PerfilManager` guarda em SharedPreferences e expõe dois StateFlows:

- **`perfilAtivo`** — o que o usuário escolheu (Pessoa Física / MEI / CNPJ / Casa). Controla o layout do dashboard.
- **`perfilDados`** — o "balde" efetivo das queries. Para o MEI, vira `MEI_PESSOAL` ou `MEI_NEGOCIO` conforme a aba ativa; para os demais, é o próprio perfil.

Todo dado no banco é particionado pela coluna `perfil` (um único banco, não um por perfil). Os ViewModels fazem `perfilDados.flatMapLatest { query }`, então **trocar de perfil/aba re-executa todas as queries automaticamente**.

## Modelo de dados (banco v4)

Entidades: `Transacao`, `Categoria`, `ConfiguracaoPerfil`, `TransacaoRecorrente`.

Campos de sincronização em transações/categorias/recorrências:

| Campo | Papel |
|---|---|
| `uuid` | Identidade global (índice único) — ids autoincrement colidem entre aparelhos |
| `atualizadoEm` | Epoch millis da última modificação — "última edição vence" nos conflitos |
| `deletado` | **Tombstone**: deletar é marcar, nunca apagar — a deleção se propaga no sync |
| `criadoPor` | (só Transacao) nome de quem lançou, exibido no perfil Casa |

Regras que o código inteiro segue:
- **Deletar = `deletado = true`** via repository (o `@Delete` direto não é usado, exceto na limpeza local total)
- **Toda escrita passa pelo repository**, que carimba `atualizadoEm` — exceto o `SyncManager`, que escreve direto nos DAOs para **preservar** o carimbo remoto
- **Toda leitura filtra `deletado = 0`**
- Desfazer uma deleção = limpar o tombstone (não re-inserir)

## Decisões de design

- **Dinheiro em centavos (`Long`)** — `R$ 12,34` = `1234`. Elimina erro de ponto flutuante em somas. Exibição sempre via `Formatadores.moeda(Long)`. Nos gráficos, converter com `toDouble()` antes de dividir (divisão inteira!).
- **Categorias nunca são deletadas** — são *arquivadas* (somem de novos lançamentos, preservam o histórico). Renomear propaga o novo nome para transações e recorrências (a referência é por nome).
- **Gráficos em Canvas puro** — sem biblioteca externa (MPAndroidChart etc.): APK menor, dark mode e animações nativas do Compose.
- **Salário fixo é materializado como recorrência** — configurar salário cria/atualiza uma `TransacaoRecorrente` mensal; recorrências vencidas são lançadas ao abrir o app (recupera dias em que o app ficou fechado).
- **Datas** — `LocalDate` convertido para epoch day (`Long`) no SQLite, o que faz `BETWEEN` funcionar direto. Telas que dependem de "hoje" usam `fluxoDataAtual()` (re-emite à meia-noite) para não mostrarem o mês velho na virada.
- **Storage Access Framework** para export/import — zero permissões de armazenamento em qualquer versão do Android.
