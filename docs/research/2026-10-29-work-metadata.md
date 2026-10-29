# Consulta humana de metadados —1.3.0

Ação explícita em Busca global ou Detalhes → Sobre a obra. Nenhuma consulta em segundo plano, escrita na biblioteca, associação automática, credencial ou nota de adaptação. Até6 candidatos por fonte; seleção abre ficha somente para leitura. Título/autor/ano/ISBN devem ser conferidos pelo usuário: obras podem reunir vários volumes/edições. Open Library não garante sinopse ou avaliação; AniList averageScore usa escala100 e não fornece contagem nesta consulta.

Fontes oficiais consultadas29/10/2026:
- https://openlibrary.org/developers/api — consultas humanas, cache, limite padrão1/s. Identificação ComicTV no User-Agent, sem inventar contato do proprietário.
- https://openlibrary.org/dev/docs/api/search — resultados em nível de obra; campos seletivos e limite6.
- https://raw.githubusercontent.com/AniList/ApiV2-GraphQL-Docs/master/docs/guide/terms-of-use.md — uso não comercial; proíbe coleta em massa, armazenamento/backup e serviços concorrentes de listas/trackers. Aqui o serviço é consulta complementar pontual de leitura, sem listas/sync AniList. Doação opcional não cria autorização automática; reavaliar termos/licença antes de futura exploração comercial ou mudança para serviço concorrente.
- https://docs.anilist.co/guide/auth/ — dados públicos não exigem OAuth; nenhuma operação de conta implementada. Site docs retornou403 na pesquisa; termos obtidos do repositório oficial acima.

Rede: HTTPS origens fixas; sem redirects; cancelamento cancela Call; timeout18s; resposta máxima256KiB; intervalo mínimo1100ms por instância; somente um lookup simultâneo. Cache descartável no cacheDir/work-metadata:24h,16 respostas, no máximo4MiB; cópia antiga explicitamente identificada quando a rede falha. Fora da tela/segundo plano cancela consulta. Cache não é backup nem armazenamento de conta.

Sinopse é texto plano limitado, tags/script removidos; não há WebView/HTML executável. URLs de atribuição são construídas de IDs validados, não aceitas do payload. Sem imagens remotas adicionais. Google Books/ComicVine omitidos por exigir chaves não fornecidas. IMDb não integrado: nota de filme não representa a HQ/livro original.

Testes fixture: proveniência/ID/ausência de nota, rejeição de anime e nota inválida, remoção de HTML, limite6 e contagem nula. Build/testes executados pelo coordenador; nenhuma validação física da Box nesta etapa.
