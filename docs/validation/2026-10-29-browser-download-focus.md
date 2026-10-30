# Downloads: foco após importar — 29/10/2026

Pacote aberto após1.4.2; semAPK,versão,push ouCI. Preservada correçãoMEGA/Blob anterior.

## Evidência e causa

Foto do usuário mostraEPUBImportado comLeragora/Detalhes visíveis mas sem foco, exigindoVoltar. No código anterior o botãoImportar era removido no estadoImporting e substituído porText; ao concluir os botões eram criados novamente. A páginaWebView ao fundo continuava focável, permitindo perder o foco da sobreposição.

## Correção

- Botão principal permanece no mesmo local/composição com chaveprimary: Importar→Importando…→Leragora. Durante operação continua focável e não dispara outra importação. Falha retorna aImportar, mantendo foco. Os demais estados de download também preservam o botão principal.
- Salvar mantém controle focável duranteexportBusy em vez de desativá-lo sob o foco.
- Painel guarda últimaação/edição eLazyColumnstate; restaura ação após diálogos, usa principal se uma ação desaparece eVoltaraonavegador se oregistro foi removido. Atualizações de progresso ou de outra linha não pedem foco. D-pad permanece no grupoDownloads;Voltar explícito/hardware continua fechando a sobreposição.
- WebView recebeinput somente quando a página está exposta, semchrome/sheets/oferta/exclusão/fullscreen. Desabilita foco nativo e libera cursor enquanto controles nativos estão ativos; NÃOpausaJavaScript/rede/transferênciasBlob do site. Ao retornar, o foco é devolvido pelos controles existentes.
- Efeito de foco doBrowserScreen também respeita o diálogo de excluir, evitando foco do site durante confirmação.

## Validação

- Primeiro teste falhou comfunção deações ausente (`build/download-focus-red.log`).
- 32testesJVM doBrowser passaram,0falhas/0erros. Incluem3novos: identidadeprimary durante importação/sucesso, presença emtodosestados/falha novamenteimportável eSalvar ocupado sem desaparecimento do controle.
- 2testesCompose de regressão acrescentados: Importar→Importando→Leragora com foco+acionamentoD-pad e falha/atualização de outra linha semroubarfoco. Compilados; NÃOexecutados em dispositivo.
- `:app:testDebugUnitTest --tests '*Browser*Test' :app:compileDebugAndroidTestKotlin :app:lintDebug`: BUILD SUCCESSFUL2m43 (`build/download-focus-final.log`),lint0erros/70avisos.
- Revisão independente semP1/P2; `git diff --check` limpo.

## Conferência na Box pendente

Após próximoAPK autorizado, baixar/importarEPUB/CBR e acionarLeragora comOK semusarVoltar antes; ir aDetalhes e voltar ao mesmo item; falha/cancelamento deimportação;Excluir→Cancelar; excluirúltimoitem; salvar viaSAF; concluir outrodownloadenquantofoco está noutra linha; fecharDownloadse navegar nosite. Isso ainda não foi executado fisicamente; testescompilados não comprovam comportamento doAndroidSystemWebView daBox.
