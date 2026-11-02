# Carrossel da TV igual ao do celular quando sincronizados (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.20 (código 94), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
Com celular e TV sincronizados, a passagem de página na TV não tem a fluidez do Leitor do celular. A imagem desliza, mas a tela seguinte fica escura até o dedo soltar, e só então a imagem carrega.

## Causa, pela leitura do código da 1.4.19
Não reproduzido em aparelho. Era um limite conhecido e informado na 1.4.19 (`docs/validation/1.4.19.md`):

- `reader-drag` só muda `dragX` (`ReaderViewModel.companionDrag`), e `HighResolutionPages` desloca a página atual. Nada é desenhado no espaço que ela libera.
- A troca de página só vem no fim do gesto, com `reader-page`, e a TV então carrega a nova página.

## Direção da correção (a confirmar ao implementar)
- Desenhar a página vizinha ao lado da atual durante o arrasto, como o carrossel do celular.
- Aproveitar o que a TV já antecipa: as duas próximas páginas originais e a visão geral delas (`ReaderScreen.kt:116`). Ao soltar, a vizinha já visível vira a página atual, sem tela escura.

## Pontos a verificar ao implementar
- A página anterior não aparece nessa antecipação. Provavelmente precisa ser preparada também.
- Galeria, Livro, zoom acima de 100%, leitor girado 90° e direção direita → esquerda.
- Fluidez e atraso reais só podem ser confirmados na Box.
