# Menu flutuante universal no painel do celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
O menu flutuante do Leitor da 1.4.20 (foto: seta no topo e, abaixo, cartão com Biblioteca, Leitor, Controle e Arquivos) passa a ser universal em todos os temas e substitui todas as barras de navegação de todas as abas.

## Direção (a confirmar ao implementar)
- Remover as navegações próprias de cada tema (`body[data-theme=...] nav` em `index.html`): lateral do Flix, topo do Prime, dock do Max, gaveta do Cinema e barra do Comic TV.
- Mesma seta e mesmo cartão em Biblioteca, Leitor e Arquivos, mudando só as cores. Sempre recolhido ao abrir o painel e ao escolher uma aba.
- Reservar espaço no topo das abas para a seta não cobrir conteúdo. Em Arquivos, conferir a engrenagem.

## Decisão do usuário sobre o Controle (02/11/2026)
A seta fica no topo também no Controle. Como o menu é flutuante, aberto ele cobre o Controle; para usar o Controle, o usuário recua o menu.

## Pontos a verificar ao implementar
- A seta do topo flutua sobre o Controle e não pode cobrir "Ajustes de gestos".
- A seta sai de baixo. Para nenhum botão mudar de lugar, a faixa de baixo continua reservada e as posições são medidas contra a 1.4.20.
