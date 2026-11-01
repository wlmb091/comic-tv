# HQ Now no Navegador: imagem do leitor não carrega — 01/11/2026

Status: **CORREÇÃO PUBLICADA NA 1.4.14; opção por site na 1.4.15. A causa segue como hipótese até o teste com um capítulo real na Box.** As seções "Opções de correção" e "Pendências" abaixo são o registro original da investigação.

## Relato
No navegador do aplicativo, ao abrir um quadrinho direto no site do HQ Now, o leitor mostra erro e a imagem não carrega. Dúvida do usuário: incompatibilidade do arquivo ou limitação do navegador?

## Evidências (GETs públicos, sem credenciais)
- `getChapterById` em admin.hq-now.com/graphql (obra 1079, capítulo 11572) devolve todas as páginas como `http://static.hq-now.com/hqs/hqs/uploads/picture/image/<id>/NN.jpg`.
- A mesma URL em `http://` responde 301 para `https://`; em `https://` responde 200, `image/jpeg`, via Cloudflare. O arquivo está íntegro.
- O site abre em HTTPS, então carregar essas imagens `http://` é conteúdo misto.
- `BrowserWebViews.kt:41` usa `MIXED_CONTENT_NEVER_ALLOW`, que bloqueia a imagem sem aviso.
- `onReceivedError` / `onReceivedHttpError` (linhas 65-70) só avisam falhas do frame principal, então erros de imagem ficam invisíveis.
- A correção HTTP→HTTPS da 1.4.0 (capas) vale para os leitores internos, não para o WebView do navegador.

## Vídeo do usuário (hqnow-site-bug.mp4, 48s, celular filmando a TV)
Quadros extraídos a cada 2s e os principais ampliados:
- 0-10s: home do HQ Now abre normal; os banners carregam, mas os cartões de obra ficam cinza/vazios (só o título, sem capa). Isso é coerente com imagens do mesmo host sendo bloqueadas.
- ~10-22s: página da obra "Saga do Infinito" (sinopse, lista de capítulos). Texto carrega, sem imagem de capa; só o logotipo "NOW!" de espaço reservado.
- ~24-40s: leitor em "Saga do Infinito - Capítulo 1 - Página 1": no lugar da página aparece o logotipo "NOW!" e o texto **"Ocorreu um erro"**. É o aviso do próprio site quando a `<img>` falha. O seletor de páginas (1 a 52), o botão "Próximo Capítulo" e o rodapé "HQ Now - 2026" carregam normalmente.
- O usuário alterna entre o erro e o seletor de páginas; o erro se repete na página 1, sem nenhuma imagem de quadrinho visível.

O vídeo mostra só a interface; não mostra a rede, então não prova o motivo da falha. Ele confirma que o HTML/JS/API funcionam (lista de 52 páginas e navegação carregam) e que só as imagens de `static.hq-now.com` falham, o que bate com a hipótese de conteúdo misto.

## Conclusão
Causa mais provável: regra de segurança do nosso navegador, não formato de arquivo nem limite do WebView. **Hipótese, não reproduzida na Box.**

## Como confirmar
Abrir a mesma página no Chrome do celular. Se carregar lá e falhar no app, a causa é o conteúdo misto.

## Opções de correção (NÃO implementadas)
1. Reescrever `http://` para `https://` em `shouldInterceptRequest` só para hosts conhecidos (ex.: `static.hq-now.com`). Recomendada: não afrouxa a regra global.
2. `MIXED_CONTENT_COMPATIBILITY_MODE`: resolve, mas reduz a proteção em todos os sites.
3. Registrar erros de recursos (imagens) em diagnóstico.

## Pendências
- Aguardar o aviso do usuário para iniciar as alterações.
- Validação física na Box. Seguir o fluxo: Claude só altera local; Codex publica.

## Imagens HTTP por site (implementado na 1.4.15)
Contexto: a 1.4.14 liberou imagens `http://` só em páginas HTTPS de `hq-now.com` (lista fixa `mixedImageSites` em `BrowserPolicy`). Outros sites com o mesmo defeito continuam bloqueados.
- **Proposta:** item nas Opções do navegador, "Permitir imagens HTTP neste site", ligado por host e lembrado no estado salvo do navegador. O HQ Now já viria liberado. Evita nova versão a cada site.
- **Cuidados:** `MIXED_CONTENT_COMPATIBILITY_MODE` libera imagem, áudio e vídeo, não só imagem (as notas da 1.4.14 dizem "só imagens", impreciso); scripts e requisições continuam bloqueados. Valer só em páginas HTTPS, por host exato ou subdomínio, e desligar com um toque.
- **Alternativa descartada por ora:** liberar para todos os sites (reduz a proteção em toda página).
- **Antes de implementar:** confirmar na Box que o HQ Now carrega com a 1.4.14. Se não carregar, a causa pode ser outra e esta proposta muda; nesse caso diagnosticar erros de imagem no WebView (`onReceivedError` só avisa o frame principal hoje).
- Usuário ainda está reunindo outras alterações; reunir tudo em um único lote.
