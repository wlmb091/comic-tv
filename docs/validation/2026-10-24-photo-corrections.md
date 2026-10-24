# Correções por fotos — Comic TV 0.12.5

O usuário encerrou o envio das fotos e autorizou reunir as correções na 0.12.5/code37. A validação visual na Box permanece pendente.

## Pedidos implementados

- Detalhes online: Escolher capítulo / edição abaixo da ação de leitura. Reutiliza a lista existente; navegação de origem details volta aos detalhes, origem reader mantém retorno à leitura. Capa substitui prévia de leitura na origem details; falha da fonte não impede retorno. Selecionar capítulo usa o fluxo existente de abertura. Não consulta páginas nem altera progresso só para listar.
- Leitor: ícone do modo Normal com uma página, Galeria com três páginas (central maior) e Livro aberto. Mapeamento exaustivo por modo ativo; rótulos e ação de alternar preservados.
- Aparência: temas lado a lado em grade adaptativa, cada célula com amostra e nome, seções explicativas ocupando a largura completa.
- Tamanho dos textos: controle horizontal focável, esquerda reduz/direita aumenta,85–130% em passos5%. Acima/abaixo continuam disponíveis para sair do controle. Semântica de faixa e ajuste para acessibilidade.
- Preferências antigas100/115/130 permanecem legíveis. Campo opcional textPercent guarda o valor exato nas preferências e no backup; esquema continua verificando tipo/range/passo. Backups antigos são aceitos; backups com o novo campo exigem aplicativo atualizado.
- Mudanças de aparência são aplicadas em sequência usando o estado mais recente, sem descartar setas por um flag de salvamento ocupado.

## Verificação

Leitor: compileDebugKotlin passou em work/reader-mode-icon.log. Aparência: RED de compilação por APIs ausentes em work/appearance-scale-red.log, depois build/testes/lint aprovados em work/appearance-scale-green.log. Core118casos (117 aprovados,1 symlink ignorado no Windows), app87 aprovados, sem falhas/erros. APK debug e APK de testes nativos compilados para verificação local, sem novo artefato entregue.

Teste nativo do controle preparado para limites, mudança de escala e saída com seta para baixo; compilado, não executado. ADB indisponível; grade real, foco e fluidez na Box continuam pendentes. Revisão independente não encontrou bloqueios. Conferir fisicamente setas mantidas pressionadas, persistência assíncrona, reflow da grade a130% e saída do controle com Acima/Abaixo.

Acréscimo de capítulos: compilação RED por backLabel ausente em work/details-chapters-red.log; teste nativo de retorno sem abrir leitor mesmo com falha da fonte preparado. Revisão independente de rotas/origem/preview não encontrou bloqueios; hardware Back e navegação real continuam pendentes de execução física.

## Pacote final0.12.5

Verificações finais em work/photo-corrections-0125-chapters.log:214 JVM aprovados/1 ignorado,build debug/AndroidTest/lint aprovados. APK entregue e assinado com o certificado anterior; ver VALIDACAO.md para hash e limites de aceitação física. Esta evidência substitui o estado preliminar sem artefato descrito acima.
