# 💰 GoodFinances

**Versão atual: 1.3.0**

App Android de controle financeiro pessoal e familiar, com **carteira compartilhada sincronizada em tempo real** entre celulares. Feito em Kotlin + Jetpack Compose, dark mode nativo, 100% em português.

## 📲 Instalação e atualizações

Baixe o APK mais recente na página de [**Releases**](https://github.com/DionathaGoulart/goodfinances/releases/latest). O app **verifica sozinho** se saiu versão nova (uma vez por dia) e oferece o download com um toque — publicou release nova aqui, todo mundo recebe o aviso no celular.

O que mudou em cada versão está no [**CHANGELOG**](CHANGELOG.md).

> Vindo de uma versão anterior à 1.2.0? Aquela atualização exigiu **desinstalar o app antes de instalar** (a chave de assinatura mudou). Foi a última vez — do 1.3.0 em diante a atualização é normal. O passo a passo de como não perder nada está no [CHANGELOG](CHANGELOG.md#120-beta).

## ✨ Funcionalidades

**Controle financeiro**
- Dashboard com saldo total/do mês, ganhos e gastos em tempo real — e **resumo do fechamento** do mês anterior no começo de cada mês
- Lançamentos com categoria, descrição e data — máscara de moeda brasileira (digite `1234` e vira `R$ 12,34`)
- **Uma lista só para pessoal e casa**: em cada ganho/gasto você escolhe **de quem é** — da casa (padrão), seu ou da outra pessoa —, e os chips `Tudo · Casa · <nomes>` recortam a lista. Só a Empresa fica separada
- **Compras parceladas** (o valor total é dividido em um lançamento por mês) e **transferências entre contextos** (Pessoal ↔ Empresa) com as duas pernas vinculadas — deletou uma, some a outra
- **Compras no cartão de crédito** agrupadas por cartão na Home, com **"Pagar fatura"** num toque — a pendência só entra no saldo quando você paga
- **Cartão é único, não por contexto**: cadastrou uma vez, serve para pessoal, casa e empresa. A aba **Cartões** mostra tudo que passou em cada cartão com a quebra de onde veio — *"Nubank R$ 260 — Meu 250 · Casa 10"*
- **Próximos meses** na Home: o que já está agendado para pagar/receber nos próximos 6 meses (contas fixas e parcelas já materializadas), com um toque para abrir o mês
- Histórico agrupado por data — **toque para editar, toque longo para o menu** (editar/esconder/excluir/encerrar recorrência)
- **Contas fixas** que se lançam sozinhas todo mês como "a pagar", com **"Encerrar recorrência"** num toque: para de repetir e limpa de uma vez tudo que ainda não foi pago (inclusive os atrasados) — o que você já pagou fica no histórico
- **Salário fixo lançado automaticamente** no dia configurado
- Gráficos em Canvas puro (categorias com alternância ganhos/gastos, linha e barras de 6 meses), **orçamentos por categoria** e estatísticas rápidas
- **Toque numa fatia da pizza** para ver os lançamentos daquela categoria — débito/dinheiro separados por categoria e crédito por cartão
- **Insights automáticos** do mês: variações relevantes vs o mês anterior ("gastou 32% a mais em Alimentação")
- **Faturas do cartão** em aberto, agrupadas por vencimento
- **Widget** de lançamento rápido na home do Android e **bloqueio por biometria** opcional

**Planejamento e avisos**
- **Notificações** — avisos de orçamento estourando, DAS vencendo, limite do MEI, contas vencendo, recorrências do dia e lembrete quando você fica dias sem registrar
- **Atualização in-app** — o app baixa e instala a versão nova sozinho quando sai release nova

**Notas fiscais e comprovantes**
- Anexe foto, imagem ou PDF a qualquer lançamento — **imagens viram PDF automaticamente**
- Backup dos arquivos no **Google Drive** da sua conta (grátis, pasta privada do app)
- Export ZIP organizado por **ano/mês + CSV anual** — pronto para a declaração de imposto

**Modos de uso** — dados totalmente isolados entre si:
- 👤 **Só pessoal** — controle do dia a dia
- 💼 **Pessoal + Empresa** — abas separadas, com pró-labore espelhado
- 🏢 **Só empresa** — Receita × Despesa + Lucro, painel fiscal com limite do MEI e lembrete do DAS
- 🏠 **Casa** — some dentro do Pessoal como carteira **compartilhada e sincronizada** entre membros (não é mais uma aba à parte)

**Casa compartilhada (sync)**
- Login com Google, criação de "Casa" com código de convite de 6 caracteres
- Lançamentos aparecem nos outros celulares em segundos, com o nome de quem lançou — e **só o autor pode editar/apagar** (garantido também no servidor)
- **Atribuição mútua**: dá para lançar um gasto como sendo da outra pessoa, e ela pode fazer o mesmo com você — a atribuição sincroniza junto
- Cada um escolhe compartilhar também os próprios gastos pessoais com a casa
- Funciona offline (sincroniza quando a conexão volta); conflitos resolvidos por "última edição vence"

**Dados**
- Export CSV, JSON e relatório em PDF · Import com prévia e deduplicação
- **Sincronização entre aparelhos** da mesma conta (opt-in) e backup automático semanal — local **e na nuvem**
- Dinheiro armazenado em **centavos (`Long`)** — sem erro de ponto flutuante

## 🛠 Tecnologias

Kotlin 2.1 · Jetpack Compose (Material 3) · Room (SQLite) · Hilt + KSP · Coroutines/Flow · WorkManager (notificações) · Firebase Auth + Firestore · Canvas API (gráficos) · JUnit

## 🚀 Como rodar

Pré-requisitos: Android Studio (ou JDK 17 + Android SDK 36).

```bash
git clone git@github.com:DionathaGoulart/goodfinances.git
```

O login Google e o sync exigem um projeto Firebase próprio (o `google-services.json` não é versionado) — siga o passo a passo em [docs/configuracao-firebase.md](docs/configuracao-firebase.md).

```powershell
.\gradlew.bat assembleDebug        # APK debug
.\gradlew.bat testDebugUnitTest    # testes unitários
```

Mais detalhes (release assinado, convenções de código): [docs/desenvolvimento.md](docs/desenvolvimento.md).

## 📚 Documentação

| Documento | Conteúdo |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | O que mudou em cada versão |
| [docs/arquitetura.md](docs/arquitetura.md) | Camadas, contextos e baldes, dono do lançamento, modelo de dados (v17), decisões de design |
| [docs/sincronizacao.md](docs/sincronizacao.md) | Como a Casa compartilhada funciona por dentro (push/pull, conflitos, tombstones, backup na nuvem) |
| [docs/configuracao-firebase.md](docs/configuracao-firebase.md) | Setup completo do Firebase (Auth, Firestore, regras de segurança) |
| [docs/desenvolvimento.md](docs/desenvolvimento.md) | Build, testes, assinatura de release e convenções |
| [docs/release.md](docs/release.md) | Como publicar uma versão e o contrato que mantém a auto-atualização funcionando |

## 📄 Licença

Uso **pessoal e não comercial** liberado — usar, modificar e compilar para você e sua família à vontade. **Uso comercial** (vender, publicar em loja de forma paga/com anúncios, usar em serviço remunerado) requer permissão por escrito do autor. Veja [LICENSE](LICENSE).

## 👤 Autor

**Dionatha Goulart** — dgoulart.work@gmail.com
