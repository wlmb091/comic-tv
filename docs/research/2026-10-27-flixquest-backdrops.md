# Investigação estática do APK: backdrops e pôsteres

Data: 27/10/2026. Somente leitura do pacote fornecido; APK não instalado nem executado. Nenhum endpoint do pacote consultado. Somente documentação oficial pública consultada na web. Não foram retidos valores de chaves/tokens.

## Identidade e método

- Arquivo: `C:/Users/wlmb/Downloads/app-armeabi-v7a-prod-release.apk`
- Tamanho: 36.808.723 bytes.
- SHA-256: `320ed9cb0bec19e6a2b620ebf92efeb2a410e7b8c41bc439a1beb7e4ba87b174`
- aapt: pacote `dev.beamlak.flixquest_v2`, versão 4.3.0, versionCode 10, minSDK 24, targetSDK 36.
- 2.634 membros ZIP. Flutter: `lib/armeabi-v7a/libflutter.so`, `lib/armeabi-v7a/libapp.so`, `assets/flutter_assets/`.
- Skill REA e guias de recuperação/Android lidos. Sem ferramentas REA/JADX disponíveis nesta sessão; nenhuma instalação ou configuração alterada. Evidências abaixo são observações de aapt/ZIP/strings locais, não REA Evidence IDs nem decompilação de fluxo.
- Strings ASCII do binário AOT podem incorporar um byte imprimível adjacente (por exemplo, sufixo `2`). Os offsets identificam bytes nos membros ZIP descomprimidos, não no APK comprimido.

## Resultado

A explicação fortemente corroborada é uso de arte horizontal independente do pôster, obtida do ecossistema TMDB. Não é necessário ampliar ou esticar uma capa vertical: os modelos distinguem `poster_path` de `backdrop_path`, o pacote referencia o CDN público TMDB e tem componentes nomeados para backdrop. As capturas fornecidas são compatíveis com essa arquitetura, mas não permitem atribuir a origem de cada imagem específica sem observar a execução.

Evidências em `lib/armeabi-v7a/libapp.so`:

| Offset | Evidência literal | Interpretação limitada |
| --- | --- | --- |
| 0x3ae16c | TmdbApi | Integração TMDB presente |
| 0x5144cc | TmdbRepository | Repositório TMDB presente |
| 0x4da9dc | TmdbTitleDetailsSource | Fonte de detalhes TMDB presente |
| 0x3fccec | poster_path | Campo separado para pôster |
| 0x4debdc | backdrop_path | Campo separado para fundo |
| 0x413e3c | backdrops | Coleção de fundos |
| 0x47ddcc | Backdrops.fromJson | Modelo de resposta de fundos |
| 0x4d67fc | SQL CREATE TABLE playlist_items com poster_path TEXT e backdrop_path TEXT | Modelo persistido pode guardar os dois caminhos separadamente; não comprova linhas reais em banco |
| 0x4e1b34 | DetailsBackdrop | Componente nomeado para fundo dos detalhes |
| 0x3c0ba4 | TvBackdrop | Componente nomeado para fundo de TV |
| 0x52ba0c | api.themoviedb.org | Host da API pública TMDB |
| 0x54b96c | https://image.tmdb.org/t/p/ | Base do CDN de imagens |

A busca delimitada em strings ASCII dos membros .so/.dex/.json não trouxe referência fanart; isso não prova ausência em execução/configuração remota.

## Resolução e cache

O binário contém `w1280` (0x418144), `w1280/` (0x44c904), `w780/` (0x3dc33c), `w500/` (0x4ab864), `original/` (0x42d6c4), além de URLs explícitas do CDN com `w300` (0x410254) e `w185` (0x546b14). Isso confirma variantes de tamanho no pacote. Sem recuperar os locais de chamada, não é possível afirmar que o hero usa sempre w1280, original, uma resolução específica ou uma proporção específica. Também não foram medidas dimensões efetivamente baixadas.

Há referências a `cached_network_image` e `flutter_cache_manager`, incluindo `DefaultCacheManager` (0x3be49c), `ImageCacheManager` (0x3e9e3c), `memCacheWidth` (0x51f00c), `memCacheHeight` (0x51d694), `cacheWidth` (0x4dcd8c), `stalePeriod` (0x3d392c), `maxNrOfCacheObjects` (0x4125ec), `validTill` (0x455a3c) e `CacheObjectProvider` (0x5184ac). Isso indica infraestrutura para cache/decodificação de imagens, mas não determina quotas, TTL, número de imagens antecipadas ou parâmetros efetivamente usados nessa tela. Não se deve aplicar ao app os padrões da versão atual dessas bibliotecas sem identificar a versão/configuração embarcada.

O caminho `storage/emulated/0/FlixQuest/Backdrops` (0x3c0b4c) também aparece. Pode estar relacionado a salvar/exportar fundos; apenas a presença da string não comprova ser o diretório de cache.

Há símbolos `tmdbProxyEnabled`, `TmdbProxyInterceptor` e `tmdbProxyUrl`; portanto a rota real pode variar com configuração/proxy. Não foi validado se essa alternativa está ativa.

## Documentação oficial consultada

- https://developer.themoviedb.org/docs/image-basics — a URL combina base, tamanho e caminho da imagem; base/tamanhos vêm de configuração.
- https://developer.themoviedb.org/reference/configuration-details — configuração informa endereços e tamanhos válidos.
- https://developer.themoviedb.org/reference/movie-images — a API de imagens retorna backdrops, posters e logos para o filme.

Essas páginas explicam o serviço, sem provar quais chamadas o APK realizou. Nenhuma credencial do APK foi usada.

## Aplicação possível no Comic TV

É possível adotar o mesmo princípio visual: campo opcional de arte horizontal distinto da capa, com recorte proporcional e degradê escuro sobre o fundo, preservando a capa original na frente. A arte pode ser fornecida pelo usuário ou por uma fonte que exponha esse recurso com identificação confiável e permissão de uso. Cache e decodificação devem respeitar os limites de memória da TV e o tamanho de exibição, sem copiar parâmetros não confirmados do outro app.

TMDB descreve filmes/TV; não fornece garantia de correspondência para toda HQ/edição. Não se deve vincular uma HQ à arte de uma adaptação só porque o título coincide. Uma implementação para HQs precisa de associação explícita ou identificação adequada, com fallback para o fundo escolhido no aplicativo. A1.1.22 corrige os temas neutros para fundo sólido#0F0F0F e conserva os fundos coloridos; o pedido cinematográfico está em estudo separado e não foi implementado por esta investigação.

## Perguntas e limites

- App/stack: respondida estaticamente.
- Banco/API de arte: integração TMDB e campos separados corroborados; rota ativa não observada.
- Fundo independente da capa: modelo confirma suporte; associação exata a cada screenshot não observada.
- Resolução: variantes presentes; seleção concreta do hero não recuperada.
- Cache: bibliotecas/mecanismos presentes; quotas/TTL/antecipação efetivos não recuperados.
- FPS, uso real de RAM e desempenho: não medidos.

Arquivos auxiliares locais em build/apk-investigation: safe-strings.json e additional-safe-strings.json conservam offsets/strings selecionadas, com scripts de coleta. Não contêm valores de credenciais e não fazem parte do aplicativo.
