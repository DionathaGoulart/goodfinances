# 💰 FinanApp

App Android de controle financeiro pessoal e familiar, com **carteira compartilhada sincronizada em tempo real** entre celulares. Feito em Kotlin + Jetpack Compose, dark mode nativo, 100% em português.

## ✨ Funcionalidades

**Controle financeiro**
- Dashboard com saldo total/do mês, ganhos e gastos em tempo real
- Lançamentos com categoria, descrição e data — máscara de moeda brasileira (digite `1234` e vira `R$ 12,34`)
- Histórico agrupado por data, com busca e filtros (semana/mês/ano/personalizado)
- Swipe para deletar com **Desfazer**
- Transações recorrentes (ex: assinatura mensal) e **salário fixo lançado automaticamente** no dia configurado
- Gráficos desenhados em Canvas puro (pizza por categoria, linha e barras dos últimos 6 meses) + estatísticas rápidas

**Perfis** — dados totalmente isolados entre si:
- 👤 **Pessoa Física** — controle pessoal
- 💼 **MEI** — abas separadas Pessoal | Negócio
- 🏢 **CNPJ** — Receita × Despesa + Lucro do mês
- 🏠 **Casa** — carteira **compartilhada e sincronizada** entre membros

**Casa compartilhada (sync)**
- Login com Google, criação de "Casa" com código de convite de 6 caracteres
- Lançamentos aparecem nos outros celulares em segundos, com o nome de quem lançou
- Funciona offline (sincroniza quando a conexão volta); conflitos resolvidos por "última edição vence"

**Dados**
- Export CSV, JSON e relatório em PDF · Import com prévia e deduplicação
- Backup automático semanal — local **e na nuvem** (restaura tudo ao trocar de celular)
- Dinheiro armazenado em **centavos (`Long`)** — sem erro de ponto flutuante

## 🛠 Tecnologias

Kotlin 2.1 · Jetpack Compose (Material 3) · Room (SQLite) · Hilt + KSP · Coroutines/Flow · Firebase Auth + Firestore · Canvas API (gráficos) · JUnit

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
