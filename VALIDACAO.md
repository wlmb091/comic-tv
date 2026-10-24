# Validação — Comic TV 0.7.7

23/10/2026 · versionCode16 · Android8/API26 ou superior.

## Navegação aprovada

- Lateral: Biblioteca, Novidades, Catálogo, Extensões, Importar e Configurações, com ícones pequenos e expansão flutuante por foco/ponteiro.
- Catálogo usa a listagem disponibilizada pela fonte selecionada; Novidades usa suas adições/atualizações recentes, sem confundir com histórico pessoal. Respeitam a paginação e os limites da própria extensão, sem prometer enumerar obras que ela não fornece.
- Mesma fonte preservada entre as duas categorias; pasta no canto superior troca a fonte e lupa abre a busca. Cabeçalho compacto libera espaço para capas.
- Extensões separado: instaladas, atualizações e disponíveis, contagens, busca por nome/fonte/idioma, grades com ícones40dp. Índices dos repositórios cadastrados são agregados, até32, com falhas parciais informadas e certificados comparados com as chaves salvas.
- Instalar/atualizar pela grade verifica pertencimento do candidato ao índice atual e repositório confiável antes e depois do download; confirmação nativa do Android preservada.
- Mantidos biblioteca/histórico completo, lateral flutuante e gestos do leitor entregues anteriormente.

## Ciclo de vida

SourcesViewModel é compartilhado pelas três seções e criado somente ao entrar nelas. Despacho por identidade da entrada de navegação evita apagar detalhes/busca ao voltar do leitor. Catálogo ainda não carregado é consultado ao sair de um gerenciamento aberto na inicialização. Trocas de seção cancelam operações pendentes, preservando a restauração inicial de fontes.

## Verificação

-145 testes JVM passaram sem erros/falhas:87core,10mihon,48app.
-6 testes novos cobrem candidatos removidos/alterados/não listados e retomada da mesma entrada, navegação explícita e recriação da instância.
-assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
-Verificador de ABI do DEX passou; assinatura APK validada por apksigner; aapt2 confirma0.7.7/code16/minSdk26.
-SHA256:CA9B5F58F02988E79B5A066E784180610D1153D544CF121370483753C833ECDB.
-Revisão independente identificou dois problemas de retomada/back; corrigidos e reavaliados sem novos achados.

## Limites

Sem teste em aparelho nesta etapa. Validar: Catálogo/Novidades na mesma fonte, pasta troca seleção, Extensões busca/filtra/instala com confirmação Android, voltar do leitor mantém capítulos, sair de Extensões numa inicialização fria carrega catálogo. Repositórios/ícones podem exigir rede. Não foram instaladas extensões no computador nem feitas contratações.
