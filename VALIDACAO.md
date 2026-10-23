# Validação — Comic TV 0.7.4

23/10/2026 · versionCode 13 · minSdk 26 (Android 8) · compileSdk/targetSdk 36.

## Causa comprovada

O aparelho reportou AbstractMethodError em GeneratedSerializer.typeParametersSerializers ao explorar HQ Now. A biblioteca kotlinx-serialization-core-jvm 1.9.0 já contém implementação padrão desse método (verificada com javap). Porém, no DEX do Comic TV 0.7.3, o método tinha flags PUBLIC ABSTRACT e nenhum corpo. O D8 reescrevia interfaces para o piso Android23 do host. A extensão compilada separadamente para Android26 depende da implementação padrão e não recebe as pontes sintéticas criadas durante a compilação do host.

## Correção e impacto

App e módulo mihon passam a minSdk26, preservando as implementações padrão no DEX. Mantidas as versões de serialização e rede: atualizar apenas a biblioteca não corrigiria a transformação feita no empacotamento. Android8/API26 foi escolhido como piso compatível com o APK HQ Now inspecionado. Android6/7 deixam de ser suportados a partir desta versão.

README e documentação do runtime atualizados. Adicionado tools/check-extension-abi.mjs, que inspeciona o DEX final, identifica a interface e verifica assinatura, visibilidade pública, método não estático/não abstrato e corpo executável. O teste é específico para o método que falhou; não certifica compatibilidade completa com todas as extensões.

## Verificação

- Verificador DEX reproduziu a falha em 0.7.3 e passou em 0.7.4.
- 126 testes JVM passaram sem falhas ou erros.
- assembleDebug, mihon:assembleDebugAndroidTest e lintDebug passaram.
- Comparação de tipos usados pelo HQ Now com APK final: nenhuma classe não pertencente à plataforma ausente.
- Assinatura v2 válida; aapt2 confirma code13, versão0.7.4, minSdk26.
- SHA-256 APK: 43552E8427A887C4FE352061195C7FFFB5241B254DB8C79C9550EBFF73937A3A.
- Revisão independente sem bloqueios; implementação no DEX final confirmada após a revisão.

## Limite da validação

Sem aparelho conectado para executar a extensão. A correção do método no APK foi comprovada; abrir catálogo, sagas e páginas na TV ainda requer teste no aparelho. Atualizar normalmente, abrir Fontes, Atualizar instaladas e HQ Now — Explorar.
