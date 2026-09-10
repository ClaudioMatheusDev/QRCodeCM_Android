# Documentacao da API Utilizada

## Aplicativo: Cardapio Online com QR Code

## 1. Introducao

Este documento apresenta os principais aspectos da API utilizada no desenvolvimento do aplicativo **Cardapio Online com QR Code**. O objetivo do aplicativo e auxiliar restaurantes, bares e lanchonetes no uso de cardapios digitais por mesa.

A aplicacao possui duas funcionalidades principais:

- Gerar QR Codes personalizados para mesas de um restaurante.
- Ler QR Codes a partir de uma foto capturada pela camera do dispositivo.

Com isso, o aplicativo atende ao requisito de utilizar uma API externa capaz de **gerar** e **ler** QR Codes.

## 2. Relevancia da Aplicacao

A aplicacao escolhida tem relevancia na area de atendimento em restaurantes. Atualmente, muitos estabelecimentos utilizam cardapios digitais acessados por QR Code, pois essa solucao reduz o uso de cardapios impressos, facilita a atualizacao dos produtos e melhora a organizacao do atendimento.

No aplicativo desenvolvido, cada mesa pode receber um QR Code proprio. Ao escanear esse codigo, o cliente e direcionado para o cardapio online do restaurante, ja com a identificacao da mesa na URL.

Exemplo:

```text
https://meurestaurante.com/cardapio?mesa=12
```

Dessa forma, o sistema pode ser usado para identificar de qual mesa partiu o acesso, facilitando processos como atendimento, pedidos ou conferencia dos QR Codes instalados nas mesas.

## 3. API Utilizada

A API utilizada no projeto e a **QR Server API**, tambem conhecida como API do site **goqr.me**.

Documentacao oficial:

```text
https://goqr.me/api/
```

URL base utilizada no aplicativo:

```text
https://api.qrserver.com/v1/
```

A API foi escolhida porque oferece, de forma publica, os dois recursos necessarios para o projeto:

- Criacao de QR Code a partir de texto ou link.
- Leitura e decodificacao de QR Code a partir de uma imagem.

Outro ponto positivo e que a API nao exige chave de acesso para os recursos utilizados no aplicativo, o que facilita o uso em projetos academicos e prototipos.

## 4. Endpoints Utilizados

| Funcionalidade | Metodo HTTP | Endpoint | Retorno |
| --- | --- | --- | --- |
| Gerar QR Code | GET | `/create-qr-code/` | Imagem PNG |
| Ler QR Code | POST | `/read-qr-code/` | JSON |

## 5. Geracao de QR Code

Para gerar um QR Code, o aplicativo utiliza o seguinte endpoint:

```text
GET https://api.qrserver.com/v1/create-qr-code/
```

Esse endpoint recebe um texto ou link e retorna uma imagem PNG contendo o QR Code gerado.

### Parametros utilizados

| Parametro | Descricao | Valor usado no app |
| --- | --- | --- |
| `size` | Define o tamanho da imagem gerada. | `300x300` |
| `data` | Conteudo que sera codificado no QR Code. | Link do cardapio com a mesa |
| `ecc` | Nivel de correcao de erro do QR Code. | `M` |

Exemplo de requisicao:

```text
https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=https://meurestaurante.com/cardapio?mesa=12&ecc=M
```

No aplicativo, o usuario informa o link base do cardapio e o numero da mesa. A aplicacao monta automaticamente a URL final e envia esse conteudo para a API. A resposta recebida e convertida em uma imagem e exibida na tela.

## 6. Leitura de QR Code

Para ler um QR Code, o aplicativo utiliza o seguinte endpoint:

```text
POST https://api.qrserver.com/v1/read-qr-code/
```

Esse endpoint recebe uma imagem enviada no formato `multipart/form-data` e retorna um JSON com o conteudo decodificado.

### Parametro utilizado

| Parametro | Descricao | Valor usado no app |
| --- | --- | --- |
| `outputformat` | Define o formato da resposta da API. | `json` |

Exemplo de endpoint usado:

```text
https://api.qrserver.com/v1/read-qr-code/?outputformat=json
```

No aplicativo, o usuario tira uma foto do QR Code usando a camera do dispositivo. Em seguida, a imagem e comprimida e enviada para a API. Se a leitura for bem-sucedida, o conteudo encontrado no QR Code e exibido na tela.

Exemplo de resposta JSON:

```json
[
  {
    "type": "qrcode",
    "symbol": [
      {
        "seq": 0,
        "data": "https://meurestaurante.com/cardapio?mesa=12",
        "error": null
      }
    ]
  }
]
```

Principais campos da resposta:

| Campo | Descricao |
| --- | --- |
| `type` | Tipo de codigo identificado na imagem. |
| `symbol` | Lista de simbolos encontrados na imagem. |
| `data` | Conteudo decodificado do QR Code. |
| `error` | Indica erro na leitura, caso exista. |

Quando o campo `data` contem um link iniciado por `http://` ou `https://`, o aplicativo libera um botao para abrir o cardapio no navegador.

## 7. Tecnologias Utilizadas

O aplicativo foi desenvolvido em **Kotlin** para Android.

As principais tecnologias utilizadas no consumo da API foram:

- **Retrofit:** usado para declarar os endpoints e realizar chamadas HTTP.
- **OkHttp:** usado como cliente HTTP para envio e recebimento das requisicoes.
- **Gson:** usado para converter respostas JSON em objetos Kotlin.
- **Kotlin Coroutines:** usadas para executar chamadas de rede em segundo plano.
- **ViewBinding:** usado para acessar os componentes da interface de forma segura.

## 8. Permissoes Utilizadas

O aplicativo utiliza as seguintes permissoes no Android:

| Permissao | Finalidade |
| --- | --- |
| `INTERNET` | Permite acessar a API QR Server pela internet. |
| `CAMERA` | Permite capturar a foto do QR Code que sera enviado para leitura. |

Tambem foi utilizado um `FileProvider`, necessario para armazenar temporariamente a foto capturada pela camera e disponibiliza-la de forma segura para o aplicativo.

## 9. Fluxo de Funcionamento do Aplicativo

### Gerar QR Code

1. O usuario informa o link do cardapio.
2. O usuario informa o numero da mesa.
3. O aplicativo monta a URL final com o parametro da mesa.
4. O app envia a URL para a API QR Server.
5. A API retorna a imagem PNG do QR Code.
6. O aplicativo exibe o QR Code gerado na tela.

### Ler QR Code

1. O usuario aciona a camera pelo aplicativo.
2. O usuario tira uma foto do QR Code.
3. O aplicativo comprime a imagem capturada.
4. A imagem e enviada para a API QR Server.
5. A API retorna o conteudo decodificado em JSON.
6. O aplicativo exibe o conteudo lido.
7. Caso seja um link, o app permite abrir o cardapio no navegador.

## 10. Limitacoes da API e do Aplicativo

Apesar de atender bem ao objetivo do projeto, a solucao possui algumas limitacoes:

- A leitura do QR Code depende da qualidade da imagem capturada.
- E necessario acesso a internet para gerar e ler os QR Codes.
- A API publica pode ter instabilidades ou limites de uso fora do controle do aplicativo.
- A leitura pode falhar se a foto estiver muito escura, tremida ou com o QR Code cortado.

## 11. Conclusao

O projeto atende ao objetivo proposto, pois utiliza uma API externa para gerar e ler QR Codes. A API QR Server / goqr.me foi aplicada nas duas funcionalidades principais do aplicativo: geracao de QR Code a partir de um link e leitura de QR Code a partir de uma imagem capturada pela camera.

A aplicacao desenvolvida possui relevancia pratica, pois simula uma solucao utilizada em restaurantes que adotam cardapios digitais por QR Code. Dessa forma, o projeto demonstra o consumo de API em um contexto realista e util.
