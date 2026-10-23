# Validação — Comic TV 0.7.3

23/10/2026 · versionCode 12 · minSdk 23 · compileSdk/targetSdk 36.

## Causa e correção

A foto do aparelho revelou ausência de okhttp3.CompressionInterceptor em uma extensão instalada (OneMangaCo); o HQ Now também falhava com NoClassDefFoundError ao explorar. A inspeção estática do APK HQ Now 1.6.0 mostrou referências a CompressionInterceptor, DecompressionAlgorithm, Gzip, Brotli, BrotliInterceptor e Zstd durante a construção de seu cliente de rede. Essas classes não existiam no APK Comic TV 0.7.2.

OkHttp e DNS over HTTPS foram atualizados de 4.12.0 para 5.3.2 nos módulos app/mihon. Foram incluídos okhttp-brotli e okhttp-zstd na mesma versão. A mensagem de incompatibilidade durante operações agora preserva a causa técnica. Metadados OSGi de JVM duplicados são excluídos do empacotamento Android; recursos de execução e bibliotecas nativas são preservados.

Referência primária da API de compressão: https://github.com/lysine-dev/okhttp/blob/main/CHANGELOG.md#version-520

## Verificação

- Regressão reproduzida antes da atualização: ClassNotFoundException no teste das APIs de compressão.
- 126 testes JVM passaram, sem falhas ou erros: 82 core, 10 mihon, 34 app.
- Novos testes: resolução e construção por reflexão das APIs usadas pela extensão; respostas HTTP Gzip/Brotli servidas por loopback e descomprimidas pelo interceptor real.
- assembleDebug, mihon:assembleDebugAndroidTest e lintDebug passaram.
- Comparação de tipos DEX do HQ Now com o APK final: nenhuma referência não pertencente à plataforma ficou ausente. A anotação dalvik.annotation.Signature pertence ao Android.
- libzstd-kmp.so presente para arm64-v8a, armeabi-v7a, x86 e x86_64.
- apksigner: assinaturas v1/v2 válidas. aapt2 confirma versão 0.7.3/code12/minSdk23.
- SHA-256 APK: 88246898628B505FA79A02367C32E674311613EB40209E37AE0159CF02B47F20.
- Revisão independente sem defeitos materiais; condição de empacotamento nativo conferida no APK final.

## Limites e teste na TV

Nenhum aparelho conectado via ADB. A abertura real do catálogo HQ Now e o teste instrumentado de decodificação Zstandard ainda precisam ser executados em Android. O teste Zstandard usa JNI Android e foi colocado em androidTest; não se deve interpretar testes JVM como execução dessa biblioteca nativa no aparelho.

Atualize o aplicativo por cima da versão anterior, abra Fontes, pressione Atualizar instaladas e escolha HQ Now — Explorar. As extensões instaladas e os repositórios são preservados pela atualização normal.
