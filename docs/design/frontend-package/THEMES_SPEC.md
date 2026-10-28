# Comic TV — Especificação dos 5 temas (Home)

Referência visual: `/docs/refs/<tema>/target.png` (exportar cada quadro do canvas).
Canvas de referência: 1280×720 px = tela 1080p da Mi Box (960×540 dp).
**Conversão: dp = px do mockup × 0.75.** Posições abaixo estão em px do mockup.

## Regras gerais

- Os mockups são referência de LAYOUT, cor, tipografia e proporção. A arte do hero
  (cidade, personagem) é placeholder: NÃO usar como asset.
- Fontes pequenas do mockup (13–16 px) ficam ilegíveis a 3 m: no app use **mínimo
  14sp** para corpo e proporcionalmente maior nos títulos, mantendo a hierarquia.
- Área segura: 48 dp laterais, 27 dp vertical.
- Se o app não tiver o dado (sinopse, "Edição", Favoritos, Histórico), omitir o elemento.
- Arquitetura: um `ThemeSpec` por tema (navLayout, heroVariant, continueCardStyle,
  collectionCardStyle, focusStyle, colors, shapes, typography, motion).
  Sem `if (tema)` espalhado nas telas.
- Imagens: backdrop em ≥1920×1080; se só houver capa vertical, recorte + blur +
  degradê (nunca esticar). Blur em bitmap pré-processado e em cache (sem RenderEffect,
  pois a Mi Box pode rodar Android 9).
- Foco: animação 150–300 ms. BACK sobe um nível (fecha painel → Home → sai).

---

## 1. Comic TV (âmbar)

- navLayout = LEFT_RAIL; heroVariant = POSTER_LEFT
- Cores: fundo `#0b0b0d`, trilho `#101012`, destaque `#f5b82e`, card `#17171a`
- Fonte: Inter (600/800)
- Trilho: x0, largura 64, ícones 22 px empilhados (y≈112, passo 54), ativo em âmbar
- Topo: logo "COMIC TV" (TV em âmbar) x118 y14, 38 px/800. Abas Todas/Favoritos/Coleções
  em y66 (16 px), sublinhado âmbar 2 px na ativa. Direita: Buscar | Histórico
- Hero: capa em destaque x118 y106 196×226, borda 2 px âmbar, raio 4. Ao lado
  (x334): título 44/800, "Edição 02 • Página X de Y" 16, progresso 300×5 âmbar,
  sinopse 2 linhas 16, botões y266: primário 238×48 (play, borda 2 px âmbar,
  fundo preto 60%), secundário 190×48
- Scrim: horizontal `#0b0b0d` 12% → transparente 62%; vertical de baixo 4% → 45%
- "Continuar lendo" (label 20/800, y342): 3 cards 330×68 em y370, passo 352,
  miniatura 110 px à esquerda, título 15/600, página 13, barra 196×5 âmbar
- "Sua coleção" (y450): 5 quadrados 194×194 em y478, passo 214
- Foco: borda âmbar 2 px

## 2. Flix (vermelho)

- navLayout = LEFT_SIDEBAR (220 px); heroVariant = FULLBLEED_TEXT
- Cores: fundo `#000`, destaque `#e50914`
- Fontes: Bebas Neue (títulos), Inter
- Sidebar: logo x40 y28 (34/800); itens ícone 22 + texto 18/600, y102 passo 56;
  ativo com sublinhado vermelho 72×3. Buscar em pílula 120×38 no topo direito
- Hero: título x248 y84, Bebas 76 (2 linhas, line-height .92); "Página X de Y" 15;
  progresso 280×5 vermelho; sinopse 14; botão primário vermelho 206×48 com play
  (sem borda), secundário 150×48 escuro
- Scrim: horizontal `#000` 14% → transparente 62%; vertical 3% → 40%
- "Continuar lendo" (y414): 4 cards 220×138 em y444, passo 236, só arte + título
  Bebas 16. Foco: borda 3 px vermelha + glow `0 0 22px rgba(229,9,20,.6)`
- "Sua coleção" (y606): pôsteres 180×110 em y634, passo 200 (cortados na base)

## 3. Cinema (cinza, sem cor de destaque)

- navLayout = RIGHT_DRAWER; heroVariant = FULLBLEED_TEXT
- Cores: fundo `#0a0a0c`, painel `rgba(22,22,28,.72)` + blur 18, texto branco/cinza
- Fontes: Cinzel 600 (títulos), Inter
- Painel: x953 y0 327×490, borda esquerda 1 px branca 8%. Buscar: caixa 276×52 com
  borda 2 px branca (foco); 6 itens ícone 22 + texto 17/600 (y100 passo 62);
  check no item ativo. Abre com D-pad direita; fecha com esquerda/BACK
- Hero: título x52 y216, Cinzel 58 (2 linhas); progresso branco 320×5;
  botões translúcidos cinza (184×48 e 130×48)
- "Continuar lendo" (y468): 4 cards de vidro 276×128 em y496, passo 296,
  miniatura 64×108, título Cinzel 16, página 13, barra branca 150×5
- Foco: contorno branco

## 4. Max (roxo)

- navLayout = BOTTOM_DOCK; heroVariant = FULLBLEED_TEXT
- Cores: fundo `#0b0818`, destaque `#9b6dff`
- Fontes: Bebas Neue, Inter
- Logo x50 y32 (26/800). Título x50 y190, Bebas 60. Progresso 340×5 violeta
- Botões y318: primário 200×48 com borda 2 px violeta e fundo `rgba(40,24,80,.9)`,
  secundário 150×48 translúcido
- Scrim: horizontal `rgba(11,8,24,.85)` 8% → .1 em 58%; vertical 4% → 48%
- "Continuar lendo" (y400): 4 cards 270×128 em y430, passo 288, fundo
  `rgba(30,20,64,.72)`, borda `rgba(155,109,255,.4)`, raio 12, miniatura 70×108
- Dock: x104 y596 1070×82, raio 14, fundo `rgba(30,20,64,.82)` + blur 14, borda
  violeta 45%; 6 itens ícone 22 + texto 13/600; ativo com pílula
  `rgba(155,109,255,.4)` raio 10. A dock reserva espaço: nada fica sob ela
- Foco: borda violeta + escala 1.05

## 5. Prime (azul)

- navLayout = TOP_BAR; heroVariant = FULLBLEED_CAROUSEL
- Cores: fundo `#060b1a`, destaque ciano `#2ad4f5`
- Fontes: Barlow Condensed 800 (títulos), Inter
- Barra: ativo em pílula 118×38 com borda 2 px ciano; demais abas 16/600; logo à
  direita (30/800); busca em campo 134×32 raio 16, escuro
- Hero: título x58 y190, Barlow 72 (2 linhas); progresso 320×5 ciano; sinopse 14;
  botão primário **branco** 186×48 com play, secundário 140×48
- Carrossel à direita: capas altas — focada 184×298 em x612 y112 (borda 3 px
  ciano + glow `0 0 24px rgba(42,212,245,.6)`), vizinhas 172×280 em y122 com
  opacidade .85; dots 8 px abaixo (ativo ciano)
- "Continuar lendo" (y468): cards 16:9 270×118 em y494, passo 282, título sobre a
  arte (Barlow 17), barra ciano no rodapé
- "Sua coleção" (y626): miniaturas 124×70 em y652, passo 136 (cortadas na base)

---

## Ordem de implementação

1. Refator para ThemeSpec/ThemedScaffold (sem mudar visual).
2. Max → Prime → Flix → Comic TV → Cinema.
3. Cada tema: rodar no emulador Android TV 1080p, salvar prints em
   `/docs/result/<tema>/` e listar diferenças em relação ao `target.png`.
