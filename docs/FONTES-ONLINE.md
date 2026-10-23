# Fontes online — 0.7.0

O Comic TV aceita vários repositórios Mihon, sem exclusividade para HQ Now. Cada URL deve usar HTTPS e fornecer um descritor com certificado SHA-256. Índices protobuf/gzip atuais e JSON legados são aceitos; índices antigos que só anunciam migração são recusados.

## Uso na TV

1. Fontes → Adicionar Keiyoushi, ou digitar a URL do repositório.
2. Ver extensões → filtrar nome/idioma → Instalar.
3. Autorizar a instalação pelo Comic TV nas configurações do Android, se solicitado; confirmar o APK no instalador nativo.
4. Atualizar instaladas → escolher a fonte → Populares, Recentes ou Buscar.
5. Abrir a capa → escolher capítulo/edição. Voltar do leitor retorna à tela anterior; a edição fica na biblioteca para continuar depois.

As sagas seguem os títulos e capítulos fornecidos pela fonte. Não é uma cópia do site nem uma reconstrução manual de ordem cronológica. O app não inventa edições ausentes e não inverte a ordem entregue pela extensão.

## Confiança e compatibilidade

A chave recebida no primeiro cadastro é preservada. Uma mudança de chave bloqueia a atualização automática; remover e cadastrar novamente permite uma nova escolha de origem. O APK baixado precisa corresponder ao pacote, versão, feature de extensão e certificado do repositório. Nada é instalado sem a tela de confirmação do Android. Extensões são código executado dentro do processo do leitor, com as permissões do aplicativo; cadastre repositórios de sua confiança.

O runtime cobre APIs Mihon 1.2–1.6, incluindo pontes Rx e suspend. Compatibilidade de uma API não garante todas as extensões: dependências adicionais e configurações específicas podem faltar. QuickJS não está disponível. Erros de API ausente são apresentados no leitor, sem encerrar o app. WebView precisa estar funcional para desafios suportados. URLs das fontes podem usar HTTP ou HTTPS conforme o site; URLs de repositórios/APKs permanecem restritas a HTTPS.

## Armazenamento e progresso

Durante a leitura online, OK → Edições da saga / série salva a posição e fecha o leitor antes de consultar a lista atualizada. Isso evita substituir o contexto de páginas da extensão com a página ainda ativa. A edição atual é identificada pela chave da fonte; anterior/próxima seguem a sequência escolhida. O botão de ordem inverte a lista e a preferência é lembrada por título. Duplicatas mantêm a primeira entrada; se a edição atual desaparecer da fonte, não são inventados vizinhos, mas a lista continua disponível para seleção. A lista tem limite de 10.000 itens antes da deduplicação. Navegar a lista não grava progresso. Abrir uma edição previamente lida conserva sua página.

O cadastro de repositórios já existe: Fontes → Adicionar Keiyoushi ou informar URL → Ver extensões → Instalar → confirmar no Android → Atualizar instaladas → escolher fonte. Cadastrar o repositório não instala suas extensões automaticamente. Repositório lista APKs; extensão instalada fornece uma ou mais fontes. Essa leitura online é feita pela TV e não usa o portal de importação de arquivos.

Um ID estável combina pacote, fonte, título e capítulo. Os registros privados guardam referências, certificado e páginas, com JSON limitado a 8 MiB e capítulo limitado a 5.000 páginas. Reabrir consulta a fonte para renovar URLs temporárias. Navegar capas ou capítulos não altera a página lida. Atualizar a contagem preserva progresso e preferências; se o capítulo encolher, a posição é limitada à última página.

Downloads de imagens usam o cliente da extensão e preservam cabeçalhos/cookies. Página: até 32 MiB e 100 megapixels. Leitura local e online compartilham o cache de 256 MiB e a proteção da página visível; miniaturas usam cache separado de 32 MiB. Cache HTTP do runtime: 5 MiB. Não há download completo de saga ou garantia de retomada sem internet nesta versão.

## Verificação física pendente

Na TV: confirmar instalação/retorno, abrir HQ Now, buscar Saga do Infinito, conferir edições retornadas, ler duas páginas, usar miniaturas/automático, voltar à lista e continuar pela biblioteca após reiniciar. Também testar uma segunda extensão e atualização de APK. Não foi executado APK de fonte no computador.

Consulte MIHON-INDEX.md e MIHON-RUNTIME.md para formatos, limites, origem do runtime e licenças.
