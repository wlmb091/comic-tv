# Validação — Comic TV 1.0.0

24/10/2026 · versionCode 38 · minSdk 26 · targetSdk 36 · base funcional 0.12.5.

## Estado da versão

Primeira versão estável autorizada pelo usuário após confirmar os últimos ajustes na Box: “ja foram confirmados,até entao tudo funcionando”. Aceitou tratar problemas futuros nas próximas atualizações. A confirmação refere-se à base 0.12.5 entregue; o novo pacote 1.0.0 foi gerado nesta rodada. Não há nova funcionalidade nesta promoção.

## Verificações automatizadas

- work/release-100-final.log: BUILD SUCCESSFUL em 2m7s, 133 tarefas executadas com --rerun-tasks. Core/JVM, mihon/JVM, app/JVM, debug, compilação AndroidTest e lintDebug passaram.
- 215 casos JVM: 214 aprovados, 1 ignorado (permissão de symlink no Windows), sem falhas/erros. Core118, mihon10, app87.
- work/release-100-production.log: assembleRelease e lintRelease aprovados em 2m2s, 97 tarefas. APK release sem minificação, alinhado e assinado externamente com o certificado das atualizações anteriores. Nenhum ramo de comportamento por BuildConfig.DEBUG foi encontrado nas fontes app/mihon.
- Assinatura v2 verificada, certificado SHA256 5f60801ffa010bb913a59857e851d5eed2d4b63bc79de38747f2db15ae0395ae, igual aos APKs anteriores. Alinhamento ZIP/16KiB validado após assinatura.
- Manifesto empacotado confirma com.pelican.comictv, versão1.0.0/code38, minSdk26 e launcher Leanback. Sem application-debuggable. Inclui armeabi-v7a, arm64-v8a, x86 e x86_64.
- ABI de extensões validada no DEX do APK final: GeneratedSerializer.typeParametersSerializers tem implementação padrão executável. Dez entradas de licenças nos assets.
- SQL de histórico e migrações de organização1→3/2→3 passaram, preservando edições/progresso, restrições e rollback.
- Revisão independente não encontrou bloqueio de promoção; pendência documental de aceitação antiga foi corrigida.

## Validação manual e limites

Uso na MiTV-AFKR0/Android TV11 confirmado pelo usuário, incluindo backup/restauração, offline após limpar caches, organização/persistência, downloads e ajustes finais. Detalhes em docs/validation/2026-10-24-mi-box-manual.md.

Testes Android/Compose foram compilados, não executados por ADB. Não foram explicitamente relatados SAF ou simulações isoladas de falha/interrupção. Não se afirma cobertura de todos os aparelhos ou desempenho medido no hardware. Não repetir testes destrutivos nos dados reais.

## Artefato

Comic-TV-1.0.0.apk · release · 14.010.510 bytes.
SHA256: 8AE5ECA2A89FD51657CF715134439D021651DE44B5C56F5B8CE264535F899108.

Instalar como atualização sobre a versão existente, sem desinstalar, mantendo o USB de armazenamento conectado. Notas em docs/releases/1.0.0.md. Próximas correções de uso diário seguem em versões1.0.x.
