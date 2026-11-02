# Barras laterais estreitas no Leitor do celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido, sobre a 1.4.20
As barras laterais tapam a página. Nas duas fotos, o usuário marcou em vermelho a largura que elas devem ter, cerca de 10% da tela (perto de 38 px em 365 px).

- **Barra direita:** só ícones.
- **Imagem e mais opções:** vira um ícone de **+**. Ao tocar, abre o resto das opções expandidas, igual à TV.
- **Barra esquerda:** mesma largura, com os sliders de zoom e rolagem.

## Decisões do usuário (02/11/2026)
- **Nome do ícone:** ao tocar, o ícone age e o nome aparece ao lado por 2 segundos, como "Ajuste: Largura".
- **Opções do +:** ficam abertas até tocar no + de novo.

## Direção (a confirmar ao implementar)
- **Ícones da barra direita, na ordem da TV (`ReaderTools.kt`):** Continuar, Escolher página, Capítulo anterior, Próximo capítulo, Capítulos / edições, Visualização, Ajuste, + e Sair da leitura. Tentar novamente aparece só com erro.
- **Dentro do +:** Direção, Girar, Usar página como capa, Filtro, Intensidade, Comparar com original, Leitura automática e Intervalo.
- **Barra esquerda:** a largura não comporta os textos "Zoom" e "Rolagem" nem o botão atual do slider. Proposta: ícones curtos e botão menor. Conferir se ele continua fácil de arrastar.
- **Setas laterais:** continuam encostadas na borda da barra aberta.
- **Texto (EPUB):** a barra direita segue as opções de texto da TV, como Fonte − e Fonte +.

## Regras que continuam valendo
Só o Leitor muda. O layout aprovado do Controle fica igual.
