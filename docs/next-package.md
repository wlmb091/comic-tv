# Próximo pacote do Comic TV

Os seis pedidos de 02 e 03/11/2026 entraram na 1.4.29 (código 103): carrossel dinâmico, configurações da TV no celular, chave do neon, torrent sem "0 conexões", lista de complementos pelo celular e filtro de fontes. Detalhes e decisões em `docs/validation/1.4.29.md`. Nada foi testado na Box nem no iPhone.

**Não implementar nem publicar antes do aviso do usuário.** Ao receber um pedido novo, listar dúvidas e sugestões antes de alterar, e perguntar quem publica: a autorização de publicar vale só para um lote.

## Regras do usuário para este trabalho

- Função com efeito ou movimento sempre tem chave para desligar, nas configurações da TV e do celular.
- O layout do Controle continua congelado, e os temas mudam só as cores. Só muda se o usuário pedir.
- Comportamento só é confirmado no aparelho: compilar e testar não prova que funciona na Box ou no iPhone.

## Lote 1.4.30 — Codex, autorizado em 03/11/2026

Usuário aceitou as cinco recomendações e autorizou as correções. Registro: `docs/validation/1.4.30.md`. Foco do player, diagnóstico de vídeo, preparação com imagem/chave, troca de player por vídeo e janela torrent implementados localmente. Teste nativo isolado no HY320 atravessou o arquivo sintético de 1,5 GB com menos de 500 MB livres, sem crescimento acumulado do cache. O buffer usa disco limitado; não é exclusivamente RAM. Testar o filme e a interface após gerar o APK.

**Quem gera o APK e publica: Claude, quando o usuário pedir após o aviso do Codex.** O Codex não gera APK nem publica este lote. A base continua 1.4.29/code103 até o fechamento pelo Claude.

**Legendas no mesmo lote (Claude Code, 03/11/2026):** importar arquivo pela TV, buscar de novo nos complementos, legenda lembrada por vídeo com a sincronia, passos de 0,1/0,5/1 s e Zerar, chave na TV e no celular. Registro na seção "Legendas" de `docs/validation/1.4.30.md`. Versão ainda 1.4.29/code103 até o fechamento.

## Ainda aberto

- Player da TV (03/11/2026, análise do vídeo `player-tv.mp4`): tela preta com som, menus que não voltam, imagem 16:9 na preparação e troca de player no menu do vídeo. Análise, causa dos menus e dúvidas em `docs/pending/player-tv-2026-11-03.md`. Implementação e validação local em `docs/validation/1.4.30.md`; testar a interface e o filme após o APK novo.

- O microfone do Navegador da TV não foi corrigido. Falta saber o que aparece na tela ao tocar no microfone.
- O usuário não disse se, na 1.4.28, a tela do torrent passou de 90 s sem erro.
- O usuário não respondeu às quatro dúvidas dos presets: pastas, o que o preset leva, onde aplicar e backup. Foi seguido o recomendado, e ele pode mudar.
