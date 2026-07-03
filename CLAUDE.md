# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

FinanApp — app Android de controle financeiro pessoal (Kotlin + Jetpack Compose, Material 3, dark mode sempre ativo). Idioma do código e da UI: **português brasileiro** (nomes de classes/funções/propriedades em pt-BR, ex: `TransacaoViewModel.adicionarTransacao`).

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

- `data/db/` — Room: 4 entidades (`Transacao`, `Categoria`, `ConfiguracaoPerfil`, `TransacaoRecorrente`) + DAOs. Agregações (saldo, somas por categoria/período) são feitas **no SQL**, não em memória. Enums persistidos por nome; `LocalDate` como epoch day (`Converters`).
- `data/repository/FinanceRepository.kt` — único ponto de acesso a dados; contém seed de categorias padrão, lançamento de recorrências vencidas e importação com dedup (data+valor+categoria).
- `data/PerfilManager.kt` — **conceito central**: perfil ativo em SharedPreferences exposto como StateFlow. `perfilAtivo` = escolha do usuário (PESSOA_FISICA/MEI/CNPJ, define layout); `perfilDados` = balde efetivo das queries — para MEI vira `MEI_PESSOAL`/`MEI_NEGOCIO` conforme a aba do dashboard. Todo dado no banco é particionado pela coluna `perfil` (um único banco, não um por perfil). ViewModels fazem `perfilDados.flatMapLatest { query }`, então trocar perfil/aba atualiza todas as telas automaticamente.
- `data/io/` — Export (CSV/JSON/PDF com `PdfDocument` nativo), `ParserImportacao` (classe pura, sem Android — é ela que os testes unitários cobrem; `ImportManager` só resolve a Uri) e Backup semanal (todos os perfis com dados, 4 por perfil, em `getExternalFilesDir`; restore filtra pelo perfil ativo). Usa Storage Access Framework — **sem permissões de storage; não adicionar `WRITE_EXTERNAL_STORAGE`**. Arquivos externos (CSV/JSON) trafegam valores em **reais decimais**; a conversão para centavos acontece no parser.
- `viewmodel/` — 1 por aba + `PerfilViewModel` (seleção inicial). Erros de escrita viram mensagens num `SharedFlow<String>` coletado pela UI (snackbar/toast).
- `ui/screen/` + `ui/component/` — `TransacaoModal` (bottom sheet criar/editar com máscara de centavos BR) e `TransacaoItemDismissivel` (swipe-delete + undo) são reutilizados por Home e Transações. Gráficos da Análise são Canvas do Compose puro — **não adicionar lib de gráficos**.
- Tema: `FinanAppTheme(escalaFonte, corPrimaria)` dinâmico via `AparenciaManager`; dark mode fixo.

## Convenções

- **Dinheiro é `Long` em CENTAVOS** em todo o app (entidades, ViewModels, UI). Exibição sempre via `utils/Formatadores.moeda(Long)` (pt-BR). Cuidado com divisão inteira ao calcular proporções em gráficos (`toDouble()` primeiro).
- **Modelo preparado para sync entre aparelhos** (banco v3): toda entidade tem `uuid` (identidade global, índice único), `atualizadoEm` (epoch millis, "última edição vence") e `deletado` (tombstone). Regras: **deletar = marcar `deletado = true`** via repository (nunca `@Delete` direto — exceto `deletarTodasTransacoes`, que é limpeza local); **toda escrita passa pelo repository**, que carimba `atualizadoEm`; **queries de leitura filtram `deletado = 0`**; undo = `restaurarTransacao` (limpa o tombstone, não re-insere). Import deduplica por uuid além da tripla data+valor+categoria.
- Categorias nunca são deletadas: arquivar preserva histórico; renomear usa `FinanceRepository.renomearCategoria`, que propaga o novo nome para transações e recorrências (categoria é referenciada por nome, não por id).
- Novos valores no enum `Perfil` não exigem migração (coluna TEXT), mas qualquer mudança nas entidades exige bump de versão + migração no `AppDatabase` (ver `MIGRACAO_1_2` como modelo).
- Filtros de período usam `utils/PeriodoFiltro` (semana começa na segunda). Telas que dependem de "hoje" combinam com `utils/fluxoDataAtual()` (re-emite à meia-noite) — nunca capturar `LocalDate.now()` uma vez só num Flow de longa duração.
- Salário fixo (Configurações) é materializado como `TransacaoRecorrente` mensal com descrição "Salário fixo" — o `ConfigViewModel` mantém as duas coisas em sincronia.
