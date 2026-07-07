# Sincronização — a Casa compartilhada

## Conceito

A **Casa** é uma carteira compartilhada entre pessoas (casal, família). Cada membro entra com a conta Google; os lançamentos do perfil "Casa" sincronizam entre todos os aparelhos em tempo real. Os perfis pessoais (Pessoa Física, MEI, CNPJ) **nunca saem do aparelho**.

## Estrutura no Firestore

```
casas/{casaId}
  ├── codigoConvite: "A3F7KP"      (6 chars, sem 0/O/1/I)
  ├── membros: [uid1, uid2]
  ├── criadoPor, criadoEm
  ├── transacoes/{uuid}            (1 doc por transação)
  │     valor, tipo, categoria, descricao, data (epochDay),
  │     atualizadoEm, deletado, criadoPor
  ├── categorias/{uuid}
  │     nome, tipo, cor, arquivada, atualizadoEm, deletado
  ├── cartoes/{uuid}               (nome, diaFechamento, diaVencimento, cor, ...)
  ├── metas/{uuid}                 (nome, valorAlvo, valorGuardado, prazo, cor, ...)
  └── contas/{uuid}                (descricao, valor, tipo, categoria, vencimento, pago, ...)

usuarios/{uid}/backups/{perfil}    (backup na nuvem, privado por usuário)
  └── json, criadoEm
usuarios/{uid}/perfis/{perfil}/{transacoes|categorias|cartoes|metas|contas}
                                   (sync pessoal entre aparelhos, opt-in)
```

`cartoes`, `metas` e `contas` são coletivas na Casa (qualquer membro edita — sem guard de autor, diferente das transações). Regras em `firestore-rules.txt`. Metas/contas usam `atualizadoEm`/`deletado` (tombstone) como o resto.

O id de cada documento é o **uuid** da linha local — a mesma transação tem a mesma identidade em todos os aparelhos.

## Fluxo (SyncManager)

Iniciado no `FinanApplication`; liga/desliga automaticamente conforme o usuário entra/sai de uma casa.

**PULL (nuvem → aparelho)** — snapshot listeners nas coleções `transacoes` e `categorias`:
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

## Backup na nuvem

Independente do sync da Casa: com o backup automático ligado **e** o usuário logado, o backup semanal de **cada perfil** (inclusive os pessoais) também sobe como JSON para `usuarios/{uid}/backups/{perfil}` (limite de 900 KB por doc — acima disso fica só o local). O **Restaurar do Backup** compara local × nuvem e usa o mais novo. Trocou de celular? Instala, faz login, restaura.

## Privacidade e regras

As regras do Firestore (ver [configuracao-firebase.md](configuracao-firebase.md)) garantem:
- Dados de uma casa: legíveis/graváveis **apenas pelos membros** daquela casa
- Backups: **apenas pelo próprio usuário**
- Qualquer usuário logado consegue buscar uma casa pelo código (necessário para o convite funcionar)
