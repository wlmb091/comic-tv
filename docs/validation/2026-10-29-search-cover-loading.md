# Busca: demora e indisponibilidade das miniaturas — pacote aberto

Pedido após 1.4.1: investigar/corrigir a foto `codex-clipboard-a4322e9b-a4bd-42b4-b6d3-b874fad37bb8.png`. Usuário ainda acrescentará alterações: **não gerar APK, incrementar versão, fazer push ou publicar**.

## Diagnóstico

1. `globalSearchCover` colocava consultas de detalhes e downloads na mesma fila de três vagas. Títulos sem thumbnail monopolizavam vagas enquanto capas com URL conhecida aguardavam.
2. `Semaphore.acquire()` estava fora do orçamento de tempo, portanto a espera na fila não era limitada.
3. Timeout de metadados/imagem era `TimeoutCancellationException`; `SearchSourceCover` o propagava como se a tela tivesse sido fechada. O estado visual continuava nulo/Carregando capa indefinidamente enquanto o produtor já havia encerrado.
4. Composição antecipada da LazyGrid também iniciava requisições. Endereços obtidos por detalhes não eram memoizados independentemente do download: cancelamento ao rolar podia repetir a consulta de detalhes.
5. Erro de endereço antigo não tentava recuperar o endereço atual da própria obra. Falhas de rede, ausência de imagem e limite de tempo tinham a mesma mensagem genérica.

Fonte primária consultada: https://github.com/keiyoushi/extensions-source/blob/main/src/pt/hqnow/src/eu/kanade/tachiyomi/extension/pt/hqnow/HQNow.kt

No fonte público da extensão HQ Now, a busca não pede `hqCover`; a consulta por ID pede. O cliente também configura limite de uma requisição a cada dois segundos. Em uma consulta diagnóstica limitada do PC, `getHqsByName("DC Rebirth")` trouxe33títulos,33semcapa; consultas por ID de898/928/1964 trouxeram URL. Tempos da API observados0,339–0,483s, busca0,431s. HEAD das imagens respondeu403; **GET normal da capa Action Comics898 respondeu200/image/jpeg em0,32s**. Não interpretar o HEAD como prova de capa ausente nem extrapolar essa medição para a Box. Não houve coleta de páginas de capítulos ou alteração da extensão.

## Correção

- `SearchCoverLoader`: filas independentes (2 metadados,3 imagens), coalescência por obra e prazo total40s incluindo admissão; prazos por etapa12s. Respeita cliente/cabeçalhos/cookies/TLS/limites da extensão.
- Somente itens realmente visíveis e tela STARTED iniciam trabalho. Pré-composição não compete; sair da tela cancela o produtor sem gravar falsa falha.
- URL resolvida permanece na memória mesmo se o download for cancelado. Reutiliza URLs já vistas no catálogo/detalhes. Cache bounded512URLs/256falhas; espera30s antes de repetir falha por rolagem. Uma nova busca explícita pode tentar de novo.
- Erro de endereço conhecido pode consultar metadados atualizados uma vez e baixar somente se houver endereço diferente. HTTP429 não provoca esse pedido extra.
- Timeout próprio é estado terminal distinto de cancelamento da tela. Mensagens distinguem fonte lenta, sem capa, HTTP403/404/429 e falha de rede.
- URLs novas obtidas nos detalhes limpam imediatamente falhas negativas daquela obra. Voltar à busca não reinicia a busca global nem muda foco/rolagem; só miniaturas visíveis retomam.
- Cache de imagem continua48MiB/4MiBporimagem, reserva64MiB, validação48MP. Chave por identidade da obra; reaproveita entradas da1.4.1 por migração local. Originais/progresso/coleções/leitor/USB não alterados.

## Verificação

Teste escrito antes da implementação do novo carregador: a primeira compilação falhou pela ausência do novo contrato (log `build/search-covers-red.log`), não constitui execução do bug antigo. Depois, nove casos do carregador verificam timeout terminal, espera limitada, não bloqueio de imagem conhecida por metadados lentos, cancelamento/cache, coalescência, endereço atualizado, cooldown/nova busca, atualização pelos detalhes e reaproveitamento de catálogo.

`build/search-covers-final.log`: **BUILD SUCCESSFUL em1m32s**. SearchCoverLoader9 + GlobalSearchSession1 + GlobalSearch5 = **15casos/0falhas/0erros**, compilação AndroidTest aprovada. Verificação anterior da implementação em `build/search-covers-fix.log` também passou. `git diff --check` aprovado. Sem assemble/release/novoAPK/CI/publicação.

Validação física pendente: repetir buscaDCnaBox, aguardar miniaturas visíveis, rolar/voltar e abrir detalhes/retornar. O limite de ritmo da fonte e a rede continuam podendo impor espera; não há promessa de carregamento instantâneo de todas as imagens nem medição de ganho na Box.
