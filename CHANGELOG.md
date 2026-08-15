# Changelog

Todas as mudanças relevantes de cada versão. O app avisa sozinho quando sai release nova (uma vez por dia) e oferece o download com um toque.

## 1.3.0

Primeira versão **estável** da leva que começou no 1.2.0-beta: Pessoal e Casa unificados, cartão global e a aba Cartões saem do beta, agora com o controle de recorrência que faltava.

### 🔁 Encerrar uma recorrência direto da linha

Toque longo em qualquer lançamento que veio de uma conta fixa e escolha **Encerrar recorrência**. Antes o menu só oferecia "Excluir", que apaga **aquele mês** — para se livrar de uma conta que não existe mais era preciso repetir a operação 12 vezes.

O que acontece ao encerrar:

- a regra para de gerar novas ocorrências;
- **todo lançamento ainda não pago some de uma vez — inclusive os atrasados**. Antes o corte era "de hoje em diante" e a parcela vencida sobrevivia ao encerrar, justamente a linha vermelha que mais incomoda;
- o que você **já pagou continua no histórico** — é dinheiro que saiu de verdade.

A confirmação diz quantos lançamentos somem antes de você decidir. Não tem desfazer.

### 🔧 Outros

- `versionCode` 11 / `versionName` 1.3.0 — o aviso de atualização chega a quem está no 1.2.0
- Documentação revisada de ponta a ponta (arquitetura no banco v17, sincronização com convites/espelhos, guia de desenvolvimento)

---

## 1.2.0 (beta)

> ⚠️ **Esta versão exigiu desinstalar o app antes de instalar** — a chave de assinatura mudou e o Android não atualiza um app por cima quando a assinatura é diferente. **Foi a última vez**: do 1.3.0 em diante a atualização é normal.
>
> Se você ainda está numa versão anterior ao 1.2.0, antes de desinstalar: anote o **código de convite da Casa** (Configurações › Casa Compartilhada), ligue **Sincronização entre aparelhos** e espere sincronizar, e faça um **Export JSON** guardado fora do celular. Notas fiscais e comprovantes só voltam com o backup do Google Drive ligado.

### 🏠 Pessoal e Casa viraram uma coisa só

A Casa **deixou de ser uma aba**. Agora é uma lista única e, em cada ganho ou gasto, você escolhe **de quem é**:

- **Da casa** (padrão) — a conta que é dos dois
- **Você** ou **a outra pessoa** — dá para lançar o gasto dela, e ela pode lançar o seu. A atribuição sincroniza junto

Na Home, os chips `Tudo · Casa · <nomes>` recortam a lista e cada linha ganhou um selo dizendo de quem é. **Só a Empresa continua separada.**

> As pessoas aparecem no seletor conforme cada uma abre o app pela primeira vez nesta versão — é nesse momento que o nome é publicado na Casa.

### 💳 Cartão é único, não por contexto

Cadastrou um cartão, ele serve para pessoal, casa e empresa. O que continua separado é o **gasto**: usar o mesmo Nubank R$ 250 no pessoal e R$ 10 na casa soma certo em cada filtro.

Nova aba **Cartões** (dentro de Análise): tudo que passou em cada cartão, com a proporção de cada contexto e o item a item rotulado — *"Nubank R$ 260 — Meu 250 · Casa 10"*.

### 📅 Próximos meses

Faixa nova na Home com o que **já está agendado** para os próximos 6 meses (a pagar e a receber). Não é estimativa: sai das contas fixas e parcelas já lançadas. Toque num mês para abrir o histórico dele.

### 🔧 Correções e ajustes

- Notificações passaram a cobrir os lançamentos da Casa — como "da casa" virou o padrão, sem isso quase nenhum aviso de vencimento disparava
- Gastos frequentes e categorias da Casa voltaram a ter tela nas Configurações
- Export (CSV/JSON/PDF) agora leva pessoal e casa, não só o pessoal
- Linha do histórico não estoura mais em tela estreita
- Banco na versão 17 (migração automática)

---

## 1.1.0 (beta)

- **Aba Ônibus**: saldo do cartão de transporte que **desconta sozinho** nos dias de rotina, com viagens fora da rotina lançadas à mão
- **Gastos frequentes** criados direto no modal de lançamento ("Repetir todo mês", com "dura até")
- **Pendências**: compra no crédito, ocorrência recorrente e parcela futura só entram no saldo quando marcadas como pagas — com "A pagar", "A receber" e o estado **Atrasado**
- **Análise multi-contexto** (chips Pessoal/Empresa combináveis), pizza clicável e insights automáticos do mês
- **Lembretes de vencimento 3x por dia** (manhã/tarde/noite), limite do MEI, DAS e orçamento estourando
- **Widget** de lançamento rápido, busca em todos os meses, orçamentos por categoria e export com seletor de período
- **Notas fiscais e comprovantes** anexados ao lançamento (imagem vira PDF), com backup no Google Drive
- Endurecimento das regras do Firestore (convite, entrada na casa, guard de autor)

---

## 1.0.0

Primeira versão: lançamentos com máscara de moeda BR, categorias, dashboard com saldo/ganhos/gastos, gráficos em Canvas puro, perfis (Pessoa Física / MEI / CNPJ), export CSV/JSON/PDF com import deduplicado, backup automático semanal, **Casa compartilhada** com login Google e sync em tempo real via Firestore.
