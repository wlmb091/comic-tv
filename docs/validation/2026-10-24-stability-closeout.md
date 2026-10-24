# Fechamento técnico da rodada — Comic TV 0.12.4

## Estado

Versão atual 0.12.4/code36. Esta rodada não acrescenta funcionalidades nem gera outra versão do aplicativo. O fechamento técnico não equivale a aprovação visual ou de desempenho no aparelho. Não promover a 1.0 estável sem essa aceitação.

## Artefato entregue

Comic-TV-0.12.4-debug.apk, certificado de atualização preservado, assinatura v2 verificada.
SHA256: DD31EE8C538863576512BFC26DE8C0239B91F3F9B367B3F48B24C7F90531ABDD.

O manifesto empacotado declara minSdk26, launcher Leanback e touchscreen não obrigatório. Bibliotecas nativas incluem armeabi-v7a e arm64-v8a, além de x86/x86_64. Esses requisitos são compatíveis com a configuração Android TV11 informada pelo usuário, sem provar todos os comportamentos de execução.

## Evidências automatizadas

- ABI das extensões verificada no DEX extraído do APK entregue: GeneratedSerializer.typeParametersSerializers tem implementação padrão executável.
- SQL de histórico preserva edições/progresso e remoção reversível; migrações de organização1→3 e2→3 passaram com restrições, rollback e cascata.
- Bateria completa com --rerun-tasks em work/final-stability-0124.log: BUILD SUCCESSFUL em2m1s,133 tarefas executadas. 213 casos JVM,212 aprovados,1 ignorado por permissão de symlink no Windows; sem falhas/erros. Core117, mihon10, app86. APK debug, APK de testes nativos e lintDebug concluídos.
- ADB sem dispositivo conectado. Testes Compose/Android são compilados, não executados nesta máquina.

## Aceitação relatada e limites

O usuário relatou sucesso em backup/restauração, leitura offline após limpeza dos dois caches, favoritos/coleções com reabertura e downloads com pausa/retomada/remoção preservando progresso. Detalhes em 2026-10-24-mi-box-manual.md.

Ainda falta confirmar visualmente os ajustes0.12.2–0.12.4: texto115%/130%, todos os temas/alto contraste, proporções e D-pad da lateral/cabeçalho. Também não foram executados SAF e falhas/interrupções nativas isoladas de restauração. Não testar falhas destrutivas nos dados reais.

## Próxima decisão

Conferir a versão atual na Box com um título longo, texto130% e alternância de temas. Corrigir apenas problemas observados; depois avaliar promoção a1.0. Instalar atualizações sem desinstalar e manter o USB de armazenamento conectado.

## Adiado por escopo

IA/recomendações, detecção automática de painéis, shader/Lanczos personalizados, nuvem/backup automático e downloads em serviço contínuo não integram esta rodada.

## Proveniência da recompilação

O APK refeito nesta rodada tem SHA25603C82B6E2267CA55415AE2259642D7B6BB0842703DF6B1EB71670467C02926B8 e não substituiu o artefato entregue. Comparação dos conteúdos ZIP encontrou diferença somente em classes21.dex; não se afirma build idêntico byte a byte. A assinatura, os requisitos do manifesto e a ABI citados acima foram verificados diretamente no APK entregue, de SHA256DD31EE8C538863576512BFC26DE8C0239B91F3F9B367B3F48B24C7F90531ABDD.
