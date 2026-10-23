# Validação — Comic TV 0.3.1

23/10/2026. versionCode 5. Incremento da barra de páginas/seções. O usuário confirmou a versão anterior funcionando; esta entrega acrescenta o seletor pelo D-pad.

APK assinado com a mesma chave debug; assinaturas v1/v2 verificadas. minSdk 23, targetSdk 36. SHA-256: `423C265785FDB76586CDB34FFD2191A62CE9CEE0A6417B6EFEBEEA4DDE99131B`.

## Comportamento

No leitor de imagens: OK → Escolher página. No texto EPUB: OK → Escolher seção. A barra abre com um cursor na posição lida, sem alterar a página atual.

Esquerda/direita movem uma posição, respeitando RTL e a direção visual da barra; cima/baixo saltam −10/+10 posições. OK confirma e volta à leitura. Só então o ViewModel carrega e salva a posição escolhida. Voltar cancela e retorna às ferramentas, preservando página, zoom e deslocamento. Confirmar a mesma página preserva a vista atual; escolher outra reinicia o enquadramento, como a navegação normal.

A interface usa o tema escuro/âmbar aprovado e expõe posição/contagem por semântica de acessibilidade. Até 80 marcas são desenhadas, com indicador exato da posição mesmo em livros maiores. Não há extração ou download de páginas enquanto o cursor se move. EPUB de texto usa a contagem de seções; não representa paginação impressa.

## Verificação executada

- `core:test`, `app:testDebugUnitTest`, `app:assembleDebug`, `app:assembleDebugAndroidTest`, `app:lintDebug`: BUILD SUCCESSFUL.
- 50 testes JVM, zero falhas (46 anteriores + 4 novos).
- Reducer: cursor separado, cancelar preserva vista, confirmar muda destino e reinicia enquadramento, limites, salto de dez, RTL, livros vazios e uma página.
- ViewModel: mover cursor não altera imagem/progresso; confirmar carrega o destino, salva no estado e persiste pelo repositório.
- Android Lint: zero erros e 27 avisos, mesma contagem da versão anterior.
- Revisão independente: sem problemas Important/Critical encontrados. Revistos reducer, entradas do controle, foco entre ferramentas/barra/leitura e integração com o leitor de texto.

`adb devices` continua vazio. Testes instrumentados compilaram, mas não foram executados. A navegação e o foco reais do novo seletor ainda precisam do reteste na TV. A confirmação do usuário sobre 0.3.0 não é uma validação física da barra nova.

## Reteste

1. Instalar Comic-TV-0.3.1-debug.apk sobre a versão anterior.
2. Abrir uma HQ no meio da leitura, ampliar e abrir ferramentas → Escolher página.
3. Mover o cursor, usar saltos de dez e cancelar com Voltar: página, zoom e progresso devem permanecer.
4. Abrir novamente e confirmar outro destino com OK. Sair e retomar: deve abrir no destino confirmado.
5. Testar a primeira/última posição, direção RTL e um EPUB de texto em Escolher seção.

ZIP/CBZ, RAR/CBR, PDF e EPUB mantêm o suporte e os limites documentados na validação 0.3.0. Fontes Mihon, HQ Now e a sequência de capas das sagas continuam sendo os próximos incrementos; esta barra é o navegador de páginas do leitor.
