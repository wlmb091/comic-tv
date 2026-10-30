# Navegador TV Bro como referência — implementação própria, pacote aberto

Pedido autorizado: estudar APK fornecido com a skill de engenharia reversa e substituir antiga seção do navegador por navegação de TV, incluindo abas, botões de páginas e acompanhamento de downloads. Usuário ainda reúne alterações: **SEM novo APK, versão, commit/push, CI ou publicação**. Última publicação permanece1.4.1/code75.

## Evidências e decisões

- docs/research/2026-10-29-tvbro-browser.md: REA inspect-artifact, manifest aapt2 e inventárioDEX. APK enviado é idêntico em tamanho/SHA256 ao asset oficial2.1.6generic-geckoExcluded, WebView, sem Gecko. Código público tagv2.1.6/commit a5a9ccbdcd0bb7a667977c4e1ed31d2fd29794c3 inspecionado. ProviderREAAndroiddecompile indisponível sem jadx-headless-mcp, não houve execução doAPK, instalação naBox nem transplante de código/assets.
- docs/design/browser-tvbro-2026-10-29.md e docs/superpowers/plans/2026-10-29-tv-browser.md definem escopo/limites. Sem outro motor grande/dependência, lista de anúncios nova ou modo incógnito sem isolamento real.

## Interface e navegação implementadas

- WebView ocupa viewport completo, sem padding externo. Antiga tela de botões removida; BrowserViewport agora sobrepõe controles, não redimensiona o site.
- Endereço/pesquisaGoogle e voz do Android, favorito da página, abas no topo. Botões compactos inferiores: fecharaba, voltar/avançar páginas, parar/atualizar, início, downloads, mais, retornar à página e sair.
- MENU/Voltar do controle recuperam/fecham controles. Botões voltar/avançar fazem navegação real de páginas. IME/vídeo/diálogo têm prioridade no Voltar; modo direto retorna primeiro ao cursor.
- Até8abas,2WebViews vivos; abas suspensas guardam Bundle de histórico bounded512KiB poraba. Estado de sessão/URLs/favoritos/histórico é app-private; favoritos/histórico100cada. Não copia bitmap de cada página para alternar abas.
- Cursor com quadros contínuos/aceleração, três velocidades, rolagem na borda via mousewheel para contêineres de página, clique/toque, segurarOK para opções do link e arrastar. Estado da tecla limpo ao perder foco local/janela/attach e ao interceptarMENU, evitando tecla retida e ativação extra ao retornar à página.
- Opções de link: abrir, abrir em novaaba, baixar e copiar endereço. Mais: favoritos/histórico/localizar/desktop/cursor-direto/velocidade/texto/arrastar/popups. Vídeo usa customViewfullscreen; seletor nativo para upload de arquivos escolhidos explicitamente. Permissões web de câmera/microfone não são concedidas automaticamente.

## Downloads implementados

- BrowserDownloadsPanel acompanha bytes/% reais; tamanho desconhecido é indicado sem porcentagem inventada. Estados fila/baixando/pausado/falhou/concluído/importando/importado/cancelado. Progresso reaberto lê tamanho real do parcial.
- Fila12/uma transferência, máximo1GiBporarquivo,2GiBtotal e64MiBreserva,100registros. Arquivos separados em filesDir/browser-downloads, persistência atômica dos registros. Completados não são apagados ao sair da página ou navegador. Remoção pede confirmação e só remove o próprio download, preservando cópias já importadas/originais.
- Pausar/retomar/recomeçar/cancelar/repetir, salvar cópia pelo seletorSAF, importação nos formatos existentes e Ler/Detalhes quando houver ID único importado. Não instala/baixaAPK: valida extensãoAPK/APKM/APKS/XAPK e MIMEAndroid antes de escrita HTTP/blob.
- Falhas anteriores: nome era recusado antes da resposta do servidor; faltava Referer; tarefa/arquivo ficavam ligados à composição antiga. Novo transferidor usa CookieManager por destino, User-Agent, Referer com política de origem, Content-Disposition UTF8/Content-Type da resposta, redirecionamento limitado sem downgradeTLS.
- Range/If-Range com validador, Content-Range e tamanho conferidos. Servidor200recomeça em vez de concatenar. Novo validador publicado somente após truncar bytes antigos ao reiniciar. Truncamento/falha não são tratados como concluídos. HTML de login sem attachment é erro explícito.
- Blob: confirmação por oferta de download, mesma origem da aba, canal WebMessagePort de uso único, token, protocolo simples limitado, chunks196608bytes/ACK, limite de tamanho e120s; sem JavascriptInterface global. Portas/stream fecham ao cancelar; sem arrayBuffer do arquivo inteiro. URLs blob não sobrevivem a nova instância do documento; retomar pode exigir reabrir o link. Mapa para página usa WeakReference e não segura Activity naViewModel.
- Sem serviço contínuo: downloads pausam fora do primeiro plano ou ao sair do destino; retomar é explícito. Upload seletor/voz externos podem colocar app em segundo plano. Cookies não são copiados para registros/backup/logs. Sessão/downloads do navegador não entram no backup portátil da biblioteca.

## Verificação executada

- browser-transfer-green.log: BUILD SUCCESSFUL1m43. Transfer6+Policy4; nomes genéricos/UTF8, contextoCookie+Referer, Range, reinício200, HTML e truncamento.
- browser-downloads-green.log: BUILD SUCCESSFUL1m36. Fila sequencial, parcial preservado/retomada, pausa ao sair do primeiro plano, exclusão isolada/restauração.
- browser-session-green.log: BUILD SUCCESSFUL1m45. Abas/limites/histórico/favoritos/restauração, matemática do cursor e AndroidTestcompile.
- tv-browser-final.log: BUILD SUCCESSFUL1m47. Browser21+busca15 = **36casos/0falhas/0erros**, AndroidTestcompile aprovado, incluindo viewport estável sob controles e painel de progresso real. Correção anterior de capas continua incluída e verificada.
- tv-browser-lint.log: BUILD SUCCESSFUL2m29, lintDebug **0erros/0fatal/70avisos** e AndroidTestcompile. Avisos do navegador são SDK_INT redundante, orientaçãoUsableSpace/StorageManager e estiloKTX, sem falha de API/segurança. Ajuste posterior de restauração dos bytes do parcial foi verificado no comando focado seguinte.
- tv-browser-complete.log: BUILD SUCCESSFUL1m52. **21testes do navegador/0falhas**, AndroidTestcompile após ajuste de progresso recuperado do disco. git diff --check aprovado.
- Revisão final em contexto novo: encontrou restriçãoAPK e tecla retida ao mudar foco, ambos corrigidos e rechecados. Nenhuma pendência concreta adicional relatada nessa revisão.

## Conferência física pendente

NaBox: abrir/restaurar navegador, endereçoOK/IME/voz, MENU/Back, cursor segurado/solto/OKlongo/arrastar, alternar8abas e voltar ao histórico após suspensão, favorito/localizar/desktop, popup porclique, vídeo fullscreen e retorno, uploadselecionado.
Downloads: servidor com URLsemextensão, Cookie/Referer, Content-Disposition, download direto/temporárioBlob, progresso conhecido/desconhecido, pausa/reabertura/retomar/reinício200/cancelar, salvarSAF/importar/ler e exclusão sem remover biblioteca. Confirmar comportamento da página original ao abrir/fecharcontroles e compatibilidade do WebView/codecs daTV.

Compilação/testes não demonstram navegação idêntica ou compatibilidade100% com sites. Login, links expirados, formuláriosPOST, iframesBlob de outra origem, codec/DRM e políticas do site podem exigir fluxo próprio. Não houve teste físico nem alegação de funcionar em todos os sites. Arquivos/originais/progresso/USB doComicTV preservados.
