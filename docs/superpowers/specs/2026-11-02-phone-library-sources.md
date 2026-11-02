# Novidades e Catálogo na Biblioteca do celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
Incluir as seções Novidades e Catálogo na Biblioteca do celular, como na tela inicial da TV.

## O que existe hoje, pela leitura do código da 1.4.20
- A TV monta as fileiras com `SourcesViewModel.homePreviews` (`HomeSourcePreviews`, títulos da fonte selecionada), e `onSourceTitle` abre o título (`ComicTvApp.kt:227`).
- O painel não recebe esses títulos. `/library` traz só as edições da biblioteca, e `/cover` só capas da biblioteca.

## Direção proposta (a confirmar)
- Novo endpoint com as duas listas, usando a mesma fonte selecionada na TV, nesta ordem: Novidades, Catálogo.
- Capas dos títulos online entregues pela TV, porque o celular não acessa as fontes diretamente.
- Ao tocar num título, o celular mostra os capítulos dele, reaproveitando `2026-11-02-phone-chapters.md`.

## Pontos a verificar ao implementar
- Sem fonte selecionada na TV, as seções ficam ocultas ou com um aviso.
- Limite de consultas à fonte: o servidor atende dois recursos por vez, e as capas precisam entrar em fila.
- Abrir um título ainda fora da biblioteca precisa criar a edição, como faz a TV.
