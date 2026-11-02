# Biblioteca do celular agrupada por saga (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
A Biblioteca do celular deve agrupar por saga, como a da TV. Ao tocar numa saga, como a Saga do Infinito, aparecem todas as edições já lidas dela. Hoje o celular mostra cada livro separado.

## O que existe hoje, pela leitura do código da 1.4.20
- A TV agrupa com `LibraryRepository.sagaGroups(comics)` (`SagaGroup`, com `editions`), incluindo a escolha de capa da saga. Na tela, o estado de cada edição vem de `sagaEditionStatus` (`SagaEditionsPage.kt`).
- O endpoint `/library` envia só a lista plana de edições (`CompanionController.kt`).

## Direção proposta (a confirmar ao implementar)
- `/library` passa a enviar também os grupos, usando o mesmo `sagaGroups`, para a saga do celular ser igual à da TV.
- Card da saga com a capa da saga. Ao tocar, abre a página com as edições e o estado de cada uma. Edição sozinha continua como card normal.
- Ler e Na TV continuam nas edições.

## Pontos a verificar ao implementar
- Capa da saga no celular, incluindo a escolhida manualmente.
- A busca da Biblioteca continua encontrando edições dentro das sagas.
- Continuar lendo segue individual, como na TV.
- Junto de `2026-11-02-phone-chapters.md`: edições lidas vêm da biblioteca, e os capítulos ainda não lidos vêm da fonte.
