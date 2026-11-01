# Carrossel do tema Prime ativo (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.15 (código 89), AUTORIZADO PELO USUÁRIO EM 01/11/2026. Nada executado na Box nem no celular.** Registro: `docs/validation/1.4.15.md`. Intervalo decidido: 8 s.

## Pedido
Deixar o carrossel do tema Prime **ativo** (troca automática) e fazê-lo ciclar **de 3 em 3**.

## Situação atual (do código, 1.4.14)
- `ComicTvApp.kt` liga a troca automática do destaque só para Flix, Max e Cinema (`autoplay = preset in setOf(Flix, Max, Cinema)`). **Prime não troca sozinho** por decisão da 1.2.8 ("Prime sem autoplay"); o carrossel simples de 8 s e o filtro de imagem foram preservados nas versões seguintes.
- A opção Configurações → "Troca automática" (`carouselAutoplay`) vale para os temas que ciclam; alto contraste desliga a troca.
- As fileiras da Home (Catálogo e Novidades) ciclam 48 títulos a cada 10 s desde a 1.4.13, parando com o foco na fileira.

## Confirmado pelo usuário (foto)
Referência: `docs/design/themes/prime-carousel-three-covers-2026-11-01.webp` (foto da TV, com a área marcada em vermelho).
- O carrossel em questão é a **faixa com 3 capas lado a lado** no destaque do Prime ("Continuar sua leitura", abaixo dos botões Continuar leitura / Ver detalhes), com os **pontos de página** embaixo (● ○ ○).
- Hoje ela fica **estática**. O pedido é que **atualize sozinha de 3 em 3 capas**, ou seja, a cada troca aparece um novo grupo de 3 capas e o ponto de página avança. (Interpretação B da primeira anotação.)

## Decisões do usuário (01/11/2026)
- **Intervalo:** "usar o mesmo tempo" dos outros carrosséis. O usuário não disse qual dos dois (8 s do carrossel simples ou 10 s das fileiras da Home); **a confirmar**. Sugestão: 10 s, igual às fileiras, que também trocam um grupo de capas por vez.
- **Último grupo:** volta para o primeiro (ciclo contínuo).
- **Menos de 3 edições:** a faixa fica estática, sem troca.

## Ainda a definir na implementação
- Confirmar o intervalo (8 s ou 10 s).
- Respeitar a opção "Troca automática" e o alto contraste, e pausar com o foco na faixa, como nos demais temas.
- Quantidade de edições usadas no rodízio (a faixa hoje mostra poucos grupos, como no ponto de página da foto); sugestão: as mesmas primeiras edições que a faixa já usa, sem consulta nova.

## Cuidados
- Preservar foco único, rolagem e D-pad: a troca não pode mover foco nem gravar progresso (regras da 1.2.8).
- Pausar com menus, diálogos, segundo plano e interação por tecla.
- Preservar a decisão do usuário de que Prime mantém o carrossel aprovado (1.2.3); não alterar proporções nem a composição.
- Compilação e testes não provam o resultado visual; conferir na Box.
