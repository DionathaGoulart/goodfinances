# Sincronização — a Casa compartilhada

## Conceito

A **Casa** é uma carteira compartilhada entre pessoas (casal, família). Cada membro entra com a conta Google; tudo que fica no balde `CASA` sincroniza entre todos os aparelhos em tempo real. Os baldes pessoais (Pessoa Física, MEI, CNPJ) **nunca saem do aparelho** — a menos que o usuário ligue o sync pessoal opt-in, que sobe para a área privada dele.

A Casa **não é uma aba**: ela e o Pessoal são uma lista só, e cada lançamento diz **de quem é** (`Dono`). Numa casa, tudo grava em `Perfil.CASA` — inclusive o atribuído a uma pessoa, senão a atribuição não seria mútua nem visível para o outro.

## Estrutura no Firestore

```
convites/{codigo}                  (código de 6 chars -> casaId; só GET, nunca LIST)

casas/{casaId}
  ├── codigoConvite: "A3F7KP"      (6 chars, sem 0/O/1/I)
  ├── membros: [uid1, uid2]        (só uid — identidade)
  ├── nomes: {uid1: "Ana", ...}    (nome de exibição, escrito em campo aninhado)
  ├── criadoPor, criadoEm
  ├── transacoes/{uuid}            (1 doc por transação)
  │     valor, tipo, categoria, descricao, data (epochDay), pago,
  │     atualizadoEm, deletado, criadoPor/criadoPorUid, pessoaUid/pessoaNome
  ├── categorias/{uuid}
  │     nome, tipo, cor, arquivada, atualizadoEm, deletado
  ├── cartoes/{uuid}               (nome, diaFechamento, diaVencimento, cor, origemUuid, ...)
  ├── metas/{uuid}                 (nome, valorAlvo, valorGuardado, prazo, cor, ...)
  ├── contas/{uuid}                (descricao, valor, tipo, categoria, vencimento, pago, ...)
  └── membros/{uid}/transacoes/{uuid}
                                   (espelho opt-in do pessoal de cada membro)

usuarios/{uid}/backups/{perfil}    (backup na nuvem, privado por usuário)
  └── json, criadoEm
usuarios/{uid}/perfis/{perfil}/{transacoes|categorias|cartoes|metas|contas}
                                   (sync pessoal entre aparelhos, opt-in)
```

`cartoes`, `metas` e `contas` são coletivas na Casa (qualquer membro edita — sem guard de autor, diferente das transações). Regras em `firestore-rules.txt` — **ao adicionar uma coleção, atualizar o arquivo e republicar no Console**. Metas/contas usam `atualizadoEm`/`deletado` (tombstone) como o resto.

O nome de exibição vai para o mapa `nomes` com update em campo aninhado (`nomes.<uid>`), nunca substituindo o mapa inteiro — senão um membro apagaria o nome dos outros. A cada aparelho que abre o app, `registrarMeuNome` publica o próprio; por isso uma pessoa só aparece no seletor de dono depois de abrir a versão nova pela primeira vez.

O espelho `membros/{uid}/transacoes` é o gasto **pessoal** que o membro escolheu mostrar para a casa: entra nas listas (`baldesVisiveis`), **nunca no saldo dos outros**. É o único caso em que o app apaga fisicamente uma linha local (o espelho `CASA_MEMBROS`).

O id de cada documento é o **uuid** da linha local — a mesma transação tem a mesma identidade em todos os aparelhos.

## Fluxo (SyncManager)

Iniciado no `FinanApplication`; liga/desliga automaticamente conforme o usuário entra/sai de uma casa.

**PULL (nuvem → aparelho)** — snapshot listeners em `transacoes`, `categorias`, `cartoes`, `metas` e `contas`:
1. O snapshot inicial entrega todos os docs; depois, só os alterados
2. Para cada doc: busca a linha local pelo uuid
3. Aplica somente se `atualizadoEm` remoto **>** local (*última edição vence*; empate mantém o local, o que evita eco)
4. A escrita vai **direto no DAO**, preservando o carimbo remoto (nunca pelo repository, que re-carimbaria)

**PUSH (aparelho → nuvem)** — reativo com debounce de 1,5s:
1. Observa `MAX(atualizadoEm)` das linhas do perfil CASA (incluindo tombstones)
2. Quando muda, sobe em batch as linhas com `atualizadoEm > marca` (marca guardada em prefs, por casa)
3. Escritas são *fire-and-forget*: a fila offline do Firestore é durável — pode lançar sem internet que sobe depois, mesmo se o app for fechado
4. Após subir, a marca avança para o maior `atualizadoEm` enviado

**Deleções** viajam como tombstones (`deletado = true`) — documentos nunca são removidos, senão a deleção não se propagaria de forma confiável.

## Conflitos

Estratégia: **last-write-wins** por `atualizadoEm` (epoch millis do aparelho que editou). Para uso doméstico é suficiente — se duas pessoas editarem a mesma transação ao mesmo tempo, vence quem salvou por último; nenhum dado além dessa edição é perdido.

## Categorias padrão sem duplicar

Só **quem cria a casa** semeia as categorias padrão (`semearCategoriasCasa`); os demais membros as recebem via sync. `garantirCategoriasPadrao` ignora o perfil CASA de propósito — se cada aparelho semeasse as suas, tudo duplicaria.

## Cartões espelhados na Casa

Cartão é global: cadastrou uma vez, serve para pessoal, casa e empresa. Para que uma compra da Casa consiga apontar para ele, cada cartão pessoal ganha um **espelho** `perfil = CASA` com uuid determinístico (`origemUuid` guarda o original). O espelho é one-way (original → espelho) e **read-only** do lado da Casa; ele é reconciliado ao criar/entrar/carregar a casa.

Ao **sair da casa**, os espelhos são tombstonados direto no Firestore **antes** do `arrayRemove` do membro — depois de sair, as regras negam a escrita e eles ficariam órfãos permanentes nos aparelhos dos outros.

Na hora de agrupar (fatura, pizza da Análise), sempre canonicalizar o espelho de volta para o cartão original — senão a mesma fatura aparece quebrada em dois grupos e Pessoal+Casa duplicam a fatia.

## Backup na nuvem

Independente do sync da Casa: com o backup automático ligado **e** o usuário logado, o backup semanal de **cada perfil** (inclusive os pessoais) também sobe como JSON para `usuarios/{uid}/backups/{perfil}` (limite de 900 KB por doc — acima disso fica só o local). O **Restaurar do Backup** compara local × nuvem e usa o mais novo. Trocou de celular? Instala, faz login, restaura.

## Privacidade e regras

As regras do Firestore (ver [configuracao-firebase.md](configuracao-firebase.md)) garantem:
- Dados de uma casa: legíveis/graváveis **apenas pelos membros** daquela casa
- Lançamentos da Casa: qualquer membro lê, **só o autor edita/apaga**
- Backups e sync pessoal: **apenas pelo próprio usuário**
- Convite: quem tem o código resolve `convites/{codigo}` por GET direto, mas **listar** os códigos é negado (sem isso dava para enumerar casas), e registrar um código exige já ser membro da casa apontada (impede squatting)
- Entrar pelo convite só pode **acrescentar o próprio uid** à lista de membros, mantendo os atuais e sem mexer no código nem no criador
