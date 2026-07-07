# 💰 GoodFinances

App Android de controle financeiro pessoal e familiar, com **carteira compartilhada sincronizada em tempo real** entre celulares. Feito em Kotlin + Jetpack Compose, dark mode nativo, 100% em português.

## 📲 Instalação e atualizações

Baixe o APK mais recente na página de [**Releases**](https://github.com/DionathaGoulart/goodfinances/releases/latest). O app **verifica sozinho** se saiu versão nova (uma vez por dia) e oferece o download com um toque — publicou release nova aqui, todo mundo recebe o aviso no celular.

## ✨ Funcionalidades

**Controle financeiro**
- Dashboard com saldo total/do mês, ganhos e gastos em tempo real — e **resumo do fechamento** do mês anterior no começo de cada mês
- Lançamentos com categoria, descrição e data — máscara de moeda brasileira (digite `1234` e vira `R$ 12,34`)
- **Compras parceladas** (um lançamento por mês) e **transferências entre contextos** (Pessoal ↔ Empresa ↔ Casa) com as duas pernas vinculadas — deletou uma, some a outra
- Histórico agrupado por data, com busca e filtros por período, **tipo (ganho/gasto), categoria e membro da casa**
- Swipe para deletar com **Desfazer** · deslize para os lados na Home para trocar de contexto
- Transações recorrentes e **salário fixo lançado automaticamente** no dia configurado
- Gráficos em Canvas puro (categorias com alternância ganhos/gastos, linha e barras de 6 meses), **orçamentos por categoria** e estatísticas rápidas
- **Toque numa fatia da pizza** para ver os lançamentos daquela categoria
- **Insights automáticos** do mês: variações relevantes vs o mês anterior ("gastou 32% a mais em Alimentação")
- **Faturas do cartão** em aberto, agrupadas por vencimento
- **Widget** de lançamento rápido na home do Android e **bloqueio por biometria** opcional

**Planejamento**
- **Metas de economia** — defina um objetivo (ex: viagem), guarde/retire e acompanhe o progresso
- **Contas a pagar/receber** — agende boletos e valores a receber; marque como pago e vira lançamento
- **Notificações** — avisos de orçamento estourando, DAS vencendo, limite do MEI, contas vencendo, recorrências do dia e lembrete quando você fica dias sem registrar

**Notas fiscais e comprovantes**
- Anexe foto, imagem ou PDF a qualquer lançamento — **imagens viram PDF automaticamente**
- Backup dos arquivos no **Google Drive** da sua conta (grátis, pasta privada do app)
- Export ZIP organizado por **ano/mês + CSV anual** — pronto para a declaração de imposto

**Modos de uso** — dados totalmente isolados entre si:
- 👤 **Só pessoal** — controle do dia a dia
- 💼 **Pessoal + Empresa** — abas separadas, com pró-labore espelhado
- 🏢 **Só empresa** — Receita × Despesa + Lucro, painel fiscal com limite do MEI e lembrete do DAS
- 🏠 **Casa** — carteira **compartilhada e sincronizada** entre membros

**Casa compartilhada (sync)**
- Login com Google, criação de "Casa" com código de convite de 6 caracteres
- Lançamentos aparecem nos outros celulares em segundos, com o nome de quem lançou — e **só o autor pode editar/apagar** (garantido também no servidor)
- Visão **Membros**: cada um escolhe compartilhar os próprios gastos pessoais com a casa
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
| [docs/arquitetura.md](docs/arquitetura.md) | Camadas, sistema de perfis, modelo de dados, decisões de design |
| [docs/sincronizacao.md](docs/sincronizacao.md) | Como a Casa compartilhada funciona por dentro (push/pull, conflitos, tombstones, backup na nuvem) |
| [docs/configuracao-firebase.md](docs/configuracao-firebase.md) | Setup completo do Firebase (Auth, Firestore, regras de segurança) |
| [docs/desenvolvimento.md](docs/desenvolvimento.md) | Build, testes, assinatura de release e convenções |

## 📄 Licença

Uso **pessoal e não comercial** liberado — usar, modificar e compilar para você e sua família à vontade. **Uso comercial** (vender, publicar em loja de forma paga/com anúncios, usar em serviço remunerado) requer permissão por escrito do autor. Veja [LICENSE](LICENSE).

## 👤 Autor

**Dionatha Goulart** — dgoulart.work@gmail.com
