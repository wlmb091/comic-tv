# Como o Stremio toca torrents, e por que o Comic TV fica em "0 conexões"

Pedido do usuário em 03/11/2026, com foto da 1.4.28 parada em "Conectando ao torrent · 0 conexões". O APK do Stremio é a referência da aba TV. Análise só estática, com REA e extração do `server.js`; o APK não foi instalado nem executado, e nenhum código foi copiado.

Artefato: `com.stremio.one-1.12.1-53145729-arm64-v8a.apk`, 76.772.139 bytes, SHA256 `ca53408edadde3fe984346afa09e8e47000ecc8ef0c6c6e7c9b1434f1c6049e4`.

## O que o APK mostra (observado)

- Bibliotecas: `libnode.so` (Node.js embutido) com `assets/server.js` (servidor de streaming, porta 11470), `libstremio-server.so`, `libstremio_core_kotlin.so`, três players (`libmpv.so`, `libvlc.so` e ExoPlayer com `libmedia3ext.so` e FFmpeg), `libass.so` e `libgav1JNI.so`.
- O torrent não usa libtorrent: é o motor em JavaScript do `server.js` (EngineFS, `PeerSearch`).
- Padrão de cada torrent (`enginefs.getDefaults`): `peerSearch {min:40, max:150, sources: defaultTrackers + "dht:<hash>"}`, `connections` 35 (o padrão das configurações é 55), handshake 20 s, pedido 4 s, `swarmCap` com 5 pares mínimos e limite suave de 1,6 MB/s.
- `defaultTrackers` é uma lista fixa de 20 trackers públicos (19 UDP, 1 HTTP), como `udp://tracker.opentrackr.org:1337/announce`, `udp://open.demonii.com:1337/announce`, `udp://open.stealth.si:80/announce` e `udp://tracker.torrent.eu.org:451/announce`. Ela entra em todo torrent, mesmo quando o complemento não manda tracker. Quando o pedido traz trackers, eles substituem a lista.
- No Android o cache é 0: buffer de 15 MiB e buffer circular de 45 MiB em memória, sem gravar o vídeo em disco.

## Comparação com o Comic TV (observado no código)

- `TvTorrentSession` monta o magnet só com os trackers que vierem em `stream.sources`. Sem trackers, depende só do DHT, que começa do zero a cada vídeo, porque cada reprodução cria e encerra uma sessão nova.
- Só uma sessão por vez (semáforo). A próxima reprodução espera a anterior terminar `session.stop()`. Enquanto espera, a tela mostra o mesmo "Conectando ao torrent · 0 conexões", e o prazo de 90 s só começa depois da espera.

## Teste no PC (observado)

Mesma configuração do `TvTorrentSession` (libtorrent4j 2.1.0-38 para Windows), torrent livre Sintel (`08ada5a7…`), só metadados, 60 s. Foram 9 execuções.
- Sem trackers: metadados em 14 a 16 s, com 3 candidatos a conexão.
- Com 11 trackers da lista do Stremio: metadados em 14 a 16 s, com 33 a 35 candidatos.
- Modo upload ligado ou desligado não mudou o resultado. Uma execução sem trackers e sem modo upload não conseguiu em 60 s.

## Conclusão

- **Provável, não provado:** na Box, a fonte escolhida não trouxe trackers, e o DHT recém-iniciado não achou pares. Com poucos seeders, ficou em 0 conexões. A lista padrão do Stremio multiplica os candidatos por cerca de 10.
- **Possível:** uma reprodução anterior ainda encerrando a sessão segurou a próxima na espera do semáforo.
- **Não verificado:** sem ADB na Box, não há log do aparelho. Não sei se a tela passou dos 90 s nem qual fonte foi usada.

## Proposta

1. Acrescentar a lista padrão de trackers públicos a todo torrent, como o Stremio faz, além dos trackers do complemento.
2. Manter uma sessão de torrent viva enquanto o app estiver aberto, com o DHT já aquecido, e trocar só o torrent a cada vídeo. Isso também acaba com a espera do semáforo.
3. Mostrar o que está acontecendo: "Iniciando torrent", "Procurando conexões: 34 candidatos, 2 conectadas · 18 s" e, ao passar do prazo, a sugestão de outra fonte com o botão já focado.
