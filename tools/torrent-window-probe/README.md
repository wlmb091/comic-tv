# Teste nativo da janela torrent — HY320 ARM de 32 bits

Usa as classes reais compiladas de `TvTorrentSession`/`TvTorrentEngine`, libtorrent4j ARM e um `ContextWrapper` apontando somente para uma pasta temporária. Não instala app, não gera APK, não usa complementos ou vídeos do usuário. Um seeder local no próprio projetor serve um arquivo sintético esparso de 1.516.383.963 bytes, com marcador diferente em cada peça. As hashes conferem esses bytes, incluindo a cauda. A ocupação física do arquivo do seeder é pequena.

Depois de `:core:jar :app:compileDebugKotlin`:

```powershell
./tools/torrent-window-probe/run.ps1 -Serial 192.168.0.81:5555
```

Requer Python, JDK 17, SDK 36/build-tools 35 e as dependências já presentes no toolchain local. `-Toolchain` permite indicar outra pasta. O teste abre duas sessões nativas isoladas dentro do seu próprio processo, usa peer loopback para obter os metadados e depois deixa a sessão de produção reaproveitar os pares ao renovar a janela. Lê início, cauda, extremidades de todas as janelas e volta ao começo, verificando os marcadores. É um teste de leitura e busca, não de decodificação ou interface do player. Pode levar alguns minutos.

Saída com `PASS native actual-session` confirma as leituras. `usable` mostra o espaço livre ao longo do teste; comparar os mínimos e a recuperação entre janelas. Os logs locais ficam fora do repositório. Em sucesso, a pasta temporária exclusiva do projetor é removida. Em falha, fica para inspeção; não é uma pasta do Comic TV instalado.
