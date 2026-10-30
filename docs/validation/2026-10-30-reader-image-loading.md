# Leitor: carregamento de imagem e giro90° — 30/10/2026

Pacote aberto, semAPK/push/CI/alteração deversão. Concluído antes da próxima tarefa de manter zoom, conforme preferência sequencial do usuário.

## Investigação

Vídeo localleitura90graus.mp4,23.96s/1024x576, analisado por quadros sem áudio (extraçãoFFmpeg com-an). Mostra filtroHQantigaForte e ativação de90°, seguida de mudanças de página. Não há arquivo original da edição nem profiler/ADB daBox; não atribuir tempos quantitativos aCPU/GPU/rede a partir do vídeo.

No código, cada bloco reconstruíaBitmapRegionDecoder e relia dimensões; filtro executado em cada bloco, com publicação de estadoCanvas após cada um. Também havia antecipação de todos os blocos de duas páginas futuras pelo mesmo mutex. O giro altera viewport lógico e pode exigir amostragem mais fina/mais pixels. Arquivo importado elimina a espera da internet, mas não a decodificação/filtro/renderização local. Essas são fontes de trabalho repetido confirmadas em código, não medição causal exclusiva do aparelho.

## Correção

- Página inteira visível pode ser decodificada numa única região quando a amostragem original cabe no limite. Mesma sampleSizeForScale, sem forçar redução de qualidade. Limite<=2MP e2048pxporaresta (imagensmuitocompridascontinuamemblocos) e adaptação ao orçamentoRAM existente: budget/8 paraOriginal,budget/16 para filtros com buffersARGB. Grandes páginas e zoom continuam em blocos originais.
- Cache mantém no máximo2sessões de dimensões/decodificador reutilizáveis. Acesso,evicção,clear/close sob mesmo mutex; descarte espera operação ativa terminar. Bitmap publicado não é reciclado; cancelamento libera bitmap ainda não publicado.
- Uma prévia completa continua disponível durante preparação; blocos visíveis novos são publicados em conjunto, evitando uma atualizaçãoCanvas por bloco decodificado.
- Trabalho especulativo usa tryLock e cede para pedidos visíveis. VMcontinua preparando os arquivos das próximas2páginas; pré-decodificação apenas da prévia512px. Até2vizinhos realmente fora da área visível, semredecodificar blocos internos quando a página já cabe inteira.
- Filtros/força,original,rotação,controles,ajuste/progresso não modificados nesta etapa.

## Verificações

- Teste inicial falhou pelas funções ausentes(build/reader-render-red.log).
- TestesReaderRendering: página inteira/amostragem em1280x720 e720x1280,grandeimagem/zoom mantêmblocos,orçamentoRAMmenor usa mesmosblocos,semantecipaçãointerna redundante,reutilização/evicção/liberação dedecodificador.
- SuitesZoomTiles/ByteBudgetCache/ImageEnhancement e testesJVMReaderdoapp passaram; AndroidTest compilado,lint0erros/70avisos.
- BUILD SUCCESSFUL3m34(build/reader-render-check.log). Ajuste final de vizinhos: BUILD SUCCESSFUL1m58(build/reader-render-final.log),testescorefocados ecompilaçãoAndroid.
- Revisão independente semP1/P2. Semtempo/FPS/ganho medido naBox.

## Conferência física pendente

ApósAPK autorizado: mesma edição/filtroForte,modoNormal0°/90°,passar páginas,zoom/pan,compararOriginal,voltar dasferramentas;Galeria/Livro eabrir/fecharleitor. Observar tempo até nitidez completa e memória semreduzir qualidade. Nenhum teste físico realizado por esta etapa.

## Checagem final do lote

Guardadearesta adicionado para imagens muito compridas: teste falhou antes da proteção(build/reader-frame-bound-red.log), passou depois. `build/reader-final-core.log`: BUILD SUCCESSFUL34s,24testescoreincluindoReaderState/Rendering/ZoomTiles/ByteBudgetCache/ImageEnhancement,0falhas. SuitesReaderdoapp41testes/0falhas na etapa dezoom(build/reader-zoom-final.log,2m10),AndroidTest compilado. O filtro e a resolução de amostragem continuam os mesmos; melhora de tempo não foi medida naBox.
