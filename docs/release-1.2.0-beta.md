# GoodFinances 1.2.0 (beta)

Notas para a release no GitHub. Publicação: veja "Como publicar" no fim.

---

## 🏠 Pessoal e Casa viraram uma coisa só

A Casa **deixou de ser uma aba**. Agora é uma lista única e, em cada ganho ou
gasto, você escolhe **de quem é**:

- **Da casa** (padrão) — a conta que é dos dois
- **Você** ou **a outra pessoa** — dá para lançar o gasto dela, e ela pode
  lançar o seu. A atribuição sincroniza junto

Na Home, os chips `Tudo · Casa · <nomes>` recortam a lista, e cada linha ganhou
um selo dizendo de quem é. **Só a Empresa continua separada.**

## 💳 Cartão é único, não por contexto

Cadastrou um cartão, ele serve para pessoal, casa e empresa. O que continua
separado é o **gasto**: usar o mesmo Nubank R$ 250 no pessoal e R$ 10 na casa
soma certo em cada filtro.

Nova aba **Cartões** (dentro de Análise): tudo que passou em cada cartão, com a
proporção de cada contexto e o item a item rotulado —
*"Nubank R$ 260 — Meu 250 · Casa 10"*.

## 📅 Próximos meses

Faixa nova na Home com o que **já está agendado** para os próximos 6 meses (a
pagar e a receber). Não é estimativa: sai das contas fixas e parcelas já
lançadas. Toque num mês para abrir o histórico dele.

## 🔧 Outros

- Análise: os contextos combináveis agora incluem a Casa junto do Pessoal
- Linha do histórico não estoura mais em tela estreita
- Banco na versão 17 (migração automática; nada a fazer)

---

## Como publicar

Esta build **não pôde ser gerada no ambiente do agente** — faltam os arquivos
que não são versionados. Do seu lado:

1. `app/google-services.json` (baixe do console do Firebase)
2. `key.properties` + `finapp-release.jks` na raiz — **os mesmos de sempre**;
   com um keystore diferente o Android recusa a atualização por cima do app
   instalado
3. `gh auth login` (a release é publicada pelo `gh`)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\scripts\release.ps1 -Versao 1.2.0 -Notas "ver docs/release-1.2.0-beta.md"
```

O `versionCode` já foi para **9** (era 8): o Android recusa instalar por cima
de um código igual ou menor, e o beta anterior já usou o 8.

O `versionName` segue **"1.2.0"**, sem sufixo `-beta`, de propósito:
`AtualizacaoManager.ehMaisNova` compara com `split('.')` + `toIntOrNull`, então
`"1.2.0-beta"` viraria `[1, 2, 0]` e empataria com o `1.2.0` final — quem
estivesse no beta nunca receberia o aviso da versão estável. O "beta" fica no
título e na tag da release.

Como a tag `v1.2.0-beta` já existe apontando para o commit anterior, o script
vai falhar ao recriá-la: apague-a antes (`git tag -d v1.2.0-beta` e
`git push origin :refs/tags/v1.2.0-beta`) ou publique como `v1.2.1-beta`.
