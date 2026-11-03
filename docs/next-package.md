# Próximo pacote do Comic TV

Os seis pedidos de 02 e 03/11/2026 entraram na 1.4.29 (código 103): carrossel dinâmico, configurações da TV no celular, chave do neon, torrent sem "0 conexões", lista de complementos pelo celular e filtro de fontes. Detalhes e decisões em `docs/validation/1.4.29.md`. Nada foi testado na Box nem no iPhone.

**Não implementar nem publicar antes do aviso do usuário.** Ao receber um pedido novo, listar dúvidas e sugestões antes de alterar, e perguntar quem publica: a autorização de publicar vale só para um lote.

## Regras do usuário para este trabalho

- Função com efeito ou movimento sempre tem chave para desligar, nas configurações da TV e do celular.
- O layout do Controle continua congelado, e os temas mudam só as cores. Só muda se o usuário pedir.
- Comportamento só é confirmado no aparelho: compilar e testar não prova que funciona na Box ou no iPhone.

## Ainda aberto

- O microfone do Navegador da TV não foi corrigido. Falta saber o que aparece na tela ao tocar no microfone.
- O usuário não disse se, na 1.4.28, a tela do torrent passou de 90 s sem erro.
- O usuário não respondeu às quatro dúvidas dos presets: pastas, o que o preset leva, onde aplicar e backup. Foi seguido o recomendado, e ele pode mudar.
