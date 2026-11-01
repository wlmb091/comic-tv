# Controle do celular — layout para uma mão (01/11/2026)

Status: **IMPLEMENTADO LOCALMENTE em 01/11/2026 (usuário autorizou começar). Sem versão, sem push, sem publicação; Box/celular PENDENTES.**
Fonte: duas fotos do usuário (Touchpad com Próxima/Anterior; Touchpad com Rolagem da Página) e respostas abaixo. **Posição e tamanho dos botões seguem as fotos.**

## Objetivo
Layout pensado para uso com uma mão: botões da coluna lateral grandes e próximos entre si, ao alcance do polegar. Tamanho e proximidade são requisito.

## Coluna lateral direita (caixa vermelha das fotos)
| Contexto na TV | Conteúdo |
|---|---|
| Modo leitura | **Próxima ›** (cima), contador **n / total** (meio), **‹ Anterior** (baixo); botões altos |
| Navegador da TV | Slider vertical **Rolagem da Página**, sem contador nem porcentagem |
| Tela principal do app (Biblioteca, Catálogo, Detalhes etc.) | Mesmo slider; de cima para baixo, como controle comum |

- Slider no navegador: escondido quando a página não tem rolagem do documento (continua valendo "absoluta, só com rolagem do documento" do lote 1.4.13).
- Na tela principal do app, o slider rola a lista/grade em foco na TV (comportamento exato a definir na implementação).

## Não muda
Abas Touchpad/Setas; área do touchpad ("um dedo move · pinça amplia e arrasta junto"); Voltar e Menu abaixo do touchpad; Ajustes de gestos (Página, Zoom da Página, Sensi. leitor); Restaurar zoom · 100%; Mais ajustes recolhido; alça de recolher na base.

## Muda
- Coluna lateral passa a depender do contexto (leitura × navegação).
- Próxima/Anterior maiores e mais próximos, com contador entre eles.
- **Girar sai do topo da coluna** (decisão do lote 1.4.13) e vai para a linha inferior: **Inverter · Girar · Teclado**.

## Inverter (botão para canhotos)
Espelha somente o **lado** da coluna lateral (direita ↔ esquerda). **Não** inverte a orientação da leitura nem o sentido de Próxima/Anterior. O usuário aceita que o rótulo seja "Espelhar" se for mais claro.

## Cursor do mouse no navegador (pedido de 01/11/2026)
Problema: o ícone atual (círculo com ponto) atrapalha a leitura de textos pequenos.
- **Tamanho:** slider "Tamanho do mouse" em *Mais ajustes* do painel do celular, 50%–300% em passos de 25%. Também na TV: Opções do navegador → "Tamanho do cursor" (50/75/100/150/200/250/300).
- **Ícone:** *Círculo com ponto* (atual, continua o padrão), *Ponto* e *Seta de mouse* (ponta da seta é o ponto do clique). Escolha no celular (botões Círculo/Ponto/Seta) e na TV (Opções do navegador → "Ícone do cursor").
- Valores salvos no estado do navegador (`cursorSize`, `cursorStyle`); estados antigos mantêm 100% e círculo. No celular os controles só aparecem com o navegador da TV aberto (ponte `BrowserCursorBridge`); comando novo `cursor` validado no servidor (50..300, estilo 0..2).

## Implementado
- Coluna lateral única por contexto (Próxima/Anterior no leitor; Rolagem da Página no navegador, escondida sem rolagem do documento, e na navegação do app, onde o slider rola a tela por distância arrastada).
- Voltar/Menu sob o touchpad; linha inferior Inverter · Girar · Teclado; Girar saiu do topo da coluna.
- Inverter espelha só o lado da coluna (lembrado neste celular, `localStorage`); Rolar ↑/↓ antigos removidos.

## Pendências
- Seguir o fluxo de duas pastas do AGENTS.md: definir de quem é cada tela; commits só locais, sem push/GitHub; publicação pelo Codex, salvo autorização explícita para o Claude Code.
- Validação física no iPhone/Android e na Box.
