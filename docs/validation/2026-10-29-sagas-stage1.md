# Sagas na coleção — etapa local de 29/10/2026

Implementação da primeira etapa, ainda sem APK, publicação ou validação física.

- `Continuar lendo` e Histórico mantêm edições e progresso individuais.
- `Sua coleção` agrupa registros online somente pela identidade exata pacote da extensão + ID da fonte + chave da obra. Nomes iguais não estabelecem vínculo.
- O card usa o nome e a capa da obra salvos no registro online. Sem capa própria da obra, a saga mostra “Escolher imagem da saga”; não usa silenciosamente capa de capítulo. Capa de edição só é usada em uma saga por escolha explícita do usuário. Ler outra edição não troca a identidade nem a capa do grupo. Não são extraídas ou baixadas páginas para formar capas da Home.
- Abrir saga mostra somente as edições já existentes na biblioteca; abrir uma edição segue para os detalhes existentes. OK longo mantém as opções da edição. Voltar à coleção restaura a origem.
- Para importados, OK longo → Organizar saga permite criar um nome ou atribuir explicitamente a edição a uma coleção nomeada. A atribuição é independente da coleção comum; uma coleção não passa automaticamente a ser saga, e nomes de arquivos não são inferidos.
- Na lista da saga, Escolher imagem da saga abre o seletor SAF e copia uma imagem própria independente das edições. Usar capa de uma edição é uma alternativa explícita, com opção de voltar à capa original da saga.

## Persistência e limites

As atribuições e escolhas são gravadas atomicamente em `files/sagas.json` (máximo 4 MiB e 20.000 atribuições/escolhas). A leitura dos registros online é local, limitada a 8 MiB por registro, validada pelo codec existente e cancelável entre registros. Um cache de até 512 metadados (somente strings limitadas, sem imagens) usa ID/data de modificação/tamanho para evitar reler os registros a cada alteração de progresso. O progresso dos grupos continua sendo reconstruído da lista atual de edições. Originais, progresso, banco Room e associações comuns de coleções não são alterados por essa organização.

As imagens próprias ficam em `files/saga-covers`, com referências por hash da identidade do grupo e arquivos imutáveis gerenciados pelo mesmo mecanismo atômico da arte cinematográfica. Aceita JPEG, PNG e WebP estático, de 64 até 12.000 pixels por dimensão, máximo 48 MP/20 MiB por imagem e 128 MiB no conjunto; validação faz uma decodificação pequena e preserva os bytes originais. Escolher outra capa remove somente a imagem gerenciada desse grupo.

O backup portátil existente permanece compatível, mas **ainda não inclui `sagas.json` nem `saga-covers`**: em restauração noutro aparelho, a identificação online é reconstruída dos registros; atribuições e capas manuais precisam ser refeitas. Nenhum dado de saga é apagado pela limpeza de caches do leitor.

## Verificação

Seis testes de agrupamento cobrem identidade, homônimos/fontes distintas, capa estável ao retomar, escolha manual, importados sem inferência, ordem de capítulos e fallback quando a edição escolhida deixa de existir. A execução e compilação serão registradas pelo agente principal; este agente não executou Gradle. `git diff --check` passou para o recorte. Foco/D-pad na Box seguem pendentes.

Revisão posterior: recuperação `.bak` do AtomicFile ocorre antes da checagem limitada do fluxo; mudança de atribuição resolve o representante atual antes de restaurar o foco; saga esvaziada retorna a um card ainda existente ou ao cabeçalho. Compilação da primeira etapa anterior a essa revisão: agente principal informou SUCCESS em 1m32. Mudanças desta revisão ainda aguardam compilação pelo agente principal.

Revisão P2: retorno de Recentes é resolvido antes da presença do capítulo na coleção agrupada, mantendo retorno ao capítulo mesmo quando ele não representa sua saga. Falha/ausência de capa própria em sagas mostra convite para escolher imagem, sem fallback automático para capítulo. Oitavo teste cobre capa ausente e escolha explícita.
