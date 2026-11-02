# EPUB aberto na TV não rola pelo celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.20 (código 94), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Relato
Com um EPUB aberto, a página não rola nem pela Rolagem da Página nem pelo touchpad do Controle.

## Causa, pela leitura do código da 1.4.19
Não reproduzido em aparelho. A rolagem do texto e os comandos do celular não estão ligados:

- **A rolagem do texto é local da tela.** `EpubTextScreen.kt:40` guarda a posição num `ScrollState` próprio, que só anda pelas teclas ↑ ↓ do controle físico (`EpubTextScreen.kt:89-95`) e pela leitura automática. A tela não lê `zoom`, `panX` nem `panY`.
- **Os comandos do celular só mudam o estado de imagem.** `companionScrollTo` e `companionPan` (`ReaderViewModel.kt`) alteram `panY`/`panX`, e só com zoom acima de 100%. No texto, isso não tem efeito visível.
- **O slider fica apagado.** `/reader-state` responde `readerScrollable=it.zoom>1.001f` (`CompanionController.kt`). Em 100% o painel apaga a Rolagem da Página e ignora o toque (`index.html`, `scrollTrack.onpointerdown`).
- **O touchpad envia, mas nada acontece.** No leitor ele manda `reader-pan`; em zoom 100% `companionPan` zera o deslocamento.
- **As setas do celular não ajudam.** Ficam desativadas quando o leitor está aberto (`controlLayout`), por decisão aprovada no layout. Por isso, hoje o texto só rola pelo controle físico.

## Direção da correção (a confirmar ao implementar)
- A tela de texto publica no `ReaderViewModel` a posição (0 a 1) e recebe dele os pedidos de rolagem.
- `/reader-state` passa a responder `readerScrollable=true` e `readerScroll` com a posição real quando a seção é texto e tem o que rolar.
- `companionScrollTo` move o texto, e `reader-pan` com um dedo no touchpad rola o texto na vertical, em vez de mover imagem.
- Sem mudar o layout do Controle: só o que já existe passa a funcionar.

## Pontos a verificar ao implementar
- Zoom da Página não tem efeito em texto: hoje ele continua aceso e muda um valor que a tela ignora. Decidir com o usuário se fica apagado no EPUB.
- Confirmar se DOCX e MOBI usam a mesma tela (mesmo problema).
- O relato é do Controle na TV. A rolagem do texto no Leitor do próprio celular (`#pageText`) não foi testada.
