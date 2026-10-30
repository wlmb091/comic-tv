# TV Bro2.1.6 — referência de navegador TV

Artefato enviado pelo usuário: `com.phlox.tvwebbrowser_2.1.6-69_minAPI24(arm64-v8a,armeabi-v7a,x86,x86_64)(nodpi)_apkmirror.com.apk`,6.790.605bytes,SHA256d8634edfe8d94b4fb9a52005d68a090a7f1e85b7f39c2cf01a25dc9dd60942b2.

REA6.7.0 inspect-artifact Evidence `ev_4327214a08a463b2ea71a2668de27a1116fccae39f2a257ba72f1b39b8176af8`; rawlocal build/tvbro-artifact.json. `inspect-android-package` provider indisponível por faltar jadx-headless-mcp0.7.1; não configurar MCP nem instalar engine sem necessidade. Complemento estático com aapt2 e tabelaDEX própria: pacotecom.phlox.tvwebbrowser/name2.1.6/code69/minAPI24; classes ActionBar,TabsView,CursorLayout,DownloadsActivity,DownloadService,WebViewWebEngine. Bibliotecas nativas somentead-block quatroABIs; semGecko na variante enviada. Nenhuma execução do APK/Box, nenhum código decompilado/copiadoparaComicTV.

Fonte público da tag correspondente inspecionado: https://github.com/truefedex/tv-bro/tree/v2.1.6 ,commit a5a9ccbdcd0bb7a667977c4e1ed31d2fd29794c3. Clone local apenas em build/research/tvbro-source ignorado. Conferência adicional da API GitHub releases/tags/v2.1.6: o asset oficial tvbro-2.1.6-generic-geckoExcluded.apk tem exatamente6.790.605bytes e SHA256d8634edfe8d94b4fb9a52005d68a090a7f1e85b7f39c2cf01a25dc9dd60942b2, idêntico ao APK enviado. Isso confirma a identidade do artefato oficial, sem provar medição runtime. Licença própria do projeto lida; solução do ComicTV é implementação independente sem código/assets incorporados.

Observações do fonte público (não medidas runtime):
- activity_main.xml: superfície do site, topo com ActionBar+TabsView e barra inferior compacta com fecharaba/voltar/avançar/atualizar e opções.
- CursorLayout/CursorDrawerDelegate: ponteiro sobre superfície, eventos sintéticos de toque, movimento contínuo/aceleração, inatividade, OK longo/arrastar e tratamento de scroll.
- WebViewWebEngine: navegação/histórico, janelas por gesto, FileChooser e vídeo customView em tela cheia, DownloadListener encaminha dados.
- DownloadTask: User-Agent, CookieManager e Referer da página; nome pelo Content-Disposition/Content-Type da resposta. Serviço/modelo próprio alimenta tela de downloads e progresso.
- O serviço de referência não é transplantado: ComicTV continua com fila própria limitada, pausa fora de primeiro plano e integração com importação validada.

Diferenças encontradas no ComicTV: rejeição de nome desconhecido antes de consultar resposta, ausência de Referer no transferidor, tarefas/arquivo completo presos ao ciclo de composição, ausência de histórico/retomada/interface de downloads e janelas/tabs. Esses mecanismos, e não apenas a cor dos botões, são o foco da reconstrução própria.
