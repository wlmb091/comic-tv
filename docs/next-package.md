# Próximo pacote do Comic TV

Os seis pedidos de 02 e 03/11/2026 entraram na 1.4.29 (código 103): carrossel dinâmico, configurações da TV no celular, chave do neon, torrent sem "0 conexões", lista de complementos pelo celular e filtro de fontes. Detalhes e decisões em `docs/validation/1.4.29.md`. Nada foi testado na Box nem no iPhone.

**Não implementar nem publicar antes do aviso do usuário.** Ao receber um pedido novo, listar dúvidas e sugestões antes de alterar, e perguntar quem publica: a autorização de publicar vale só para um lote.

## Regras do usuário para este trabalho

- Função com efeito ou movimento sempre tem chave para desligar, nas configurações da TV e do celular.
- O layout do Controle continua congelado, e os temas mudam só as cores. Só muda se o usuário pedir.
- Comportamento só é confirmado no aparelho: compilar e testar não prova que funciona na Box ou no iPhone.

## Lote 1.4.31 — fechado pelo Claude Code em 03/11/2026

mpv como motor padrão do player (ExoPlayer como opção e reserva), libarchive no lugar do Junrar (RAR com senha deixou de ser aceito, por decisão do usuário), GPL versão 3, NDK local instalado e limite do atualizador em 150 MiB. Também entram a chave "Decodificador de vídeo", o Nova Video Player como player externo, a saída do x86 e das tabelas pós-quânticas do BouncyCastle. Registro: `docs/validation/1.4.31.md`; notas: `docs/releases/1.4.31.md`. Nada testado na Box, no projetor ou no iPhone.

## Lote 1.4.30 — Codex, autorizado em 03/11/2026

Usuário aceitou as cinco recomendações e autorizou as correções. Registro: `docs/validation/1.4.30.md`. Foco do player, diagnóstico de vídeo, preparação com imagem/chave, troca de player por vídeo e janela torrent implementados localmente. Teste nativo isolado no HY320 atravessou o arquivo sintético de 1,5 GB com menos de 500 MB livres, sem crescimento acumulado do cache. O buffer usa disco limitado; não é exclusivamente RAM. Testar o filme e a interface após gerar o APK.

**Quem gera o APK e publica: Claude, quando o usuário pedir após o aviso do Codex.** O Codex não gera APK nem publica este lote. A base continua 1.4.29/code103 até o fechamento pelo Claude.

**Legendas no mesmo lote (Claude Code, 03/11/2026):** importar arquivo pela TV, buscar de novo nos complementos, legenda lembrada por vídeo com a sincronia, passos de 0,1/0,5/1 s e Zerar, chave na TV e no celular. Registro na seção "Legendas" de `docs/validation/1.4.30.md`. Versão ainda 1.4.29/code103 até o fechamento.

## Lote 1.4.31 em preparação (Claude Code, 03/11/2026) — nada publicado

Prioridade máxima do usuário: o vídeo não aparece no player do Comic TV (ExoPlayer) no projetor HY320 (Allwinner, Android 11): o decodificador `OMX.allwinner.video.decoder.avc` inicia, mas nunca entrega quadro (`rendered=0`, sem `firstFrame`); o áudio toca. A Mi TV Box 4K (principal, sem ADB) tem só o "Media player" do sistema como outro player. Análise estática do APK 1.12.1 do Stremio: ele traz ExoPlayer + LibVLC + mpv + FFmpeg (decodificação por software), VLC com lista de decodificadores ruins por chip. Usuário: "o ExoPlayer não pega nem no Stremio, o que abre é o mpv".

Decisões do usuário: o mpv entra como motor interno (padrão "Automático"), o ExoPlayer vira escolha, com decodificadores de software incluídos (H.264, H.265, VP9, AV1, MPEG-2), LGPL. Teste do item 1 (sem legenda automática) adiado para o lote seguinte.

Já feito, para abrir espaço no APK (limite de 64 MiB do atualizador): removidos x86/x86_64 (`ndk.abiFilters`) e os arquivos `org/bouncycastle/pqc/crypto/**` (tabelas pós-quânticas do BouncyCastle do PDFBox). APK release sem assinatura de 49,2 para **38,3 MiB**; testes 298/355 sem falhas. R8/minify fica de fora (reflexão do Mihon, sem aparelho para testar).

Atualização (03/11/2026, tarde): o VLC externo também ficou preto no projetor; o **Nova Video Player 6.6.3** (org.courville.nova) mostra imagem e legenda no projetor e na Mi Box. Análise estática dos APKs do Nova: motor próprio `libavos` (GPL) com decodificador de hardware (`sfdec`) e volta para o FFmpeg de software (LGPL 2.1+) quando falha; opção "forçar decodificação por software". O libmpv pronto que o Stremio usa (`dev.jdtech.mpv:libmpv`) é **GPLv3** (`--enable-gpl`), então não serve. Usuário: "vamos testar sem o GPL, caso não dê certo a gente habilita".

Feito localmente (sem publicar): chave "Decodificador de vídeo" (Automático/Hardware/Software) na TV e no celular, com troca automática para o decodificador de software do Android (`OMX.google.h264.decoder`) se em 10 s não vier nenhum quadro; "Nova Video Player" como player externo (sem legenda automática, porque os extras dele não foram confirmados); x86/BouncyCastle fora do APK.

**Correção minha:** o `ExperimentalFfmpegVideoRenderer` do Media3 1.8.0 é um esqueleto ("not yet functional"), então só compilar decodificadores de vídeo no FFmpeg NÃO basta; faltaria escrever o decodificador de vídeo em JNI. Caminhos se a troca para o software do Android não resolver: (1) esse JNI próprio com FFmpeg LGPL, (2) mpv compilado por nós sem GPL, (3) habilitar o GPL.

Teste pendente: instalar um build de teste no projetor e ver se a troca automática faz a imagem aparecer. Falta versão 1.4.31/code105 e publicação (não autorizada).

## Ainda aberto

- Player da TV (03/11/2026, análise do vídeo `player-tv.mp4`): tela preta com som, menus que não voltam, imagem 16:9 na preparação e troca de player no menu do vídeo. Análise, causa dos menus e dúvidas em `docs/pending/player-tv-2026-11-03.md`. Implementação e validação local em `docs/validation/1.4.30.md`; testar a interface e o filme após o APK novo.

- O microfone do Navegador da TV não foi corrigido. Falta saber o que aparece na tela ao tocar no microfone.
- O usuário não disse se, na 1.4.28, a tela do torrent passou de 90 s sem erro.
- O usuário não respondeu às quatro dúvidas dos presets: pastas, o que o preset leva, onde aplicar e backup. Foi seguido o recomendado, e ele pode mudar.
