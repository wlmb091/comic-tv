# Validação — Comic TV 0.4.0

23/10/2026. versionCode 6. Miniaturas na barra e leitura automática, conforme solicitado pelo usuário. Intervalo inicial de 10 segundos confirmado por ele.

APK assinado com a mesma chave debug; v1/v2 verificadas. minSdk 23, targetSdk 36. SHA-256: `87B253DCF86728CF220C15F7296DFE0A093FA00C12543D9A591B554B7B1A92D8`.

## Miniaturas

A barra mostra a posição selecionada destacada e até duas posições anteriores e posteriores. Direção RTL preservada. O ViewModel espera 180 ms de estabilização, prioriza a selecionada e cancela/descarta resultados antigos. Falha de miniatura mostra “Sem prévia”, sem alterar o erro ou o progresso da leitura.

Cache JPEG separado de 32 MiB, com miniaturas de até 240×360 e proporção preservada para imagens; EPUB de texto recebe cartão com trecho. Uma página original é extraída por vez para temporário, convertido e removido, mantendo os limites dos leitores. Esse caminho não altera protectedPage nem substitui a página principal. Não gera o livro inteiro antecipadamente; RAR sólido pode exigir replay interno de entradas anteriores.

## Leitura automática

Nas ferramentas há Iniciar leitura automática e Intervalo. Opções: 5,10,20,30,60 segundos, iniciando em 10. Imagens: o timer aguarda o sucesso de exibição pelo Coil. Falha de carregamento ou decodificação pausa e mostra erro; callbacks de outro arquivo são ignorados.

Texto EPUB: rolagem suave até o fim da seção, usando o intervalo por altura visível. A última porção permanece brevemente antes da próxima seção. Se o texto cabe na tela, aguarda um intervalo completo. A seção seguinte é aberta pelo mesmo fluxo de progresso do leitor; callbacks antigos não saltam seções. A gravação da posição de rolagem é amostrada em 500 ms e feita ao sair da seção.

Setas, ferramentas/barra, Voltar, saída, erro e perda de primeiro plano pausam a automação. Ela não reinicia sozinha ao retornar. No fim da última imagem ou após concluir o último texto, para. Trocas automáticas seguem a ordem do livro também em RTL.

## Evidências

`core:test`, `app:testDebugUnitTest`, `app:assembleDebug`, `app:assembleDebugAndroidTest`, `app:lintDebug`: BUILD SUCCESSFUL.

60 testes JVM, zero falhas: 50 anteriores e 10 novos. Casos novos: debounce e limites de prévias, prioridade da selecionada, máximo de cinco, nenhum progresso/carga principal, cancelamento tardio e erro isolado; intervalo inicial e ajustável, RTL, espera por carregamento e por exibição, pausa manual/erro/fim, texto aguardando rolagem, callback antigo e falha de imagem sem salto.

Android Lint: zero erros, 27 avisos, mesma contagem anterior. Revisão independente encontrou a necessidade de esperar a renderização efetiva antes do timer e comunicar erros do Coil ao ViewModel. Correção e teste adicionados; reavaliação sem problemas Important/Critical.

Sem dispositivo em `adb devices`. APK de testes instrumentados compilado, sem execução. Decodificação nativa das miniaturas, animação de rolagem, foco e comportamento real em segundo plano ainda precisam do reteste na TV. Os testes JVM verificam os estados e a coordenação; não comprovam a renderização Android.

## Reteste na TV

1. Instalar 0.4.0 sobre a versão anterior e abrir OK → Escolher página/seção. Mover e aguardar brevemente as miniaturas. Verificar primeira/última posição, RTL e cancelamento sem alterar progresso.
2. OK → Iniciar leitura automática: esperar dez segundos após a imagem aparecer. Usar uma seta ou OK para pausar.
3. Nas ferramentas, alternar Intervalo e reiniciar. Confirmar que uma página com erro não é pulada.
4. Em EPUB de texto, verificar rolagem suave, próxima seção e parada na última. Interromper com D-pad e conferir retomada.
5. Sair para a tela inicial da TV e voltar: automático deve continuar pausado.

Fontes online, HQ Now e sagas continuam sendo incrementos seguintes. Nenhuma mudança no importador de formatos nesta versão.
