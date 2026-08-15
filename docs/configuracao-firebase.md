# Configuração do Firebase

O login Google, a Casa compartilhada e o backup na nuvem dependem de um projeto Firebase. O `google-services.json` **não é versionado** — quem clona o repositório precisa criar o próprio projeto (gratuito, plano Spark).

## 1. Criar o projeto

1. Acesse https://console.firebase.google.com → **Criar projeto** (ex: `finapp`)
2. Google Analytics pode ficar desativado

## 2. Registrar o app Android

1. No projeto: **+ Add app → Android**
2. Pacote: `com.finapp`
3. Adicione o **SHA-1 de debug** (obrigatório para o login Google):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android | Select-String "SHA1:"
```

4. Se for gerar release: adicione também o SHA-1 do seu keystore de release
   (⚙️ Project settings → General → card do app → *Add fingerprint*)

## 3. Habilitar o login Google

- Menu **Security → Authentication** → *Get started* → aba **Sign-in method** → habilitar **Google**

> ⚠️ **Importante:** habilite o Google **antes** de baixar o `google-services.json`. É a ativação que cria os clientes OAuth dentro do arquivo. Se baixou antes, baixe de novo (⚙️ Project settings → General → card do app).

Coloque o arquivo em **`app/google-services.json`**.

## 4. Criar o Firestore

- Menu **Databases & Storage → Firestore Database** → *Create database*
- Modo **produção**, região `southamerica-east1` (São Paulo)

## 5. Publicar as regras de segurança

Na aba **Rules** do Firestore, cole o conteúdo de **[`firestore-rules.txt`](../firestore-rules.txt)** (raiz do repositório) e publique. Esse arquivo é a fonte única das regras — não copie regras de outro lugar, e republique sempre que ele mudar.

O que elas garantem:

| Recurso | Quem pode |
|---|---|
| Criar casa | qualquer usuário logado, desde que se inclua nos membros e seja o `criadoPor` |
| Ler a casa | **somente membros**; listar casas é negado (impede enumerar) |
| Resolver um código de convite | qualquer logado, por GET direto — **listar** os códigos é negado |
| Registrar um código de convite | **somente membro** da casa apontada (impede squatting) |
| Entrar na casa | quem ainda não é membro, **só acrescentando o próprio uid** (sem trocar a lista, o código ou o criador) |
| Ler lançamentos da casa | **somente membros** daquela casa |
| Editar/apagar um lançamento | **somente o autor** (`criadoPorUid`) |
| Categorias, cartões, metas e contas da casa | qualquer membro (são coletivos) |
| Espelho pessoal (`membros/{uid}`) | todos os membros leem, **só o dono escreve** |
| Backups e sync pessoal (`usuarios/{uid}`) | **somente o próprio usuário** |

## 6. Verificar

Compile e rode o app: Configurações → Casa Compartilhada → *Entrar com Google*. Se aparecer "Login Google não configurado", o `google-services.json` foi baixado antes de habilitar o provedor Google — volte ao passo 3.

## Custos

O plano gratuito (Spark) dá 50 mil leituras e 20 mil escritas por dia no Firestore — um casal usando o app diariamente consome menos de 1% disso.
