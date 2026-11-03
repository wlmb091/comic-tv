# Próximo pacote do Comic TV

Pedidos aprovados pelo usuário em 02/11/2026, ainda não implementados. A última versão publicada é a 1.4.26 (código 100), sem teste no iPhone nem na Box; decisões em `docs/validation/1.4.26.md`.

**Não implementar nem publicar antes do aviso do usuário.** Ele ainda pode acrescentar pedidos e vai testar a 1.4.26 no aparelho. Ao receber o aviso, listar dúvidas e sugestões antes de alterar, e perguntar quem publica: a autorização de publicar vale só para um lote.

## 1. Carrossel dinâmico em Flix, Max e Cinema

Em ciclos de 8 segundos, as imagens alternam dois efeitos:

- **Zoom:** afasta devagar e aproxima de novo, com cerca de 8% de variação.
- **Rolagem:** começa no topo da imagem e desce até o fim. Arte sem altura para rolar usa o zoom.

O Prime tem outro carrossel e fica de fora. Alto contraste continua sem movimento.

Uma tentativa foi publicada na 1.2.9 (`699bc24`) e removida na 1.2.10 (`f1c2b89`), porque o carrossel parou na Box. Um único relógio de animação comandava a troca, o movimento e os indicadores, e a escala de animação do sistema podia desativar tudo. A causa na Box não foi provada.

Como refazer:

- A troca continua no temporizador de 8 segundos de hoje, em `HeroCarousel.kt`, sem depender de animação.
- O movimento é só um efeito visual, sem nenhum poder sobre a troca. Se falhar, a imagem fica parada e o carrossel segue.
- Movimento só na fase de desenho, sem recomposição a cada quadro, por causa da Mi Box.
- Teste automático de que a troca acontece sem nenhum quadro de animação.
- Escolha entre Dinâmico e Sem movimento nas configurações da TV e do celular, separada da troca automática. `carouselMotion` ainda existe nas preferências e no backup, sem controle na tela.

## 2. Mais configurações da TV no celular

Sincronizadas nos dois sentidos, em Configurações do painel:

- Orientação da tela
- Troca automática do carrossel e movimento do carrossel
- Filtro do carrossel e intensidade
- Fundo dos detalhes
- Tamanho dos textos
- Alto contraste
- Restaurar aparência padrão, com confirmação

Backup, armazenamento, atualizações e diagnóstico ficam só na TV.

## 3. Chave para o neon do Controle

Só nas configurações do celular, porque o efeito não existe na TV.

## Regras do usuário para este trabalho

- Função com efeito ou movimento sempre tem chave para desligar, nas configurações da TV e do celular.
- O layout do Controle continua congelado, e os temas mudam só as cores. Só muda se o usuário pedir.
- Comportamento só é confirmado no aparelho: compilar e testar não prova que funciona na Box ou no iPhone.

## Ainda aberto

- O microfone do Navegador da TV não foi corrigido.
- O usuário não respondeu às quatro dúvidas dos presets: pastas, o que o preset leva, onde aplicar e backup. Foi seguido o recomendado, e ele pode mudar.
