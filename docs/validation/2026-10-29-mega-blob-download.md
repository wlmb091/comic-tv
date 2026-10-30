# MEGA / arquivos temporários do navegador — correção em pacote aberto

## Pedido e evidência

Usuário mostrou confirmação comUUID.bin noComicTV, enquanto o computador oferecia o mesmo quadrinho comoCBR. Autorizou corrigir e avisar ao terminar, SEM gerarAPK, pois continuará enviando observações. Sem novo número deversão/push/CI/publicação.

Investigação do nosso código: DownloadListener passava sóURL/contentDisposition/MIME paraURLUtil.guessFileName; não lia o atributoHTMLdownload. A transferênciaBlob buscava aURL somente após confirmação nativa, podendo usar uma referência já revogada.

Fonte primária consultada, somente para revisão e sem incorporar código do fornecedor:
https://raw.githubusercontent.com/meganz/webclient/master/js/transfers/meths/memory.js . No caminhoMemoryIO, oMEGA coloca nome no atributo do link e revoga aURLtemporária após7segundos. OMEGA também tem outros caminhos; não inferir que todo download utilize esse mecanismo. Não foi fornecido link reproduzível do arquivo dafoto nem realizada execução doMEGA naBox.

## Implementação própria

- Preparação imediata noDownloadListener e noBaixarlink do menu de contexto. Captura o nome do elemento cujo endereço coincide com aURLBlob e mantém referência aoBlob já preparado pelo site antes da confirmação nativa.
- SomenteBlob da mesma origem da página. Referências limitadas a4porpágina/5minutosinativas; ativa durante transferência, novo prazo quando pausada. Cancelar oferta/download, excluir ou concluir libera a referência. SemJavaScriptInterface; chaves aleatórias, portasWebMessage existentes.
- Ao confirmar, streaming em partes de196608bytes comACK e cancelamento; metadadosUTF8 preservam nomes comacentos. Nenhum arrayBuffer do arquivo inteiro nem novaAPI/SDKdoMEGA. A página de origem ainda deve permanecer aberta; fechar/recarregar/destruir aaba ou encerrar o processo perde a referência.
- Após receber exatamente o tamanho anunciado, identificaRAR/ZIP/PDF/EPUB pelos bytes. Mantém nomeCBR quando compatível; recupera extensão de nomes genéricos somente quando identifica formato. Conteúdo não reconhecido não vira quadrinho por troca cega deextensão. Identificação de cabeçalho não é prova de integridade de todas as páginas; aimportação mantém validação própria.
- ZIP tem diretório limitado a5000entradas/8MiBantes deZipFile. PacoteAndroid disfarçado dequadrinho continua bloqueado. Limites existentes1GiB/download/2GiBdearquivos/reserva64MiB preservados.
- Cookies não alterados. Cookies de terceiros continuam bloqueados naWebView; cookies da origem e propagação dos cookies do destino nosdownloadsHTTP permanecem. Não há evidência de que falta de cookies tenha causado oUUID.bin dafoto.

## Verificação

- Teste inicial falhou porBrowserBlobFile ausente (`build/mega-blob-red.log`).
- 29testesJVM do navegador passaram,0falhas/0erros, incluindo4novos casos de nomeUnicode, detecçãoRAR, ZIPsemrenomearparaCBR, instaladorAndroiddisfarçado e payloadHTML/desconhecido.
- 6testesNode executam oassetJS e o mesmo script de streaming embutido noAndroid: nome/preservação apósrevogarURL, limites/origem/liberação, expiração/pausa, limite detamanho, UTF8/chunks/ACK e cancelamento. Semrede nosfixtures.
- TesteAndroidWebView com origem local simulada/Blob/captura/revogação/transferência compilado, NÃOexecutado em dispositivo.
- VerificaçãofinalGradle BUILD SUCCESSFUL2m36 (`build/mega-blob-final.log`); AndroidTestKotlincompilado,lintDebug0erros/70avisospreexistentes. `git diff --check` limpo.
- Revisão independente identificou ordem incorreta ao excluir: referência era liberada após remover oregistro. Corrigido para liberar antes de remover.
- Workflow futuro passa a executar os testesNode na próxima publicação autorizada; nenhumaexecuçãoCI foi disparada nesta etapa.

## Conferência física pendente

Após o próximoAPK autorizado, testar o mesmo linkMEGA: nomeCBR na oferta, aguardar mais de7segundos antes de confirmar, acompanharDownload e importar/abrir; cancelar e repetir; pausar/retomar mantendo aaba; fechar aaba deve gerar mensagem de reabrir e nunca indicar sucesso indevido. Arquivos maiores podem continuar limitados pela memória/implementação do próprio site/WebView. Não alegar compatibilidade integral doMEGA ou validação naBox a partir decompilação/fixtures.
