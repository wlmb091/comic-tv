# Chave do Controle tem prioridade sobre o apagado (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Relato sobre a 1.4.20
A chave aparece ligada, mas o grupo continua apagado. O usuário pede:
- Se o grupo começa apagado, a chave começa desligada.
- Ao ligar a chave, o grupo fica ativo.

## Causa
Erro da implementação, em `controlLayout()` (`index.html`). A chave só soma uma condição à regra de contexto: o botão funciona se o contexto da TV permitir **e** a chave estiver ligada. Por isso ela só consegue apagar. As setas no Leitor, por exemplo, seguem apagadas com a chave ligada.

## Decisões do usuário (02/11/2026)
- A chave passa a mandar no grupo, e o estado dela vale **por tela da TV**: navegação do app, Leitor e Navegador.
- O valor inicial de cada tela repete o layout aprovado. Setas: ligadas na navegação, desligadas no Leitor e no Navegador. Touchpad, Anterior e Próxima, Sliders: desligados na navegação, ligados no Leitor e no Navegador.
- A escolha fica salva no celular, em chave nova de `localStorage`.

## O que ligar precisa fazer onde hoje está apagado
Pelo código, hoje esses botões não têm função nesses lugares:

| Grupo | Tela | Função |
| --- | --- | --- |
| Setas | Leitor | Decidido: igual ao controle físico: ← → passam a página, ↑ ↓ ajustam o zoom; em texto, ↑ ↓ rolam |
| Setas | Navegador | Proposta: movem o mouse (como na 1.4.15) |
| Touchpad | Navegação do app | Proposta: arrastar move a seleção, tocar é OK |
| Esquerdo e Direito | Navegação do app | Proposta: Esquerdo é OK; Direito é OK longo |
| Anterior e Próxima | Navegação do app | Decidido: ficam ativos com a chave ligada, mas não fazem nada |
| Sliders | Navegação do app | Decidido: ficam ativos com a chave ligada, mas não fazem nada |

## Limites que continuam
Mesmo ligado, Anterior não volta antes da primeira página, e Próxima não passa da última.

## Regra permanente
Nada muda de tamanho nem de posição. O valor inicial reproduz as funções aprovadas: a mudança só acontece quando o usuário liga ou desliga a chave.
