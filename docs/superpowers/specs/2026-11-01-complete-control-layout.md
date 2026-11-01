# Controle do celular — layout "Completo" aprovado (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.15 (código 89), AUTORIZADO PELO USUÁRIO EM 01/11/2026. Nada executado na Box nem no celular.** Registro: `docs/validation/1.4.15.md`.

Referências (do usuário): `docs/design/companion/complete-layout-2026-11-01.png` (layout) e `docs/design/companion/complete-layout-functions-2026-11-01.png` (tabela de funções). Os números "100" e "0" e os textos de rótulo da imagem são provisórios; valem a tabela e este documento.

Motivo: o layout de uma mão da 1.4.14 deixou botões longe do dedo (Voltar/Menu sob um touchpad alto; Rolagem da Página em coluna comprida). O usuário segura o celular mais ao centro, às vezes com o dedo mínimo apoiando, e quer também servir quem usa as duas mãos.
Prioridade de uso, informada por ele: Touchpad ou Setas; Zoom/Mover; Rolagem da Página; Linha do tempo da página; Voltar e Menu; o restante. Sensibilidades ficam recuadas.

## Layout (de cima para baixo)
1. **‹ Voltar | ☰ Menu**, grandes, no topo (sem as abas Touchpad/Setas).
2. **Setas** ↑ ← OK → ↓, grandes.
3. **‹ Anterior | contador | Próxima ›**.
4. **Touchpad**, mais baixo que o da 1.4.14.
5. **Esquerdo | Direito**, botões de clique.
6. **Coluna de sliders à direita**, empilhados, com botão em pílula e trilho longo: **Zoom da Página** (em cima) e **Rolagem da Página** (embaixo, Início no alto e Fim embaixo).
7. **▸ Ajustes de gestos** recolhido.
8. **Inverter · Girar · Teclado** na base, e a alça de recolher a navegação.

Todos os controles aparecem sempre, nos três contextos: o que não vale no contexto fica **apagado, no mesmo lugar**, para o dedo não aprender posições diferentes.
O Inverter espelha a coluna dos sliders para o outro lado, sem inverter comandos.

## Função de cada controle por contexto (aprovado)
| Controle | Leitor (na TV) | Navegador | Navegação do app |
|---|---|---|---|
| Voltar | Fecha o leitor | Voltar do navegador | Voltar da TV |
| Menu | Home do app | Home do app | Home do app |
| Setas | Movem a imagem ampliada | Movem o mouse | Foco da TV |
| OK | Enter na página | Enter na página | OK |
| Anterior / Próxima | Viram a página | Retrocede / avança na aba atual (histórico) | Apagados |
| Contador | Página/total | Oculto | Oculto |
| Touchpad | Move a imagem; pinça dá zoom | Cursor do mouse; 2 dedos rolam | Navega a tela da TV |
| Esquerdo | Retrocede a página | Clique | OK |
| Direito | Avança a página | Opções do link (toque longo) | OK longo |
| Slider Zoom | Zoom real, de baixo para cima, 100%–300% | Zoom da página, de baixo para cima, 100%–300% | Apagado |
| Slider de rolagem | Rolagem da página, de cima para baixo | Rolagem da Página (documento) | Rolagem relativa da tela |
| Sensibilidade | Velocidade do touchpad | Do mouse | Velocidade do touchpad |
| Inverter / Girar | Iguais | Iguais | Iguais |
| Teclado | Apagado | Ativo | Ativo |

## O que muda em relação à 1.4.14 (pontos para a implementação)
- **Rolagem no leitor:** a tabela define o slider do leitor como rolagem da página, de cima para baixo (posição vertical da imagem ampliada), e não como Linha do tempo. A **Linha do tempo (Página n/total)** continua no painel Ajustes de gestos, a menos que o usuário peça outra posição. Exige comando de leitor absoluto (hoje `reader-pan` é relativo) e a posição vertical no estado `/reader-state`.
- **Setas no navegador movem o mouse:** reverte o comportamento da 1.4.12 (setas do celular como teclas da página, `browser-up/down/left/right`). OK continua como Enter na página. Setas passam a usar o mesmo movimento das setas físicas da TV.
- **Esquerdo/Direito:** botões novos. No navegador, Esquerdo = `browser-click`, Direito = `browser-context`. Na navegação do app, OK e OK longo (a TV já trata OK longo como opções). No leitor, Esquerdo/Direito voltam e avançam a página.
- **Anterior/Próxima no navegador:** histórico da aba (`goBack`/`goForward`); comandos novos no servidor do painel e na lista de ações permitidas (o teste Node cruza botões × lista).
- **Rolagem no app:** continua relativa (distância arrastada), como na 1.4.14.
- **Zoom no navegador:** hoje é relativo e começa em 100%; manter 100%–300% e mostrar a porcentagem real.
- **Abas Touchpad/Setas:** removidas, pois setas e touchpad aparecem juntos; layout único, sem alternância (ver decisão abaixo).
- A altura total exige cerca de 850 px; em telas baixas ou deitadas, compactar os botões ou permitir rolagem leve. O touchpad menor pesa na precisão do cursor; sensibilidade compensa. Medir no celular.

## Decisão: layout único (01/11/2026)
O usuário decidiu **unificar: não haverá alternância entre layouts**. Este layout Completo é o único modo do controle, em todos os contextos, e substitui o layout de uma mão da 1.4.14 e as abas Touchpad/Setas. O Inverter (espelhar o lado dos sliders) continua. A ideia de um seletor "Completo | Uma mão" foi descartada por enquanto.

## Barra inferior recuável em todas as abas (pedido de 01/11/2026)
Recuar a barra inferior do painel (Biblioteca, Leitor, Controle, Arquivos) em **todas** as abas, como já funciona no Leitor e no Controle (alça ⌃/⌄ que esconde as quatro abas e deixa só a alça).
- Hoje (1.4.14): a alça `#navToggle` e o estado `tabsFolded` só existem nos modos `controlMode` e `readerMode`; em Biblioteca e Arquivos a barra fica sempre aberta.
- A decidir na implementação: lembrar se a barra estava recuada ao trocar de aba e entre aberturas do painel (localStorage), e o que fazer ao tocar numa aba com a barra recuada (a alça reabre).
- Atenção com a altura no layout Completo e com o teclado aberto (a barra já cede espaço ao teclado).

## Em aberto
- Slider em tempo real: ver "Ajuste futuro" em `2026-11-01-one-hand-control-layout.md`; vale também aqui.

## Fluxo
Seguir o fluxo de duas pastas do `AGENTS.md`: definir no início do lote de quem é cada parte (layout e foco ficam com o Claude Code; funções novas como histórico e Esquerdo/Direito podem nascer com o Codex). Commits só locais, e publicação só com autorização explícita do usuário.
