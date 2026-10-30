# Página visual de edições da saga — 29/10/2026

Pacote aberto, sem APK, alteração de versão, push ou publicação.

## Alteração

A lista textual do diálogo da saga foi substituída por uma página ampla, com uma única grade vertical rolável. A mesma implementação atende Comic TV, Flix, Prime, Max e Cinema. O cabeçalho de comandos rola junto com as miniaturas.

Cada edição conserva sua capa local proporcional e inteira (`ContentScale.Fit`), título, estado Não iniciado/Em leitura/Lido e progresso existente. O modo de escolher capa usa a mesma grade; mantém a opção de imagem externa e de recuperar a capa original da saga. Não altera arquivos de edições, progresso nem agrupamento. Miniaturas ausentes recebem indicação, sem novas consultas de rede.

A grade conserva estado no chamador e restaura a edição anterior após detalhes/opções. Na primeira entrada prioriza a última leitura. Emissões de progresso/capa não disparam foco novamente. ↑ da primeira fileira revela o cabeçalho e ↓ dos comandos retorna à edição selecionada. Back sai primeiro da escolha de capa, depois da página, retornando ao cartão de origem na coleção. Os cartões têm espaço para escala/contorno, elevação quando focados e colunas ajustadas à escala do texto. Não há grade dentro de outro scroll vertical.

## Verificação

- Teste inicial falhou pelas funções ainda ausentes (`build/saga-page-red.log`).
- 3 testes JVM passaram, sem falhas: retorno explícito/última leitura, estados de leitura incluindo edição de uma página ainda não aberta, adaptação de colunas ao texto grande.
- 2 testes Compose de foco/cabeçalho/última edição/modo de capa compilados. Não executados: sem dispositivo conectado nesta etapa.
- `:app:testDebugUnitTest --tests '*SagaEditionPageTest' :app:compileDebugAndroidTestKotlin :app:lintDebug`: BUILD SUCCESSFUL em 1m23s (`build/saga-page-verified.log`). Lint sem erros.
- `git diff --check` limpo.

## Conferência física pendente

Na Box: abrir saga com várias edições; percorrer primeiro/último cartão e ↑↓/←→; abrir detalhes e voltar; segurar OK e voltar das opções; escolher uma capa, restaurar original e cancelar seletor externo; repetir com texto 130% e alto contraste. Confirmar imagem inteira, contorno visível e foco/posição restaurados. Compilação não comprova execução física.
