# Sagas e tela de detalhes — pacote ainda aberto

Aplicação compartilhada aos cinco temas (Comic TV, Flix, Prime, Max, Cinema). Fotos de referência em `docs/design/sagas-and-details/`; não são capturas do resultado.

## Coleção

Continuar lendo e Histórico permanecem individuais. Sua coleção agrupa por identidade exata da fonte e obra; títulos iguais de fontes diferentes não são mesclados. Abrir um grupo mostra as edições da biblioteca; escolher edição abre seus detalhes. Ordem/capa do grupo não muda por ler outro capítulo.

Sem identidade de série nos arquivos importados, a associação é explícita: opções da edição → Organizar saga. Pode criar um nome ou atribuir a um existente. Escolher imagem da saga copia uma imagem própria independente, validada e limitada; opcionalmente o usuário pode escolher expressamente a capa de uma edição. Ausência/falha da capa principal nunca substitui automaticamente pela página de um capítulo: aparece indicação para escolher imagem.

SagaStore usa arquivo atômico limitado e recuperação `.bak`, cache de 512 metadados por identidade/mtime/tamanho. Originais, progresso, banco e coleções comuns não são reescritos pela organização. **Backup portátil ainda não inclui associações e imagens manuais de sagas**; no outro aparelho elas precisam ser refeitas. Identidades online são reconstruídas dos registros restaurados. Essa limitação está registrada, não declarar cobertura de backup inexistente.

## Detalhes

Resumo completo vira botão à esquerda, com diálogo amplo rolável. O painel direito lista os capítulos reais da fonte, na ordenação salva, com atualização/erro/retry. Para arquivos locais, lista os membros conhecidos da saga, sem inventar edições não presentes. Marca Lido somente com progresso real no fim; parcial é Em andamento; sem histórico é Não lido.

Retomada usa a leitura mais recente entre identidades exatas da obra. Ações de navegação e edição não marcam capítulos como lidos. Foco tardio não substitui interação do usuário nem abre atrás de Resumo/Mais opções. Consultas de fonte são canceladas e aguardadas antes de entrar no leitor e ao perder o ciclo visível.

## Qualidade das capas

Detalhes e prévia de capítulos preferem a primeira página original já no cache/offline; arquivos locais podem extrair a primeira página do original. Não baixa capítulos para decorar a tela. Arte Cinema independente não é usada como capa da edição. Fit preserva a proporção, interpolação High e decodificação no tamanho exibido; a miniatura existente é fallback se faltar original/ocorrer erro. Não há IA nem promessa de recuperar detalhes inexistentes.

## Verificações

- `build/saga-grouping.log`: primeira etapa passou, 1m32s.
- `build/saga-grouping-final.log`: cache/recuperação/capa própria passaram, 1m40s.
- `build/saga-details-final.log`: agrupamento revisado e testes de política de capítulos/controlador passaram; AndroidTest compilado, 1m38s.
- Revisão independente corrigiu retorno a Recentes de capítulo não representante e detectou consumo prematuro do foco durante a saída dos detalhes; correção final registrada ao concluir.

Testes nativos não foram executados na Box. Layout/foco/qualidade percebida ainda exigem conferência física. Sem APK, incremento de versão, push, CI ou publicação; aguardando novos detalhes do usuário.

Correções finais verificadas em build/saga-details-verified.log: SUCCESS1m45s, suitesSagaGrouping/DetailsChapterPolicy/DetailsChapterController semfalhas,AndroidTestcompilado. Retorno de foco agora é armado apenas após abrir capítulo comsucesso e consumido somente apósPAUSE→RESUME dosDetalhes; a animaçãodesaída não oconsome. Resumo/maisopções bloqueiam focotardio porclique também; falhas deIO da capa retornam à miniatura. NenhumAPKgerado.
