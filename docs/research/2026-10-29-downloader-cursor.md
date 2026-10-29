# Downloader: referência estática de cursor para TV

Artefato fornecido pelo usuário: `C:/Users/wlmb/Downloads/com.esaba.downloader_1.4.5-ForGoogleAndroidTV-52_minAPI19(nodpi)_apkmirror.com.apk`.
SHA-256: `e2337dbdf9049a7922ce1c9f9dc81c4c1bf5b6b0d1e76c1adfdcca77835e7c2b`.

REA 6.7.0: inventário `inspect-artifact`, Evidence `ev_dc969be093a8bcebf2f7da3242a5f229aeb005b8c655c45d04a54f012d519dad`. A sessão não dispõe de MCP REA conectado nem de JADX; não houve alteração de configuração para instalá-los. Resultado bruto local em `build/downloader-artifact.json` (não versionado).

Inspeção complementar própria, somente tabela de strings DEX: o pacote contém classes `com.esaba.downloader.ui.components.CursorOverlay` e `CursorWebview`, com nomes `setDrawCursor`, `setCursorListener` e limites `getMinCursorX/getMaxCursorX/getMinCursorY/getMaxCursorY`. Isso é evidência de componentes de cursor próprios; não prova temporização, aceleração ou despacho de eventos em execução. Nenhum APK foi executado, nenhum código decompilado ou asset copiado.

Implementação independente no Comic TV: cursor desenhado sobre o WebView, setas deslocam o ponto, OK envia um toque local, alcançar a borda rola o documento e MENU/ESC recupera a barra nativa. A barra deixa de reservar altura durante a navegação no site. Valores de movimento são escolhas próprias. Não há injeção JavaScript, serviço de acessibilidade ou alteração de TLS/permissões de arquivos.

Vídeos do usuário foram amostrados com ffmpeg em `build/video-review/current/`: `navegaçãobusca.mp4` e `navegaçãonavegador.mp4`. Confirmam visualmente barra fixa ocupando altura e resultados reiniciando ao retornar; não substituem teste da nova implementação na Box.
