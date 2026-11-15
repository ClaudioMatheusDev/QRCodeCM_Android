# Cardápio Online — QR Code das Mesas

Aplicativo Android (Kotlin) para restaurantes: **gera o QR Code de cada mesa**
(que leva o cliente direto ao cardápio digital) e também **lê o QR de uma mesa**
para conferir e abrir o cardápio. Usa a API pública e gratuita
**QR Server / goqr.me** (`https://api.qrserver.com`).

## Como funciona

- **Gerar QR da mesa:** informe o link do cardápio do restaurante (salvo
  automaticamente para as próximas mesas) e o número da mesa. O app monta a URL
  final — ex.: `https://meurestaurante.com/cardapio?mesa=12` — e gera o QR Code.
- **Ler QR da mesa:** fotografe o QR Code; se ele contiver um link, aparece o
  botão **Abrir cardápio**, que abre o endereço no navegador.

## Como abrir no Android Studio

1. Abra o Android Studio.
2. **File > Open** e selecione a pasta raiz do projeto (a que contém o `settings.gradle.kts`).
3. Aguarde o **Gradle Sync** (o wrapper baixa o Gradle 8.7 e as dependências).
4. Rode em um emulador ou aparelho físico (mínimo Android 7.0 / API 24).

Pela linha de comando (precisa de JDK 17 e Android SDK):

```
./gradlew assembleDebug        # gera o APK
./gradlew testDebugUnitTest    # roda os testes unitários
```

## Estrutura do projeto

```
app/src/main/java/com/example/qrcodeapp/
├── MainActivity.kt            -> tela principal (UI, câmera, compartilhar, consulta de prato em Thread)
├── MainViewModel.kt           -> estado da tela + chamadas à API de QR (sobrevive a rotação/morte do processo)
├── HistoricoActivity.kt       -> ListView com o histórico salvo no SQLite
├── DetalheConsultaActivity.kt -> detalhes de um registro (recebidos via Bundle)
├── api/
│   ├── QrApiService.kt        -> endpoints da QR Server (Retrofit)
│   ├── RetrofitClient.kt      -> configuração do Retrofit/OkHttp
│   └── MealDbClient.kt        -> consulta TheMealDB com HttpURLConnection + BufferedReader + JSONObject
├── db/
│   ├── BancoHelper.kt         -> SQLiteOpenHelper (onCreate / onUpgrade)
│   └── ConsultaDao.kt         -> DAO: inserir / listar (Cursor)
├── model/
│   ├── QrReadResponse.kt      -> JSON da leitura de QR
│   └── ConsultaPrato.kt       -> registro do histórico (prato consultado)
└── util/
    ├── CardapioLinks.kt       -> montar/validar link do cardápio (com testes)
    ├── Prato.kt               -> reconhecer o código do prato no texto do QR (com testes)
    └── Imagens.kt             -> decodificar foto reduzida (evita falta de memória)
```

## Funcionalidades

### 1. Gerar QR Code da mesa
Informe o link do cardápio (fica salvo) e o número da mesa. O app monta a URL
(`https://meurestaurante.com/cardapio?mesa=12`; o parâmetro entra antes de um
eventual `#fragmento`, e o valor é codificado) e faz um **GET** para:
```
https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=<url>&ecc=M
```
A API devolve a **imagem PNG**, que é exibida e pode ser compartilhada.

### 2. Ler QR Code
O usuário tira uma foto; o app reduz a imagem (máx. 1280 px) e envia em um
**POST multipart/form-data** para:
```
https://api.qrserver.com/v1/read-qr-code/?outputformat=json
```
Se o conteúdo for um link, aparece o botão **Abrir cardápio**.

### 3. Consulta do prato via QR Code + histórico (SQLite)
O QR Code de um prato contém o código dele no **TheMealDB** (ex.: `52772`, ou
`prato:52772`). Ao ler um QR assim:
1. Uma **Thread** secundária consulta
   `https://www.themealdb.com/api/json/v1/1/lookup.php?i=<código>` usando
   `HttpURLConnection` + `BufferedReader`, e lê nome, categoria, origem,
   ingredientes e modo de preparo com `JSONObject`.
2. O resultado volta para a tela com `runOnUiThread`, com feedback por **Toast**.
3. Consultas bem-sucedidas são gravadas no SQLite (`BancoHelper` + `ConsultaDao`).
4. **Pratos consultados** lista os registros (lidos com `Cursor`) numa
   `ListView` + `ArrayAdapter`; tocar num item (`onItemClick`) abre a tela de
   detalhes via `Intent`, com os dados num `Bundle`.

Para testar, gere um QR com o texto `52772` (Teriyaki Chicken Casserole) em
qualquer gerador de QR Code e escaneie pelo app. Outros códigos: `52771`,
`52874`, `52959`. Os dados do TheMealDB vêm em inglês.

## Observações
- Nenhuma chave de API é necessária (QR Server e TheMealDB, com a chave pública de testes `1`).
- A foto do QR é enviada ao serviço externo goqr.me para decodificação.
- É necessário emulador/aparelho com internet e câmera (ou câmera virtual no emulador).
