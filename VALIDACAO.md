# Validação — Comic TV 0.12.4

24/10/2026 · versionCode 36 · minSdk 26 · base 0.12.3.

## Escopo

Revisão de acessibilidade por inspeção das restrições Compose: cabeçalho antes concentrava marca e três ações numa Row; lateral usava largura fixa e botões de altura fixa sem rolagem. LibraryHeader agora usa LocalDensity.fontScale e ações em FlowRow, separando marca/ações quando o espaço é menor. A lateral expandida cresce até 320 dp, permite rótulos em mais de uma linha e rolagem vertical. Lateral recolhida64 dp, destinos, callbacks, foco inicial e persistência preservados. Não altera detalhes/resumo sem evidência de novo defeito.

## Verificação

Dois testes nativos preparados: cabeçalho a130% em560dp, e navegação por todas as entradas laterais em280dp de altura. Compilação RED antes do novo componente: LibraryHeader ausente em work/large-text-red.log. São testes de dispositivo; não afirmar execução sem aparelho conectado. AssembleDebug, assembleDebugAndroidTest, 86 testes JVM do aplicativo e lintDebug passaram em work/large-text-0124.log. Revisão independente não encontrou bloqueios. Assinatura verificada, mesmo certificado das versões anteriores; metadados0.12.4/code36/minSdk26 confirmados.

## Aceitação física pendente

Conferir texto115%/130%, D-pad, retorno entre telas, alto contraste e temas na Box. As proporções da tela principal e escolhas das0.12.2/0.12.3 também aguardam conferência visual. Manter USB e instalar como atualização sem desinstalar. Relatos dos fluxos anteriores em docs/validation/2026-10-24-mi-box-manual.md.

SHA256 APK: DD31EE8C538863576512BFC26DE8C0239B91F3F9B367B3F48B24C7F90531ABDD
