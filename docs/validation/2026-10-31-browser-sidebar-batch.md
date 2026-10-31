# Navegador e barra lateral: lote de 31/10/2026

Implementado em `feature/comic-tv`. Sem novo APK, versão, push ou publicação: o usuário pediu para não publicar ainda.

## Saída do Navegador em aba vazia

Vídeo `bug-loop-navegador.mp4`, analisado por quadros. A fala não foi transcrita: não há reconhecimento de voz neste PC.

Causa: em aba vazia, Voltar só alternava os controles, ↓ das abas ia para a Home e focar a Home fechava os controles, então o rodapé com Sair ficava inalcançável. Abrir um site desligava esse desvio, o que explica o contorno relatado. Veio da correção de 30/10 (`2026-10-30-browser-home-focus.md`).

Correção:
- Aba vazia: Voltar com controles abertos vai à Home; Voltar na Home sai do Navegador. Com site aberto nada muda.
- ↑ na primeira fileira de sites reabre os controles.
- Ícone de Sair desenhado em Compose, porque o caractere ⏻ aparecia como retângulo vazio na TV.

Limite que permanece: em aba vazia, o rodapé (Fechar aba, Mais opções, Sair) continua inalcançável pelo D-pad, porque o desvio de ↓ foi mantido. Downloads, Favoritos e Histórico seguem acessíveis pela própria Home.

## Cores do tema no Navegador

Controles, abas, campo de endereço, barra de progresso e painel de Downloads usam `TvColors`. Texto em foco usa `OnFocusAccent`. Alto contraste mantém fundo preto. O conteúdo dos sites não muda.

## Barra lateral na Biblioteca

Só a barra se move, por `offset`, sem medir o conteúdo de novo. Posição inicial: Comic 25dp e Flix 5dp, contra 48dp da área segura compartilhada. Valores estimados pelas fotos com a marcação vermelha. Outras seções e temas não mudam.

`HomeRows127Test` afirmava que a barra não tinha margem à esquerda, o que já não valia com a área segura de 48dp. Agora ele compara com a posição aprovada.

## Verificações

- `build/batch-browser-sidebar.log`: BUILD SUCCESSFUL em 3m38; 53 testes JVM do recorte, 0 falhas; testes nativos compilados.
- `build/batch-browser-sidebar-lint.log`: lint release com 0 erros e 100 avisos.
- Testes nativos não executados: sem ADB nem emulador. Posição da barra, cores e D-pad ainda não foram vistos na Box.
