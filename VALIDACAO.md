# Validação — Comic TV 0.7.2

23/10/2026 · versionCode 11 · minSdk 23 · targetSdk 36.

## Diagnóstico de extensões

A lista de extensões instaladas agora mantém o erro por pacote, mostra nome/pacote e causa técnica, incluindo causas encapsuladas por InvocationTargetException. Mensagens são limitadas a 350 caracteres e normalizadas para uma linha; cadeias cíclicas são limitadas por identidade e profundidade. A opção de tentar novamente respeita o estado ocupado. Uma atualização bem-sucedida substitui os erros anteriores.

Esta versão melhora o diagnóstico da falha relatada na TV; não afirma corrigir o carregamento da extensão. Não há aparelho conectado para obter o Logcat ou reproduzir o problema. A inspeção estática do APK HQ Now 1.6.0 não confirmou a causa, e o usuário ainda não identificou nominalmente a extensão afetada.

## Verificação

- 123 testes JVM passaram, incluindo quatro novos testes de diagnóstico.
- assembleDebug e lintDebug concluídos com sucesso.
- APK verificado por apksigner, assinaturas v1/v2 válidas.
- SHA-256: 3840CF68953BFD663920A5EDF2016D736A45D82F025B7F534D04AE53AF954FED.
- Testes em Android/TV não executados nesta etapa.

## Teste na TV

Atualizar o aplicativo, abrir Fontes e Atualizar instaladas. Descer até Extensões no aparelho e registrar o texto “Falha ao abrir”. A instalação do APK da extensão não implica que o seu catálogo já tenha sido carregado pelo host.
