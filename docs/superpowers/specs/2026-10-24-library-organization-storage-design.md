# Biblioteca organizada e armazenamento — desenho para aprovação

Data: 24/10/2026. Base: Comic TV 0.10.0.

## Decisões do usuário

O próximo pacote contém coleções/favoritos, busca na biblioteca e gerenciamento de armazenamento. Backup e downloads offline ficam para depois. O usuário escolheu integrar as funções às telas atuais, preservando o layout aprovado, temas customizáveis e navegação por controle remoto.

Este documento detalha essa escolha. Regras descritas como proposta são decisões sugeridas pelo implementador e ainda precisam da aprovação do desenho.

## Abordagens consideradas

1. **Integração nas telas atuais — escolhida pelo usuário.** A biblioteca recebe busca e filtros; Configurações recebe Armazenamento; o menu de segurar OK recebe ações de organização. Preserva a navegação conhecida.
2. **Aba Organizar independente.** Centralizaria as ferramentas, mas exigiria outra tela e mais deslocamentos pelo controle. Não escolhida.

## Escopo e comportamento propostos

### Biblioteca

- Preservar cabeçalho COMIC TV, Biblioteca/Histórico, destaque, Continuar lendo e grade de capas.
- Adicionar um botão Buscar no cabeçalho. O campo abre somente quando acionado, usando o teclado do Android; não abrir teclado ao entrar na biblioteca. Busca local por título, sem consultar fontes ou IA.
- Comparar sem diferenciar maiúsculas/minúsculas e acentos; ignorar espaços nas pontas. Digitação filtra a grade sem recarregar imagens já válidas. Mostrar quantidade e mensagem de nenhum resultado, com Limpar busca.
- Abaixo dos controles do cabeçalho, uma faixa horizontal rolável permite escolher Todas, Favoritos ou uma coleção criada pelo usuário. Incluir Gerenciar coleções. Não comprimir dezenas de coleções na mesma linha.
- Busca e filtro combinam-se. Aplicam-se à grade atual de Biblioteca ou Histórico; busca/coleção não alteram o destaque nem a fila de Continuar lendo. A legenda da grade identifica o filtro ativo para deixar esse alcance claro.
- Manter termo e filtro ao abrir detalhes/leitor e retornar. Se a coleção selecionada for removida, voltar a Todas. Alterações de dados não devem roubar o foco.

### Favoritos e coleções

- Segurar OK por 1 segundo em capa ou leitura recente mantém as ações atuais e acrescenta Favoritar/Remover dos favoritos e Organizar em coleções.
- Favorito é independente do histórico e das coleções. Uma edição pode participar de várias coleções; a edição não é duplicada.
- Seletor de coleções com seleção múltipla e Salvar/Cancelar. Cancelar não grava alterações parciais. Permitir criar uma coleção dentro desse fluxo.
- Gerenciar coleções permite criar, renomear e excluir coleções. Proposta: nomes de 1 a 40 caracteres após aparar espaços, sem duplicatas por maiúsculas/minúsculas/acentos; mensagens de validação em português. Ordem alfabética estável. Favoritos e Todas são filtros reservados, sem exclusão/renomeação.
- Exclusão de coleção exige confirmação explicando que os quadrinhos e o progresso serão mantidos. Excluir coleção remove somente seus vínculos. Não altera favorito ou histórico.
- Acrescentar acesso às mesmas ações nos detalhes da edição para quem já abriu a capa. A remoção/restauração do histórico mantém seu comportamento atual.
- Identificação discreta de favorito nas capas, com semântica acessível; não cobrir título ou progresso. Coleções são manuais; não agrupar sagas automaticamente neste pacote.

### Configurações → Armazenamento

- Acrescentar Armazenamento ao lado das seções existentes, sem nova rota na lateral.
- Mostrar espaço livre no volume do armazenamento privado e uso dos diretórios conhecidos: originais importados, capas salvas, cache de páginas e cache de miniaturas. Indicar que esses valores não representam todo o espaço ocupado pelo aplicativo/Android.
- Somar arquivos em tarefa de IO, com estado Calculando e Atualizar; não percorrer arquivos em cada recomposição ou tecla do controle.
- Oferecer Limpar cache de páginas, Limpar miniaturas e Limpar ambos. Cada ação mostra confirmação com a categoria e a estimativa de espaço. Avisar que imagens precisarão ser preparadas/baixadas novamente.
- Limpeza preserva originais, capas permanentes, banco, progresso, favoritos, coleções, configurações e descritores das fontes online. Não oferecer apagar originais neste pacote. Não chamar cache de download offline.
- Serializar limpeza com o acesso aos caches usando o mutex do repositório; não apagar arquivo em uso por uma carga ativa. O fluxo ocorre fora do leitor, depois de suas operações terem sido canceladas/aguardadas. Integrar invalidação da memória de imagens quando necessário para evitar referências a arquivos removidos.
- Não apagar indiscriminadamente cacheDir nem diretórios do Coil, extensões ou arquivos de outras funções. Limitar os alvos aos diretórios conhecidos de páginas e miniaturas; rejeitar destinos fora deles e não seguir links simbólicos durante varredura/limpeza.
- Informar bytes efetivamente liberados, falhas parciais e espaço atualizado. Não mostrar sucesso integral se algum arquivo não puder ser removido. Desabilitar comandos repetidos durante a tarefa e permitir tentar novamente após erro.

## Dados e arquitetura propostos

- Manter Kotlin/Compose nativo, Room, StateFlow e dependências atuais. Nenhum serviço novo, conta, IA ou biblioteca gráfica.
- Migração aditiva Room 2→3: coluna favorite com padrão falso em comics; tabela de coleções e tabela de associação edição/coleção com chave composta e índices. Vínculos com integridade referencial; exclusão de coleção limpa vínculos em transação. Preservar também a cadeia 1→2→3, sem migração destrutiva.
- Identificador da edição continua sendo o atual; não mudar IDs de edições online ou arquivos locais. Nomes de coleções recebem chave normalizada para unicidade. Manter chaves de filtros distintas dos nomes traduzidos.
- DAO/repositório expõem observação das coleções, favoritos e vínculos e operações transacionais. Estado de consulta/filtro na ViewModel/SavedStateHandle; dados persistentes no banco. Filtragem local em memória é suficiente para a coleção atual; normalizar os títulos fora das recomposições repetidas.
- O gerenciador de armazenamento usa o repositório para relatórios e limpeza de seus diretórios. Relatório com contagens Long; lidar com diretório ausente, arquivo removido durante cálculo e falha de acesso.
- Atualizações devem preservar as APIs de fontes, leitura e histórico. Preferências de filtro de imagem por edição ficam intactas.

## Controle remoto, aparência e erros

- Tudo acessível por D-pad; OK curto abre, OK longo mantém 1 segundo. Nenhuma ação depende de mouse, arrastar ou gesto.
- Busca, coleções e confirmações têm foco inicial previsível; Voltar fecha o contexto ativo e restaura o botão/capa de origem, ou um destino estável se esse item deixou de existir no filtro.
- Cancelar inicialmente focado nas confirmações de exclusão/limpeza. Atualizações de banco não reabrem menus nem transferem foco.
- Temas, alto contraste e texto ampliado preservados. Faixas e diálogos rolam; nomes longos não devem empurrar botões para fora da tela.
- Mensagem clara para erro de gravação/limpeza. Não atualizar a UI como persistida antes da confirmação da operação.

## Fora deste pacote

Backup/restauração, downloads completos, sincronização, metadados enriquecidos, recomendações, agrupamento automático de séries, marcação manual de leitura, painéis individuais e mudanças no motor de zoom.

## Verificação necessária

- Busca: acentos, caixa, espaços, título vazio, nenhum resultado, combinação de filtros e retorno de navegação.
- Persistência: favorito e associação múltipla; cancelar edição; criar/renomear duplicata; remover coleção mantendo HQ/progresso/favorito; migrações 1→2→3 e 2→3 em SQL/Room.
- Armazenamento: cálculo por categoria, cache ausente, limpeza limitada aos alvos, originais/banco/capas preservados, falha parcial e concorrência com materialização. Testes com arquivos temporários fora dos diretórios reais do usuário.
- TV: foco ao buscar, teclado somente por ação explícita, coleção com texto longo, texto ampliado, todos os temas, retorno de menu/detalhes, coleção que esvazia e limpeza enquanto a UI se atualiza.
- Executar testes JVM apropriados, build APK, compilação da instrumentação, lint e verificações existentes de ABI/migração/assinatura. Registrar separadamente qualquer teste de TV que não puder ser executado por ausência de aparelho.

## Entrega

Depois da aprovação deste desenho: plano técnico por etapas, implementação, revisão, verificações e novo APK identificado. A primeira etapa será a persistência e busca/organização; a segunda, a tela de armazenamento integrada ao mesmo pacote. Não alterar a versão 0.10.0 instalada nem apagar dados durante o desenvolvimento.
