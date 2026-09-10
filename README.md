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
2. **File > Open** e selecione a pasta `QRCodeApp` (a pasta raiz, que contém o `settings.gradle.kts`).
3. Aguarde o Android Studio importar o projeto. Se aparecer um aviso pedindo para
   configurar o **Gradle Wrapper**, clique em "OK" / "Use Gradle from ..." — o
   Studio baixa automaticamente a versão do Gradle indicada em
   `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.7).
4. Deixe o **Gradle Sync** terminar (ele vai baixar as dependências: OkHttp, Retrofit, Gson, Coroutines).
5. Rode em um emulador ou aparelho físico (mínimo Android 7.0 / API 24).

## Estrutura do projeto

```
QRCodeApp/
├── app/
│   ├── build.gradle.kts          -> dependências (Retrofit, OkHttp, Gson, Coroutines)
│   └── src/main/
│       ├── AndroidManifest.xml   -> permissões INTERNET e CAMERA + FileProvider
│       ├── java/com/example/qrcodeapp/
│       │   ├── MainActivity.kt   -> lógica das duas telas/funcionalidades
│       │   ├── api/
│       │   │   ├── QrApiService.kt    -> interface Retrofit (endpoints da API)
│       │   │   └── RetrofitClient.kt  -> configuração do Retrofit/OkHttp
│       │   └── model/
│       │       └── QrReadResponse.kt  -> modelo do JSON de leitura
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/ (strings, colors, themes)
│           └── xml/file_paths.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Funcionalidades

### 1. Gerar QR Code
O usuário digita um texto/link e o app faz uma requisição **GET** para:
```
https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=<texto>&ecc=M
```
A API responde diretamente com a **imagem PNG** do QR Code, que é baixada com
OkHttp/Retrofit e exibida na tela.

### 2. Ler QR Code
O usuário tira uma foto do QR Code (via câmera do aparelho). O app envia essa
imagem em uma requisição **POST multipart/form-data** para:
```
https://api.qrserver.com/v1/read-qr-code/?outputformat=json
```
A API decodifica a imagem e retorna um JSON com o conteúdo do QR Code lido.

Ambas as requisições seguem o mesmo padrão ensinado em aula: OkHttp + Retrofit +
Gson + Kotlin Coroutines (`Dispatchers.IO` + `lifecycleScope`).

## Observações
- Não é necessária nenhuma chave de API (a QR Server API é gratuita e pública).
- É necessário emulador/aparelho com internet e câmera (ou emulador com câmera virtual habilitada).
