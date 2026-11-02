# Deslize suave de página e zoom espelhado celular↔TV (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.19 (código 93), AUTORIZADO PELO USUÁRIO EM 01/11/2026; Box e iPhone PENDENTES.** Na TV só a página atual se desloca no arrasto; a vizinha não é desenhada. Detalhes em `docs/validation/1.4.19.md`.

## Vídeos de referência (Downloads)
- `zoom-celular-tv-mesmotempo.mp4` (5,7 s, gravação de tela do iPhone): leitor do celular no tema roxo; a imagem dá zoom com dois dedos, fluido e seguindo os dedos, com a página ampliada preenchendo a tela. A TV não aparece no vídeo, só o celular.
- `deslizar-da-pagina.mp4` (4,9 s, gravação de tela): leitor em tela cheia, sem barras; ao arrastar, a página atual sai e a vizinha entra lado a lado, acompanhando o dedo (carrossel de páginas), indo e voltando (capa → índice → página seguinte e volta).
- Vídeos analisados em 01/11/2026 por frames (opencv instalado via pip a pedido do usuário).

## Pergunta
1. Dá para passar de página no celular com a imagem acompanhando o dedo (a página atual sai e a próxima entra, vista durante o arrasto) com a suavidade dos vídeos?
2. Dá para o zoom no celular acontecer ao mesmo tempo na TV, quase um espelhamento de tela?

## Resposta técnica (a partir do código atual, `companion/index.html`)
- Deslize: hoje é discreto (`swipeTurn` só decide a troca ao soltar o dedo, sem animação de arrasto). Fazer a página seguir o dedo é viável no leitor web do celular (translate pelo `touchmove` + animação ao soltar). Esforço médio, sem mudar o app da TV.
- Zoom: já existe `reader-pinch`/`reader-zoom` enviados do celular à TV, mas em lotes por HTTP (fila) e o estado volta por leitura a cada 0,3 s; não é streaming. Dá uma sincronia próxima, com atraso perceptível; reduzir o lote e enviar mais seguido melhora, e SSE/WebSocket deixaria mais fluido. Não é espelhamento de vídeo (a tela da TV não é transmitida).
- Nada disso foi medido na Box/iPhone; não prometer o mesmo nível dos vídeos.

## Decisões do usuário (01/11/2026)
- Incluir **deslize suave** e **zoom sincronizado** no lote.
- O deslize suave (página seguindo o dedo, vizinha entrando lado a lado) vale **no leitor do celular**.
- Na TV, o zoom acompanha **enquanto os dedos se mexem**, como **espelhamento de tela**. A TV também deve refletir **a passagem de página do jeito do deslize**, ou seja, o arrasto/troca de página aparece na TV ao vivo, não só o zoom.
- Implicação técnica: exige estado contínuo (posição do arrasto, zoom e deslocamento) do celular para a TV em alta frequência e animação equivalente de carrossel no leitor da TV (hoje só zoom/pan/página discretos). Conexão contínua (SSE/WebSocket) e testes na Box/iPhone, que seguem pendentes; não prometer sincronia perfeita.
