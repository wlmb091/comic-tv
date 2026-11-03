# Aba TV — implementação local em 03/11/2026

Escopo autorizado: Filmes, Séries e Animes via complementos Stremio por URL; fontes diretas e torrents; controle da reprodução pelo celular. Jogos futuros. Legendas externas SRT/VTT e ASS/SSA com estilos ficam na fase seguinte, conforme sequência comunicada ao usuário.

## Código implementado

- Protocolo com manifesto, catálogos, metadados, streams e descoberta addon_catalog; recursos e IDs por tipo/prefixo, links stremio:// e HTTP(S), caminhos configurados e queries preservados.
- Registro privado de complementos, confirmação por prévia, ativação/remoção, catálogo Cinemeta opcional, identificação manual de catálogos de anime, favoritos e progresso separados das HQs.
- Aba TV na navegação compartilhada dos temas, descoberta e filtros declarados, temporadas/episódios e fontes por complemento.
- Player Media3 1.8.0 para fontes diretas HLS/DASH/progressivas e adaptador de torrents libtorrent4j 2.1.0-38. Metadados canceláveis, cache delimitado, leitura condicionada a peças verificadas e flush de disco, liberação ao sair/background. Versão 2.1.0-39 foi descartada porque declara mínimo API28; projeto continua API26.
- Área TV no painel do celular: abrir aba, pausa/continuar, seek/parar e prévia/instalação de complemento. URLs configuradas não são expostas no estado de reprodução, logs ou backup genérico. Nonce de instalação expira em cinco minutos e é consumido uma vez.
- Layout congelado do Controle não foi reordenado; área TV própria. Sem efeitos de movimento novos.

## Verificação

Logs locais em .superpowers/sdd/2026-11-02-tv-stremio/.

- Testes iniciais protocolo: RED por classes ausentes e GREEN após implementação.
- Regressões de protocolo: RED capturado; correções de links, arrays, duplicatas e fileIdx incorporadas.
- Testes persistência: RED capturado para campos nulos/ausentes e sobrescrita Windows; GREEN após normalização de campos e substituição atômica do arquivo.
- Painel: 95 testes Node passaram, incluindo comandos de vídeo, confirmação por nonce e invalidação ao editar URL. git diff --check sem erro no momento desta anotação.
- Compilação Android e testes focados finais: BUILD SUCCESSFUL em 1m16s no log verify.log, com testes core Stremio/nonce/Companion e app TvStoreTest. As falhas antigas de referências top-level desapareceram com compilação sem incremental. Agregação de detalhes/fontes tem limite total de 60s, preserva resultados e cancela requisições pendentes.

## Limites e pendências físicas

Não houve reprodução real na Box, instalação no iPhone, seeder de torrent local controlado ou medição de desempenho. A compilação não prova codec, seek, cores/foco físicos ou carregamento JNI. A paridade completa do Stremio não é afirmada: protocolos de mídia especiais, DRM, conta Stremio e legendas externas são itens distintos do primeiro módulo. A distribuição exige revisão das licenças transitivas nativas e verificação de ABI/alinhamento do APK gerado no lote autorizado.

Nenhuma nova versão, APK de distribuição, push ou publicação autorizada/inferida nesta implementação.
