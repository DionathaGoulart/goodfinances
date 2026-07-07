# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

GoodFinances (nome interno FinanApp/`com.finapp` — o applicationId nunca muda, senão o app instalado não atualiza) — app Android de controle financeiro pessoal (Kotlin + Jetpack Compose, Material 3, dark mode sempre ativo). Idioma do código e da UI: **português brasileiro** (nomes de classes/funções/propriedades em pt-BR, ex: `TransacaoViewModel.adicionarTransacao`).

## Build

Não há Java nem Gradle no PATH desta máquina — use o JBR do Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug          # APK debug
.\gradlew.bat testDebugUnitTest      # testes unitários (JUnit, src/test)
.\gradlew.bat assembleRelease        # APK release assinado (R8)
```

- APK debug: `app\build\outputs\apk\debug\app-debug.apk`
- SDK em `local.properties` (compileSdk 36, minSdk 26)
- Versões centralizadas em `gradle/libs.versions.toml` (AGP 8.9.2, Kotlin 2.1.0, Room 2.7.1, Hilt 2.53.1, **KSP** — não usar kapt)
- Release assinado via `key.properties` + `finapp-release.jks` na raiz (**gitignorados** — se perdê-los não dá para atualizar o app instalado; mantenha backup)

## Arquitetura

MVVM + Repository, tudo reativo via Flow do Room (a UI nunca recarrega dados manualmente):

- `data/db/` — Room (versão **11**): 7 entidades (`Transacao`, `Categoria`, `ConfiguracaoPerfil`, `TransacaoRecorrente`, `Cartao`, `Meta`, `ContaAgendada`) + DAOs. Agregações (saldo, somas por categoria/período) são feitas **no SQL**, não em memória. Enums persistidos por nome; `LocalDate` como epoch day (`Converters`). `Meta` = objetivo de economia (aporte manual); `ContaAgendada` = conta a pagar/receber com estado pendente → `pagarConta` cria a `Transacao` e marca `pago`.
- `data/repository/FinanceRepository.kt` — único ponto de acesso a dados; contém seed de categorias padrão, lançamento de recorrências vencidas e importação com dedup (data+valor+categoria).
- `data/PerfilManager.kt` — **conceito central**: perfil ativo em SharedPreferences exposto como StateFlow. `perfilAtivo` = escolha do usuário (PESSOA_FISICA/MEI/CNPJ, define layout); `perfilDados` = balde efetivo das queries — para MEI vira `MEI_PESSOAL`/`MEI_NEGOCIO` conforme a aba do dashboard. Todo dado no banco é particionado pela coluna `perfil` (um único banco, não um por perfil). ViewModels fazem `perfilDados.flatMapLatest { query }`, então trocar perfil/aba atualiza todas as telas automaticamente.
- `data/sync/CasaManager.kt` — login Google (Credential Manager + Firebase Auth) e a "Casa" (carteira compartilhada): documento em `casas/{id}` no Firestore com `codigoConvite` de 6 chars e lista `membros`. O perfil `Perfil.CASA` é o balde local dos dados compartilhados; só aparece no seletor quando o usuário está numa casa. Requer `app/google-services.json` (gitignorado) — se o recurso `default_web_client_id` não existir em runtime, o provedor Google não foi habilitado no console e o json precisa ser baixado de novo.
- `data/sync/SyncManager.kt` — motor do sync (iniciado no `FinanApplication`): sincroniza `transacoes`, `categorias`, `cartoes`, `metas` e `contas` (todas coletivas na Casa; `metas`/`contas` seguem o padrão dos cartões — sem guard de autor, qualquer membro edita). Atualizar `firestore-rules.txt` ao adicionar coleção. PULL via snapshot listeners em `casas/{id}/transacoes|categorias/{uuid}` aplicando docs remotos quando o `atualizadoEm` remoto é maior (empate mantém o local, evitando eco); PUSH reativo com debounce que sobe linhas com `atualizadoEm > marca` (prefs, por casa) — fire-and-forget porque a fila offline do Firestore é durável. **Regras**: escritas do pull vão direto nos DAOs preservando o `atualizadoEm` remoto (nunca pelo repository, que re-carimba); categorias padrão da Casa são semeadas SÓ pelo criador (`semearCategoriasCasa`) — `garantirCategoriasPadrao` ignora CASA de propósito.
- `data/notif/` — `NotificacaoManager` avalia gatilhos financeiros (DAS, orçamento 80/100%, limite MEI, recorrência do dia, contas vencendo, inatividade 3 dias) e posta notificações locais, deduplicadas por período em prefs; `NotificacaoWorker` roda 1x/dia via **WorkManager** e obtém o manager por Hilt `EntryPoint` (sem HiltWorkerFactory). Canais criados no `FinanApplication`; permissão `POST_NOTIFICATIONS` pedida na `MainActivity` (fail-open). Toggle em Configurações › Notificações.
- `data/io/` — Export (CSV/JSON/PDF com `PdfDocument` nativo), `ParserImportacao` (classe pura, sem Android — é ela que os testes unitários cobrem; `ImportManager` só resolve a Uri) e Backup semanal (todos os perfis com dados, 4 por perfil, em `getExternalFilesDir` + doc em `usuarios/{uid}/backups` no Firestore; restore filtra pelo perfil ativo). `NotaFiscalManager` guarda notas/comprovantes em `filesDir/notas/` (qualquer contexto; imagens viram PDF na entrada); `DriveBackupManager` faz backup desses arquivos no `appDataFolder` do Google Drive da conta logada (escopo `drive.appdata` via AuthorizationClient — sem Firebase Storage/billing). Usa Storage Access Framework — **sem permissões de storage; não adicionar `WRITE_EXTERNAL_STORAGE`**. Arquivos externos (CSV/JSON) trafegam valores em **reais decimais**; a conversão para centavos acontece no parser.
- `viewmodel/` — 1 por aba + `PerfilViewModel` (seleção inicial + dicas de onboarding) + `PlanejamentoViewModel` (metas e contas da aba Metas). Erros de escrita viram mensagens num `SharedFlow<String>` coletado pela UI (snackbar/toast).
- `ui/screen/` + `ui/component/` — `TransacaoModal` (bottom sheet criar/editar com máscara de centavos BR) e `TransacaoItemDismissivel` (swipe-delete + undo) são reutilizados por Home e Transações. Bottom nav tem **5 abas**: Home, Análise, **Metas** (`PlanejamentoScreen` — metas + contas a pagar), Transações, Config. Gráficos da Análise são Canvas do Compose puro — **não adicionar lib de gráficos**.
- Tema: `FinanAppTheme(escalaFonte, corPrimaria)` dinâmico via `AparenciaManager`; dark mode fixo.

## Convenções

- **Dinheiro é `Long` em CENTAVOS** em todo o app (entidades, ViewModels, UI). Exibição sempre via `utils/Formatadores.moeda(Long)` (pt-BR). Cuidado com divisão inteira ao calcular proporções em gráficos (`toDouble()` primeiro).
- **Modelo preparado para sync entre aparelhos** (banco v3): toda entidade tem `uuid` (identidade global, índice único), `atualizadoEm` (epoch millis, "última edição vence") e `deletado` (tombstone). Regras: **deletar = marcar `deletado = true`** via repository (nunca `@Delete` direto — até `deletarTodasTransacoes` tombstona, senão o sync/espelho ressuscita ou não propaga a limpeza; só o espelho local `CASA_MEMBROS` apaga físico); na Casa, **só o autor (`criadoPor`) edita/apaga** — guard nos ViewModels via `podeSerEditadaPor`; **toda escrita passa pelo repository**, que carimba `atualizadoEm`; **queries de leitura filtram `deletado = 0`**; undo = `restaurarTransacao` (limpa o tombstone, não re-insere); transferências entre contextos compartilham `transferenciaId` — deletar/restaurar uma perna afeta a outra (via repository). Import deduplica por uuid além da tripla data+valor+categoria.
- Categorias nunca são deletadas: arquivar preserva histórico; renomear usa `FinanceRepository.renomearCategoria`, que propaga o novo nome para transações e recorrências (categoria é referenciada por nome, não por id).
- Novos valores no enum `Perfil` não exigem migração (coluna TEXT), mas qualquer mudança nas entidades exige bump de versão + migração no `AppDatabase` (ver `MIGRACAO_1_2` como modelo).
- Filtros de período usam `utils/PeriodoFiltro` (semana começa na segunda). Telas que dependem de "hoje" combinam com `utils/fluxoDataAtual()` (re-emite à meia-noite) — nunca capturar `LocalDate.now()` uma vez só num Flow de longa duração.
- Salário fixo (Configurações) é materializado como `TransacaoRecorrente` mensal com descrição "Salário fixo" — o `ConfigViewModel` mantém as duas coisas em sincronia.
