# Comic TV — especificação do primeiro incremento

Data: 22 de outubro de 2026.

## Objetivo
Aplicativo nativo para Android TV e Google TV, em português, operável inteiramente pelo controle remoto. O primeiro incremento permite importar um CBZ/ZIP, encontrá-lo na biblioteca, ler suas páginas e retomar a leitura.

## Direção adotada
Kotlin, Activity única e Jetpack Compose for TV. Um módulo Android inicial, com pacotes separados para biblioteca, arquivos, persistência e leitor. Essa estrutura reduz a configuração inicial e preserva fronteiras para extrair módulos depois. A alternativa de criar muitos módulos agora aumenta o custo sem benefício imediato; a alternativa web não atende à direção nativa da proposta.

## Escopo implementável
- Launcher de TV, tema escuro, textos em português e foco visível.
- Biblioteca com capas, título, estado vazio e ação de importar.
- Importação pelo seletor de arquivos do Android, quando oferecido pelo dispositivo. O original é copiado para armazenamento privado para sobreviver à perda de acesso à URI.
- Leitura de CBZ/ZIP com JPEG, PNG e WebP, ordenação natural e extração sob demanda para cache limitado em disco.
- Esquerda/direita mudam a página; Select abre ferramentas; Back fecha ferramentas ou retorna à biblioteca.
- Ferramentas para ajustar à tela, largura ou altura e inverter direção de leitura.
- Progresso persistido por edição e restauração do item focado ao voltar à biblioteca.
- Mensagens compreensíveis para arquivos inválidos, ausência de imagens e falhas de importação.

## Componentes e dados
A UI recebe estado de ViewModels e emite ações. O repositório da biblioteca conserva IDs persistentes e metadados em Room. O adaptador ZIP indexa imagens e fornece arquivos ao leitor através de um contrato de páginas que também comportará fontes remotas. Coil decodifica imagens no tamanho solicitado pela área de exibição. Operações de disco e ZIP executam fora da thread principal. Importação incompleta não entra na biblioteca.

## Limites dos arquivos
Sem extração integral ou carregamento do arquivo inteiro em memória. Limites explícitos para número de entradas, tamanho por página e total extraído; rejeição de caminhos inseguros. O cache pode ser reconstruído a partir do original. A primeira versão não inclui zoom de alta resolução.

## Etapas seguintes preservadas
Integração Mihon/Keiyoushi e HQ Now terá prova técnica própria, incluindo assinatura, compatibilidade e leitura de uma edição real. Importação HTTP com QR code, RAR/7z/TAR, galeria, marcadores e zoom por tiles ficam para incrementos posteriores.

## Verificação
Testes de ordenação natural, seleção de imagens, limites de extração e persistência de progresso. Compilação e testes locais quando JDK e SDK estiverem disponíveis. Navegação D-pad, retorno de foco e retomada de leitura exigem emulador ou TV; sem execução nesses dispositivos, o resultado será identificado como não validado em TV.

## Critério de entrega
Projeto Android com configuração de build reproduzível e instruções de execução. APK somente após compilação bem-sucedida. O primeiro incremento estará funcionalmente validado quando uma edição CBZ real puder ser importada, lida e retomada pelo controle remoto.

## Design aprovado e evolução do produto
A proposta visual gerada foi aprovada pelo usuário: superfícies escuras, texto claro, destaque âmbar de foco, navegação lateral, capas grandes, Continuar lendo, detalhes da obra, leitor imersivo e ferramentas em painel inferior. Nome Comic TV é provisório. Capas, sinopses e obras da imagem são ilustrativas; não serão apresentadas como conteúdo real disponível.

O produto terá gerenciamento de múltiplos repositórios compatíveis com Mihon: cadastro, consulta, instalação/atualização de extensões e seleção de fontes. HQ Now é uma fonte possível, sem exclusividade. Essa integração será um incremento posterior ao fluxo local e requer validação própria.

## Proposta de controles para revisão no plano
Leitura: esquerda/direita trocam páginas respeitando RTL; cima/baixo aumentam/reduzem zoom entre 1x e 3x em passos de 0,25. OK abre ferramentas. A ação Mover página ativa um modo em que as quatro setas deslocam a imagem, com limites visíveis e geométricos. Back sai do deslocamento; depois fecha ferramentas; depois retorna à biblioteca conforme o contexto ativo. Ao trocar página, o zoom volta a 1x. Zoom inicial usa decodificação limitada à área de exibição; tiles ficam para incremento posterior.

## Limites concretos do primeiro incremento
Arquivo importado: máximo de 1 GiB. Índice: máximo de 5.000 entradas. Página extraída: máximo de 32 MiB, medido durante a cópia mesmo quando o ZIP não informa tamanho. Total declarado de imagens descompactadas: máximo de 2 GiB; entradas com tamanho desconhecido continuam sujeitas aos limites por página e de cache durante extração. Cache em disco de páginas: máximo de 256 MiB, com proteção da página em uso e remoção LRU. Imagens: rejeitar dimensões acima de 100 megapixels antes de decodificar. Rejeitar ZIP criptografado ou sem imagens suportadas com mensagem em português.

## Transferências incluídas no produto — atualização de 22/10/2026
Esta seção substitui as descrições anteriores que deixavam Web e FTP indefinidos. A área Importar terá quatro entradas: Arquivo local, Pela rede local (Wi-Fi/Ethernet), FTP e Pela web.

Rede local: iniciar servidor HTTP temporário na TV; exibir URL real, QR equivalente, estado, clientes conectados, progresso e ação de parar. Dispositivos na mesma rede enviam arquivos por uma página de upload.

FTP: iniciar servidor FTP temporário na TV; mostrar URL/porta e dados de acesso, QR e ação de parar. Receber arquivos numa área restrita de importação, sem expor a biblioteca completa. A referência permite acesso anônimo; autenticação será decidida no desenho desse subsistema.

Web: parear TV e portal de envio por token temporário, digitável ou incluído no QR; permitir renovar token, mostrar espera/progresso e cancelar sessão. Este é um portal de transferência, não um catálogo e não uma URL de download. Serviço próprio com backend a projetar; a imagem não permite afirmar como o aplicativo de referência transporta os arquivos.

Todos os transportes escrevem por streaming em arquivo temporário e usam o mesmo fluxo de validação/promoção da importação local. Limites de arquivo e espaço em disco continuam aplicáveis. Sessões podem ser encerradas pelo usuário; transferência incompleta não aparece como HQ pronta. Não fixar IPs, tokens ou domínio mostrados nas fotos.

A tela seguirá o design próprio aprovado: fundo escuro, foco âmbar, textos grandes, QR com contraste e comandos acessíveis por controle. Transferências são requisitos do produto; sua inclusão não significa que já estejam implementadas no incremento local.

## Formatos — entrega 0.3.0 em 23/10/2026
Implementados RAR/CBR, PDF e EPUB de imagens/texto, além de ZIP/CBZ. Detecção pelo conteúdo; EPUB segue a sequência do pacote. Texto nativo com fonte ajustável e rolagem pelo D-pad. Limites e recursos não suportados estão documentados em VALIDACAO-0.3.0.md; validação em TV física pendente.
