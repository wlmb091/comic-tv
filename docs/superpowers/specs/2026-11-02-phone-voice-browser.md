# Falar um site pelo microfone do celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.20 (código 94), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
Opção de usar o microfone do celular para falar um site ao Navegador da TV. O modo de microfone que o Navegador já tem não está funcionando.

## O que existe hoje, pela leitura do código da 1.4.19
Nada reproduzido em aparelho.

- **Microfone do Navegador da TV.** O botão "Pesquisa por voz" (`BrowserChrome.kt:45`) abre o reconhecimento de voz do próprio Android (`BrowserScreen.kt:184`), e o Comic TV só recebe o texto de volta. O app não grava áudio nem tem a permissão de microfone. Se o Android não oferecer essa tela, aparece "Pesquisa por voz indisponível". A causa da falha na Box ainda não é conhecida.
- **Teclado do Controle.** Envia texto com o comando `text` para o campo selecionado na TV (`index.html`, `keyboardForm`). Fica ativo fora do leitor.

## Limite do iPhone
O painel abre por `http://` na rede local. O Safari só libera o microfone para páginas em HTTPS. Na rede local isso exigiria certificado próprio, com aviso de segurança no celular. Por isso, um botão próprio que grave a voz dentro do painel não é o caminho indicado.

## Direção aprovada pelo usuário (02/11/2026)
- Usar o ditado do teclado do iPhone, o microfone do próprio teclado, que funciona em qualquer campo de texto. O Safari não deixa a página abrir o ditado sozinha: o toque no microfone do teclado é do usuário.
- Na janela do Teclado, com a TV no Navegador, acrescentar "Abrir site", que envia o texto direto para a barra de endereço e navega. Domínio abre o site; o resto vira pesquisa, como a barra já faz.
- Layout aprovado do Controle sem mudança: a janela do Teclado só aparece ao tocar em Teclado.

## Em aberto
- O que acontece na TV ao acionar o microfone do Navegador.

## Pontos a verificar ao implementar
- Ditado costuma escrever "ponto com" e espaços de formas variadas. Avaliar a normalização antes de decidir entre site e pesquisa.
- Testar no iPhone real.
