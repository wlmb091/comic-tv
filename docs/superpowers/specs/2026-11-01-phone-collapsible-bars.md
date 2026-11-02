# Barras recolhidas nos temas do celular (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.19 (código 93), AUTORIZADO PELO USUÁRIO EM 01/11/2026; Box e iPhone PENDENTES.**

## Pedido
- Contexto: alterações que ficaram deslocadas nos temas do painel web do celular (`app/src/main/assets/companion/index.html`).
- **Todas as barras** do painel nos temas (lateral de abas Biblioteca/Leitor/Controle/Arquivos, barra inferior Anterior/Página/Próxima, e as demais de cada tema) devem ficar **recuadas (recolhidas)** por padrão.
- Recolhida, aparece **apenas a seta** indicando que dá para expandir.
- A barra **só expande quando a seta for tocada**; do contrário atrapalha a leitura (na foto, a barra lateral e a inferior ocupam área do quadrinho).
- Vale para **todas as abas** e para **as barras de cada tema** (Comic TV, Flix, Prime, Max, Cinema).

## Foto enviada
- Aba Leitor (tema Comic TV): barra lateral esquerda com Biblioteca/Leitor/Controle/Arquivos e barra inferior "‹ Anterior · Página 7 de 76 · Próxima ›" ocupando espaço da página.

## Regras que continuam valendo
- O layout do **Controle** aprovado (`docs/design/companion/control-layout-approved-2026-11-01.png`) não muda de tamanho, posição ou função; só cores. Ponto a resolver ao implementar: como recolher barras sem mexer no bloco do Controle (barras de navegação do painel são separadas dele; o Controle em si não muda).

## Decisões do usuário (01/11/2026)
- **Ao fechar e reabrir o painel:** as barras voltam **sempre recolhidas** (não lembra o último estado).
- **Depois de tocar numa aba:** a barra **recolhe sozinha**.
