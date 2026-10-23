# Hikari como referência para o Comic TV

Análise de código em 23/10/2026. Hikari consultado no commit `30f988950090904f93e1f5f31eb18aeab611323a`. Não houve execução do APK nem teste em TV nesta análise. Funcionalidades abaixo são referências ou propostas; não estão implementadas no Comic TV.

## O que foi confirmado

- O Hikari hospeda extensões Mihon/Tachiyomi e usa uma ponte entre fontes, catálogo, capítulos e páginas. A documentação distingue a API combinada de atualização das fontes recentes das chamadas antigas. Isso interessa ao motor de extensões já previsto no Comic TV.
- O leitor oferece navegação por páginas e leitura contínua, retomada de progresso, troca de capítulos e configurações por série. Há cache em disco e decodificação por regiões para imagens grandes. Downloads precisam passar pela própria fonte, preservando seu tratamento de imagens e requisições.
- A barra `ChapterNavigatorPill`, em `YomiReaderChrome.kt`, tem uma posição por página e botões de capítulo anterior/próximo. O código aplica `tvAdjust` à barra: esquerda/direita alteram a página pelo controle remoto. Isso confirma uma possível interpretação da expressão “linha do tempo”, mas não confirma que seja a tela que o usuário descreveu.
- A versão examinada já declara launcher de TV e touchscreen opcional. Contém `TvMode`, `TvFocus`, `TvKeys`, menu lateral e ajustes para seletores. Existência de código não comprova funcionamento correto no aparelho do usuário.

Fontes:
- [Manga e integração de extensões](https://github.com/codegeasse1/hikari/blob/30f988950090904f93e1f5f31eb18aeab611323a/docs/MANGA.md)
- [Arquitetura do leitor](https://github.com/codegeasse1/hikari/blob/30f988950090904f93e1f5f31eb18aeab611323a/docs/READER.md)
- [Barra de páginas e ferramentas](https://github.com/codegeasse1/hikari/blob/30f988950090904f93e1f5f31eb18aeab611323a/app/src/main/java/com/hikari/app/reader/ui/YomiReaderChrome.kt)
- [Controles de TV](https://github.com/codegeasse1/hikari/blob/30f988950090904f93e1f5f31eb18aeab611323a/app/src/main/java/com/hikari/app/tv/TvKeys.kt)
- [Manifest](https://github.com/codegeasse1/hikari/blob/30f988950090904f93e1f5f31eb18aeab611323a/app/src/main/AndroidManifest.xml)

## HQ Now e sagas: confirmado versus pendente

O usuário relata que o Hikari permite adicionar HQ Now e abrir separadamente as sagas organizadas pelo site. Esse comportamento deve orientar a investigação, mas sua implementação específica ainda não foi localizada.

A busca por HQNow e por saga no repositório Hikari não encontrou um adaptador dedicado. Isso é compatível com fontes instaladas externamente, mas não prova qual extensão ou motor o usuário utiliza.

Foi examinada também a implementação atual de `HQNow.kt` no repositório Keiyoushi. Ela consulta uma API GraphQL para populares, atualizações, busca de HQs, detalhes, capítulos e páginas. Esse arquivo não expõe uma operação específica de catálogo de sagas ou ordem de leitura entre séries. Portanto, instalar uma extensão HQ Now não garante, por si só, reproduzir as sagas relatadas. É necessário identificar a fonte concreta usada pelo usuário e o formato que ela entrega.

Fonte: [HQNow.kt](https://github.com/keiyoushi/extensions-source/blob/1d3d7c4b3e876fb0c26ae3ade8113fb329af15fa/src/pt/hqnow/src/eu/kanade/tachiyomi/extension/pt/hqnow/HQNow.kt).

## Aplicação proposta no Comic TV

1. Manter biblioteca local, fontes online e sagas acessíveis pela navegação de TV, seguindo a identidade escura/âmbar aprovada.
2. Tratar uma saga como coleção própria com título, capa e lista ordenada de edições; manter a ordem fornecida pela fonte, inclusive quando mistura séries. Preservar identificação de fonte, série e capítulo em cada item.
3. Permitir abrir uma edição individualmente e retomar a leitura da saga. A relação entre progresso da saga e progresso da edição precisa ser especificada antes de implementar.
4. Avaliar uma barra de páginas acessível pelo painel de ferramentas. O foco na barra usa esquerda/direita para escolher páginas; sair dela devolve o mapeamento normal do leitor, evitando conflito com zoom e deslocamento.
5. Implementar capacidades opcionais por fonte: catálogo comum, sagas/coleções e ordem de leitura. Uma fonte sem sagas continua funcionando no catálogo comum. Compatibilidade Mihon não implica suporte a toda função exclusiva de um site.

Essas propostas não autorizam substituir o projeto por um fork do Hikari. A solicitação atual é estudar suas ferramentas e sua adaptação ao nosso aplicativo.

## Licença e próximos pontos de verificação

O Hikari declara GPL-3.0-ou-posterior. Se houver incorporação de código, revisar compatibilidade com a licença do Comic TV, obrigações de distribuição e atribuições de componentes portados. Estudar comportamento e desenvolver uma implementação própria é uma decisão distinta de copiar arquivos.

Fonte: [licença do Hikari](https://github.com/codegeasse1/hikari/blob/30f988950090904f93e1f5f31eb18aeab611323a/LICENSE).

### Esclarecimento do usuário

O usuário confirmou que deseja as duas formas de linha do tempo:
- Barra para escolher páginas durante a leitura, navegável pelo D-pad.
- Sequência visual de capas/edições da saga, mantendo a ordem de leitura e permitindo abrir cada edição separadamente pelo controle remoto.

São requisitos do produto, ainda não implementados. O tratamento de foco deverá preservar os comandos de zoom, troca de páginas e deslocamento já previstos.

Pendências: identificar o tipo e repositório da fonte HQ Now instalada no Hikari; reproduzir abertura de uma saga; validar fluxo de foco, barra e leitura em TV física.

O suporte RAR/CBR solicitado anteriormente continua pendente; esta análise não o implementa nem o substitui.

## Evidências das cinco fotos enviadas pelo usuário

Fotos recebidas em 23/10/2026, examinadas diretamente na conversa:

1. Área de mangá com busca geral, “Continuar lendo”, cartões de Saga do Infinito e The Boys (2006), contadores de páginas e fonte “HQ Now!” com Popular/Recentes. Isso confirma que a saga aparece como título retomável na instalação do usuário; não revela o repositório/pacote da extensão.
2. Catálogo Popular da fonte HQ Now!, com busca e alternância Popular/Recentes. Os cartões visíveis usam imagens horizontais. Para o Comic TV, preservar a proporção da capa é uma recomendação de design, não uma falha técnica comprovada pela foto.
3. Detalhes de Spawn: capa, fonte, estado, contagem de capítulos, Seguir, começar leitura, sinopse expansível e seção de capítulos.
4. Lista de capítulos, ordenação indicando mais novo primeiro e contorno destacado no capítulo em foco. A imagem comprova destaque visual; não comprova todos os caminhos de navegação pelo controle.
5. Leitor de The Boys com barra pontilhada de páginas, controles laterais, marcador, menu e ferramentas inferiores. Confirma visualmente a barra discutida anteriormente. A foto não permite atribuir com certeza a ação de cada ícone sem conferir o código ou testar.

A sequência de capas/edições de uma saga continua sendo um requisito confirmado pelo usuário, mas não aparece aberta nestas fotos. A presença de “Saga do Infinito” pode ser um título agregado pelo catálogo, sem um catálogo de sagas dedicado: essa distinção permanece pendente. Não concluir que as fotos mostram uma API específica de sagas nem ordem entre séries.

Referências funcionais registradas para o Comic TV: continuar leitura com contagem de páginas; busca geral e por fonte; Popular/Recentes; seguir título; detalhes com sinopse expansível; edições com ordenação e foco visível; barra de páginas; marcador e ferramentas acessíveis pelo controle. Preservar visual escuro/âmbar próprio. Registro de referência não significa implementação desses recursos.

## Esclarecimento: saga como título online da fonte

O usuário identificou o exemplo concreto: https://www.hq-now.com/hq/1079/saga-do-infinito. Segundo seu teste no Hikari, selecionar a capa abre o título online e oferece a lista das revistas necessárias para acompanhar a saga; a leitura online adapta a página à tela da TV.

Isso resolve a intenção funcional: o fluxo desejado é fonte → capa da saga → lista de revistas/edições → leitura online adaptada à TV. Não exigir que o usuário importe arquivos ou monte manualmente uma coleção para esse caso. Preservar a sequência apresentada pela fonte e permitir retomar a edição/página. Não inferir que a sequência seja uma cronologia universal ou que todas as fontes ofereçam sagas.

A URL usa o mesmo padrão `/hq/{id}/{slug}` dos títulos na extensão HQ Now examinada. É plausível que a saga seja entregue como um título comum e suas revistas como capítulos, sem uma API exclusiva de sagas; essa hipótese ainda precisa de validação da resposta da fonte. Não presumir que o aplicativo apenas embuta o site em uma WebView: a semelhança do fluxo não demonstra o mecanismo de renderização.

A consulta web ao endereço retornou apenas a exigência de JavaScript; não foi possível verificar por esse meio a lista, quantidade ou ordem das revistas. O comportamento descrito acima está confirmado pelo relato do usuário, não por execução local do Hikari.
