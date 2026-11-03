# Aba TV compatível com complementos Stremio

Estado: desenho aprovado pelo usuário neste chat; implementação ainda não iniciada. Planejamento técnico em andamento.

## Respostas posteriores do usuário

- Torrents e links diretos entram juntos na primeira entrega.
- Celular pode controlar a reprodução também.
- Organização inspirada no Stremio com identidade/cores Comic TV aprovada; autorizado prosseguir com criação.
- Usuário aceita deixar legendas para a fase seguinte e também confirma interesse em ASS/SSA e formato OpenSubtitles (provavelmente SRT, referido como .src). Interpretação comunicada no chat: primeira entrega com reprodução e controle; fase seguinte com legendas externas SRT/VTT e ASS/SSA com estilos. Não marcar essa fase concluída nem excluir do escopo total.
- Jogos e formato próprio de repositórios continuam futuros.

Estas respostas substituem as pendências de escolha de torrents e de controle do celular abaixo. Cadastro de complementos pelo celular proposto; navegação completa do catálogo pelo celular não foi explicitamente escolhida.

## Decisões do usuário

Aba TV ao lado das seções existentes, abrindo menu próprio. Filmes, Séries e Animes são a prioridade. Adicionar complementos existentes do Stremio por URL; formato próprio de repositórios e jogos ficam para o futuro. Buscar compatibilidade também em fontes, legendas e organização visual. Perguntar sobre dúvidas importantes antes de implementar.

## Abordagem escolhida

Cliente nativo do protocolo Stremio em Kotlin/Compose, integrado ao Comic TV. O usuário escolheu complementos existentes em vez de depender da API CinemaHQ ou criar formato próprio. O protocolo define o contrato de integração; o APK serve como evidência dos modelos e da organização de telas. Não importar código, binários, credenciais, marca ou artes do APK analisado.

## Evidências e limites

APK fornecido: com.stremio.one 1.12.1, code 53145729, SHA256 ca53408edadde3fe984346afa09e8e47000ecc8ef0c6c6e7c9b1434f1c6049e4. Análise estática REA/JADX, sem instalação ou execução; assinatura não verificada.

Modelos Manifest e AddonDescriptor confirmam transportUrl, recursos, tipos, prefixos de ID, catálogos, catálogos de complementos e indicadores de configuração. Subtitle contém id, lang, url, label e name. As classes de TV incluem Board, Discover, Search, Library, Addons, MetaDetails, listas de temporadas, vídeos e streams, e Player.

O APK empacota bibliotecas de players, libass e servidor de streaming. Isso não comprova comportamento, desempenho, compatibilidade com codecs ou aparência renderizada. O layout abaixo é uma proposta baseada na organização encontrada, não uma reprodução visual já validada.

Documentação: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md ; https://stremio.github.io/stremio-addon-sdk/api/responses/manifest.html ; https://stremio.github.io/stremio-addon-sdk/api/responses/stream.html ; https://stremio.github.io/stremio-addon-sdk/api/responses/subtitles.html .

## Fluxos propostos

TV abre Início, Filmes, Séries, Animes, Minha lista, Buscar e Complementos. Início mostra continuar assistindo e fileiras dos catálogos instalados. Descoberta oferece tipo, catálogo e filtros declarados pelo complemento. Detalhes mostra arte, sinopse, metadados e temporadas/episódios quando disponíveis; reprodução lista as fontes retornadas, identificadas pelo complemento.

Sem catalogo instalado: estado vazio explicativo com Adicionar complemento. Proposta pendente: oferecer instalação opcional do Cinemeta para preencher o catálogo inicial.

Complementos: colar URL, carregar manifesto, mostrar nome/descrição/capacidades, confirmar instalação, ativar/desativar e remover. Aceitar manifest.json HTTP(S) e normalizar links stremio:// preservando caminhos/configurações. Links de configuração abrem no navegador para o usuário concluir e retornar com a URL configurada; não adivinhar configuração. Link de página genérica não é automaticamente um manifesto. Catálogos addon_catalog devem ser tratados como descoberta de complementos, com confirmação individual, não instalação em massa de qualquer repositório GitHub.

Preservar os IDs de obras e vídeos exatamente como retornados. Anime pode aparecer como movie, series ou tipo próprio: não reduzir tudo a IMDb nem inventar conversão. A seção Animes depende dos catálogos/tipos efetivamente declarados; permitir escolher quais catálogos alimentam essa seção quando a identificação for ambígua.

## Contrato de compatibilidade

Implementar manifest, catalog, meta, stream, subtitles e descoberta de complementos via addon_catalog nesta primeira versão proposta; catalog extra com search, genre, skip e extras declarados. Respeitar isRequired, options, recursos em string/objeto, types e idPrefixes por recurso. Codificar IDs e extras sem destruir caminhos configurados. Catálogos com pesquisa obrigatória só são consultados com pesquisa. Consultar apenas complementos compatíveis; resultados chegam progressivamente e falha de um não bloqueia todos.

Limitar bytes, tempo de resposta, imagens, itens e concorrência; cancelar ao sair ou trocar a pesquisa e rejeitar resultados tardios. Reutilizar OkHttp, coroutines, Coil e componentes de foco existentes. Valores concretos ficam para o planejamento técnico.

## Vídeo e legendas

Proposta: player Android Media3 próprio, com pausa, busca temporal, áudio, legendas, proporção, escolha de fonte, retorno ao ponto e retomada persistida. URLs HTTP(S) e cabeçalhos de mídia informados pelo complemento precisam de tratamento próprio. externalUrl abre o destino explicitamente. Não alegar suporte universal a DRM, YouTube, torrents ou formatos de servidor apenas por reconhecer a resposta.

Legendas: faixas embutidas, legendas associadas ao stream e complementos de subtitles. Respeitar id, idioma, label, nome, URL e metadados de identificação do vídeo; não presumir IMDb. Oferecer desligar, idioma preferido, tamanho, fundo e ajuste de sincronização. SRT/VTT como base; avaliar suporte ASS/SSA e estilo antes de assumir paridade para animes. Não reutilizar URLs localhost:11470 de conversão sem um servidor compatível presente.

Pendente decisivo: iniciar apenas com URLs diretas (incluindo resolvidas por serviços configurados no complemento) ou incluir torrents com mecanismo próprio. No segundo caso, projetar cache, armazenamento, pausa, limpeza e ciclo de vida antes de escolher dependência nativa. Nenhuma opção foi escolhida pelo usuário até aqui.

## Layout e integração

Proposta: organização inspirada no Stremio, com navegação interna, fileiras de capas, grade de descoberta, detalhes amplos, episódios e lista de fontes, mantendo identidade e cores Comic TV. Os cinco temas, contraste, texto ampliado e D-pad devem funcionar. Não alterar o layout congelado do Controle do celular. Toda animação/efeito novo deve ter chave para desligar na TV e no celular.

Adicionar rota e entrada TV em todos os layouts de navegação. Componentes do módulo e persistência são próprios, sem misturar filmes com as edições da biblioteca de quadrinhos. Fluxos de volta restauram foco/posição. Proposta: cadastro de URLs também pelo painel do celular, aproveitando a sessão LAN existente; alcance dessa parte depende de confirmação.

Guardar complementos e preferências localmente. URLs configuradas podem conter dados de conta: não registrar em logs ou mensagens nem exportar no backup genérico até definir tratamento explícito. Sem login/sincronia de conta Stremio no escopo inicial proposto. Minha lista e progresso locais por obra/episódio.

## Validação e entrega

Verificar contratos com fixtures de recursos variados, normalização de links, prefixos/IDs, falhas isoladas, paginação e legendas. Compilar uma tarefa Gradle por vez. Validação de Box/iPhone permanece física. Não alterar versão nem publicar/gerar APK de distribuição sem autorização do lote. Registrar andamento em docs próprio; AGENTS só na integração do lote.

## Perguntas para fechar o desenho

1. Torrents já na primeira entrega ou URLs diretas primeiro?
2. ASS/SSA com estilos/posicionamento/fontes precisa entrar na primeira entrega para animes, ou SRT/VTT e faixas básicas bastam inicialmente?
3. Celular apenas cadastra/configura complementos ou também navega e controla o vídeo nesta primeira entrega?

Propostas visuais e de inicialização para revisar junto: organização inspirada no Stremio com identidade Comic TV; Cinemeta opcional; preferência inicial PT-BR alterável. Revisão técnica do desenho confirmou que as três perguntas acima mudam substancialmente o plano.
