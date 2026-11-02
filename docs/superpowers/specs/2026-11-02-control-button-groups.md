# Ativar e desativar grupos de botões do Controle (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.20 (código 94), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
Em Ajustes de gestos ou Mais ajustes, opção para ligar e desligar grupos de botões do Controle do celular.

| Grupo que pode ser desligado | Elementos em `index.html` |
| --- | --- |
| Setas | `[data-direction]` |
| Touchpad, com os botões Esquerdo e Direito | `#touchpad`, `#btnLeft`, `#btnRight` |
| Anterior e Próxima | `#tvPrev`, `#tvNext` |
| Sliders | `#zoomTrack`, `#scrollTrack` |

Sempre ativos: Voltar, Menu, OK, Inverter, Girar e Teclado.

Grupo desligado fica **apagado no mesmo lugar**, sem responder ao toque, como os controles fora de contexto.

Cada grupo é ligado e desligado por uma **chave no estilo do iPhone** (interruptor deslizante), uma por linha, com o nome do grupo ao lado.

## Direção (a confirmar ao implementar)
- **Regra permanente do Controle.** Nada muda de tamanho, posição ou ordem (`control-layout-approved-2026-11-01.png`). Com todos os grupos ligados, o Controle funciona como hoje.
- **Regra de contexto.** O botão só funciona se o contexto da TV permitir e o grupo estiver ligado. A regra atual de `controlLayout()` continua valendo.
- **Valor inicial.** Todos os grupos ligados.
- **Onde fica salvo.** No celular, em `localStorage`, como o Inverter. Sobrevive a fechar e reabrir o painel.
- **Onde fica a opção.** Mais ajustes, dentro de Ajustes de gestos, porque a barra visível continua igual.
- **Fora do pedido.** A aba Leitor do celular e o slider Página em Ajustes de gestos não entram nesses grupos.

## Relação com outros pedidos
`2026-11-02-epub-phone-scroll.md`: com Touchpad e Sliders desligados, o EPUB não teria como rolar pelo celular mesmo depois da correção.
