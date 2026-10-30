# Atalhos de sites e busca externa — investigação 29/10/2026

Pedido exploratório do usuário, sem implementação autorizada por esta investigação e sem APK. Skill reverse-engineer-anything consultada; alvo HTML público, não binário. Não houve REA MCP/decompilação nem execução de APK; inspeção HTTP GET limitada de formulários e ícones, sem download de quadrinhos.

## Evidência observada

- https://site.soquadrinhos.com/: GET200, WordPress, formulário GET action raiz com campo `s`; ícones declarados em link rel=icon, inclusive192px.
- https://site.ds-club.net/: GET403. Mecanismo de busca/extração ainda não confirmado. Isso não prova indisponibilidade na WebView do usuário.
- https://dcinfinita.blogspot.com/: consulta web429, GET local200; Blogger/favicon declarado. Endpoint de resultados não validado nesta investigação.
- https://sq-apresenta.blogspot.com/: GET200; formulário GET `/search`, campo `q`; favicon declarado.
- https://podermarvel.blogspot.com/: GET200; Blogger/favicon declarado. GET `/search?q=marvel`200 com títulos correspondentes.
- https://renegadoscomics.blogspot.com/: GET200; formulário GET `/search`, campo `q`; favicon declarado. GET `/search?q=batman`200 com títulos correspondentes.
- https://archive.org/advancedsearch.php?q=batman%20AND%20mediatype%3Atexts&output=json&rows=1: GET200/JSON, response.numFound6449. Contagem é resultado de metadados, não prova arquivo disponível ou direito de download. A home usa JavaScript.
- Fonte primária Blogger: https://developers.google.com/blogger/docs/3.0/reference/posts/search documenta busca por blogId/q e retorno de posts; integração oficial exige configuração adequada, não presumir API disponível sem chave/configuração.

## Proposta pendente

Home do navegador: cartões dos sete sites, ícone obtido do próprio domínio/cache com iniciais de fallback, ação abre no navegador existente. Possibilidade de favoritos editáveis, sem WebViews carregadas em segundo plano para cada ícone.

Busca: destinos selecionáveis + atalhos de termos; abrir busca do site é distinto de agregar resultados nativos. Para resultados nativos, adaptadores por plataforma/site com origem, título/capa/link, cache, cancelamento, limites e estado de falha separados. Falha não deve ser apresentada como zero resultados. Google com restrição site: é alternativa de navegação, sem promessa de índice completo. Não abrir sete abas/carregar sete WebViews a cada tecla.

Não há garantia de resultado para Batman/DC/Marvel em todos os domínios. Site que bloqueia automação permanece navegável manualmente se funcionar na WebView, sem tentar contornar bloqueios. Biblioteca local deve aparecer separada dos resultados da web; nenhum resultado indica automaticamente arquivo baixável ou importado.

## Implementação posterior autorizada

Usuário confirmou manter os7sites e autorizou fechar/publicar pacote. Implementação1.4.2 adiciona home comícones, consultas públicas WordPress/Blogger/Archive e fallback no navegador. Não altera escopo para sites sugeridos depois. Ver docs/validation/1.4.2.md.
