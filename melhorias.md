# Melhorias pendentes

Backlog do que está mapeado mas ficou de fora das levas entregues. Nada aqui bloqueia o build. Atualizado no 1.3.0.

## Ação manual (crítico)

- [ ] **Publicar o `firestore-rules.txt` no Firebase Console.** As regras do repositório são só referência — auto-convite, troca da lista de membros, squatting de código de convite e o guard de autor nas transações só valem depois do deploy. Republicar sempre que o arquivo mudar.

## UX pendente (menores)

- [ ] **Unificar entrada de dinheiro.** Modal e Ônibus usam máscara de centavos ("digite 1234 → R$ 12,34"), mas a Config ainda usa texto decimal livre (`reaisParaCentavos`) em salário/DAS/orçamento/edição de recorrência. Extrair o `CampoMoeda` do `OnibusScreen` para `ui/component/` e reusar.
- [ ] **Escala no eixo Y do gráfico de linha** (`GraficoLinha.kt`). O grid não tem valores; rotular ao menos topo/meio/zero com `moedaCompacta`. O tooltip por toque também não tem dica de descoberta.
- [ ] **Chip "Personalizado" da Análise sem o range** (`AnaliseScreen.kt`). Depois do `DateRangePicker`, mostrar "12/05 – 20/06" no label do chip.
- [ ] **`PerfilSelecaoScreen` sem scroll.** `Column` centrada sem `verticalScroll` — pode clipar em landscape ou com fonte grande.
- [ ] Cosméticos (baixa): escala de espaçamento ad-hoc (2..24dp → consolidar em 4/8/12/16/24); botão ATUALIZAR vermelho na edição de gasto (mesma cor semântica do DELETAR — mover o CTA para `primary`); o detalhe da fatia da pizza desloca a legenda (reservar altura como o `GraficoLinha` faz); `subAbaIndice` da Análise salvo como Int dessincroniza quando a aba Fiscal some.

## Limitações conhecidas (decisão de design, não esquecimento)

- **Dedup de categoria pós-rename no sync pessoal.** Dois aparelhos semeiam "Alimentação" com uuids diferentes; renomear num deles cria duplicata no outro ("Alimentação" vazia + "Comida"). Exige redesign de identidade de categoria (hoje é por nome). Raro; esperar report real.
- **Dedup de import por data+valor+categoria** descarta duplicatas legítimas (ex.: duas passagens iguais no mesmo dia ao mesclar CSV sem uuid). Tradeoff aceito do import.
- **`ContaAgendada` e `Meta` sem UI.** Entidades, sync, backup e notificação seguem ativos, mas as telas foram removidas — não recriar sem pedido explícito. A avaliação diária serve dados restaurados/sincronizados e custa quase nada.
- **Encerrar recorrência não tem desfazer.** Tombstona todas as ocorrências não pagas de uma vez; a UI confirma antes e diz quantas somem. Um "desfazer" exigiria guardar o lote encerrado.

## Infra

- [ ] **Testes das lógicas que ainda vivem em ViewModel/repository**: `transacoesParaExport` (recorte de período) e a materialização de salário/DAS. O projeto só testa classes puras (`src/test`) — extrair para classes puras ou adicionar Room in-memory. Já cobertos: recorrências, vencimentos, ônibus, espelho de cartão, parser de import, períodos e formatadores.

## Checklist de teste manual (a cada release)

- [ ] `.\gradlew.bat assembleDebug` + `.\gradlew.bat testDebugUnitTest`
- [ ] Abrir com dados existentes (migração do banco preserva recorrências, pendências e o dia mensal)
- [ ] Fluxo Casa com as regras publicadas: criar casa, entrar por convite, atribuir gasto ao outro, sair
- [ ] Home: scroll único, grupos de cartão, "Pagar fatura", busca (lupa), card de orçamento, faixa "Próximos meses"
- [ ] Recorrência: criar pelo modal ("Repetir todo mês"), editar na Config, **encerrar pela linha** e conferir que só o pago sobrou
- [ ] Widget "Saldo" na home do Android e atualização in-app apontando para a release nova
