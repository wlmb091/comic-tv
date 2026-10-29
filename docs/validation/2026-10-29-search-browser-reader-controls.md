# Busca, navegador e controle do painel girado — lote aberto

## Entrega no código

- Leitor: Mais opções recebe vizinhos explícitos por identidade de botão; ↑/↓ mantêm a sequência normal em 0° e 90°, inclusive com botões opcionais. Direção da obra e comandos do leitor permanecem independentes.
- Busca: consultas pertencem ao ViewModel da rota. Mesma chave ao retornar não reinicia requisições nem apaga resultados; preserva a grade salva e a identidade do foco. Query, atalhos, nova tentativa, grupo ou fontes confiáveis diferentes iniciam outro pedido. Ao sair para segundo plano, cancela o trabalho mantendo resultados parciais; não reinicia silenciosamente ao voltar. Uma nova busca explícita retoma consultas. Cabeçalho continua no mesmo scroller da grade.
- Capas de resultados: cliente da própria extensão, com cabeçalhos/cookies; HTTPS validado; consulta só metadados quando o catálogo não informa URL, sem baixar capítulos. Até três operações visíveis concorrentes, timeout 15s/8s para metadados, 4MiB por imagem, limite 48MP e cache 48MiB com reserva de 64MiB. Falhas externas mostram Capa indisponível em vez de uma área vazia. Originais/progresso não alterados.
- Navegador: cursor nativo próprio no WebView, setas movem, repetição acelera, bordas rolam, OK toca no ponto. MENU/ESC recupera controles, sem teclado automático. Cursor pode ser desligado. Buscar/acessar e Entrar na página ocultam a barra e ampliam o site; ↓ na faixa de ações também entra quando não há confirmação/importação pendente. Confirmar downloads continua alcançável. O site mantém o próprio scroller; não foi colocado em altura ilimitada de uma Column rolável. TLS, bloqueios file/content, confirmação de download e importação existentes preservados.

Referência Downloader apenas estática: ../research/2026-10-29-downloader-cursor.md. Não executar/aplicar APK de terceiros à Box.

## Verificações executadas

- `build/reader-rotation-menu.log`: BUILD SUCCESSFUL 1m50, teste ReaderRotation e compilação AndroidTest.
- `build/search-return-cover.log`: BUILD SUCCESSFUL 1m46, GlobalSearch/GlobalSearchSession + AndroidTest.
- Verificação final após navegador e complemento de capa ausente: `build/browser-search-final.log`, BUILD SUCCESSFUL 1m45. BrowserCursor 2, BrowserPolicy 4, GlobalSearchSession 1, GlobalSearch 5: **12 casos, zero falhas/erros**. Compilação `compileDebugAndroidTestKotlin` aprovada, incluindo ReaderSettingsRotationTest e BrowserViewportTest. Testes nativos não executados.
- A primeira compilação de testes novos identificou uso incorreto de API de bounds e ausência de opt-in no teste de teclas; ambos corrigidos e recompilados na verificação final. Não foi falha de código de produção.
- `git diff --check` aprovado, somente avisos locais de conversão LF/CRLF nos arquivos preexistentes.

## Conferência na Box pendente

1. Girar leitor 90°, abrir Mais opções e percorrer ↑/↓, chegar a itens abaixo da dobra e retornar, incluindo Filtro original/HQ antiga e capa da página.
2. Buscar Batman, descer a um resultado, abrir e voltar. Fonte/contagem não reiniciam e foco/offset permanecem no título. Nova consulta/atalho/Nova tentativa devem consultar novamente.
3. Capas com URL HTTP antiga, fonte que exige headers e fonte sem URL de capa; offline deve mostrar mensagem sem bloquear título.
4. Navegar no Google, ↓ para site, setas/OK sem ativação extra, rolar até as bordas; MENU para barra, alternar cursor. Abrir teclado de formulário e retornar, testar páginas com elementos fixos.
5. Oferecer download, alcançar confirmação, cancelar/baixar/importar sem perda de foco nem mudança de dados existentes.

**Sem novo APK, versão, commit, push, CI ou publicação.** Alterações anteriores de capas automáticas/manuais e atalho de desinstalação permanecem no mesmo pacote aberto. Validação física e ajuste fino de cursor/rolagem dependem da Box; nenhuma equivalência visual/funcional física foi inferida da compilação.
