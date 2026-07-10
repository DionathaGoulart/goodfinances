# Melhorias pendentes

Backlog do que ficou de fora das levas de correção/features de 2026-07-10 (auditoria end-to-end). Nada aqui bloqueia o build.

## Ação manual (crítico)

- [ ] **Publicar o `firestore-rules.txt` no Firebase Console.** A correção de segurança das casas (auto-convite, troca da lista de membros, squatting de códigos de convite) só vale depois do deploy — o arquivo no repo é só referência.

## UX pendente (menores)

- [ ] **Unificar entrada de dinheiro.** O parse "1.500" foi corrigido, mas a Config ainda usa texto decimal livre (`reaisParaCentavos`) enquanto modal/Ônibus usam máscara de centavos ("digite 1234 → R$ 12,34"). Extrair o `CampoMoeda` do `OnibusScreen` para `ui/component/` e usar em salário/DAS/orçamento/edição de recorrência.
- [ ] **Escala no eixo Y do gráfico de linha** (`GraficoLinha.kt`). Grid sem valores; rotular ao menos topo/meio/zero com `moedaCompacta`. Tooltip por toque não tem hint de descoberta.
- [ ] **Chip "Personalizado" da Análise sem o range** (`AnaliseScreen.kt`). Depois do DateRangePicker, mostrar "12/05 – 20/06" no label do chip.
- [ ] **`PerfilSelecaoScreen` sem scroll.** `Column` centrada sem `verticalScroll` — pode clipar em landscape.
- [ ] Cosméticos (baixa): escala de espaçamento ad-hoc (2..24dp → consolidar em 4/8/12/16/24); botão ATUALIZAR vermelho em edição de gasto (mesma cor semântica do DELETAR — mover CTA para `primary`); detalhe da fatia da pizza desloca a legenda (reservar altura como o `GraficoLinha` faz); `subAbaIndice` da Análise salvo como Int dessincroniza quando a aba Fiscal some.

## Limitações conhecidas (decisão de design, não esquecimento)

- **Dedup de categoria pós-rename no sync pessoal.** Dois aparelhos semeiam "Alimentação" com uuids diferentes; renomear num deles cria duplicata no outro ("Alimentação" vazia + "Comida"). Exige redesign de identidade de categoria (hoje é por nome). Raro; esperar report real.
- **Dedup de import por data+valor+categoria** descarta duplicatas legítimas (ex.: duas passagens iguais no mesmo dia ao mesclar CSV sem uuid). Tradeoff aceito do import.
- **`ContaAgendada` sem UI.** Entidade/sync/backup/notificação ativos, mas sem tela de criação — CLAUDE.md proíbe recriar as telas removidas sem pedido explícito. A avaliação diária de notificação serve dados restaurados/sincronizados e custa quase nada.

## Infra

- [ ] **Testes automatizados das lógicas novas**: `proximoLancamentoMensal` (duplicação de salário/DAS), motor de recorrência com `diaMensal` (drift 31→28), `transacoesParaExport` (recorte de período). Hoje vivem em ViewModel/repository; o projeto só testa classes puras (`src/test`) — extrair para classes puras ou adicionar Room in-memory.
- [ ] **Bump de versão** (`versionCode`/`versionName` no `app/build.gradle.kts`) quando for gerar a próxima release — necessário para o update in-app via GitHub Releases detectar a versão nova.

## Validação da leva atual (checklist de teste manual)

- [ ] `.\gradlew.bat assembleDebug` + `.\gradlew.bat testDebugUnitTest`
- [ ] Migração Room v12→13 abrindo o app com dados existentes (recorrências preservam o dia)
- [ ] Fluxo Casa com as regras novas publicadas: criar casa, entrar por convite, sair
- [ ] Home restruturada: scroll único, swipe de contexto, grupos de cartão, busca (lupa), card de orçamento
- [ ] Widget "Saldo" adicionado à home do Android
- [ ] Chip de categoria sugerida no modal; export com período; edição de recorrência
