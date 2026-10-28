# Correções Prime/Max/Cinema — 1.2.3

Spec: docs/design/themes/photos-1.2.3/README.md. Base2520fc5, árvore limpa antes do trabalho. Pedidos autorizados por fotos, continuação/publicação automática já autorizadas; sem nova rodada de aprovação. Usar imagens mais recentes junto das prévias1.2.2. Implementação nativa própria.

## Restrições

Preservar carrosselPrime aprovado, rotas/dados/USB/originais, texto85–130%, alto contraste opaco e foco real único. Arte atéas bordas sem reduzir qualidade/alterarbytes. Não alterarComic TV original/Flix, APIs dearte ou leitor. BuscarPrime usa a busca existente entre seções. Cinema mantém drawerRight e logo apenas expandido. Sem declarar execução física.

## Tarefas e registro

- [x] Registrar lote atual e escopo persistente.
- [x] ShellPrime: fundo raiz, navegação transparente/centrada com marcaàdireita eBuscar; Maxdock52dp×escala/reserva74dp×escala. Implementadorprime_max_shell123, sem build/commit; revisão integrada pendente.
- [x] ConteúdoPrime: retirarBusca/fundo/degradê próprios mantendo carrossel; Maxgrade/Histórico horizontais/destaque maisbaixo. Implementadorprime_max_content123.
- [x] Cinema: Menu/painel compartilham material deContinuarleitura, sem reflexo claro; marca no topo expandido, retorno/foco preservados.
- [x] Revisão independente parcial semP1/P2, interrompida por limite do serviço; revisão estática final pelo implementador. GeometriaMax corrigida no registro:3colunas a960dp com margens.
- [x] Build/testes/lint e compilação de testes nativos, verificações de publicação/dados/ABI adequadas.
- [x] Publicar1.2.3/code65 peloCI e conferirAPK público por manifesto/hash/tamanho/certificado/ABI/alinhamento.

Diagnóstico: molduraPrime vem de backdrop dentro do conteúdo com insets e glassPanel do topo; retânguloHero vem de gradientes locais. Maxfoto está noHistórico com gradeportrait alta e rolagem ativa; reduzir apenasRecentes não resolveria. Cinema usava sidebar#404040/80%+reflexo enquanto ações usam preto68%; reutilizar o material das ações. Ruling: manter rolagem/restauração reais em vez de zerar posição para imitar captura — preserva navegação anterior, usuário confere tela no topo.
