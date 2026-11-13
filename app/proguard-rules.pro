# Regras do R8/ProGuard (usadas quando isMinifyEnabled = true no build de release).

# Gson preenche os modelos por reflexão usando o NOME dos campos.
# Sem esta regra, o R8 renomeia/remove os campos de QrReadResponse/QrSymbol
# e a leitura do QR Code passa a retornar sempre vazio.
-keep class com.example.qrcodeapp.model.** { *; }

# Retrofit/Gson precisam dos tipos genéricos (ex.: Response<List<QrReadResponse>>)
# e das anotações em tempo de execução.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# A interface da API é acessada só via proxy do Retrofit
-keep,allowobfuscation interface com.example.qrcodeapp.api.QrApiService
