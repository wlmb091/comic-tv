# Organização da biblioteca e armazenamento — 0.11.0

## Busca, favoritos e coleções

Na biblioteca, use **Buscar** para abrir o teclado e pesquisar o título. A busca ignora diferenças de acentos e maiúsculas. A faixa **Todas / Favoritos / suas coleções** combina com a pesquisa. O contador e os resultados são da grade de capas da aba Biblioteca ou Histórico; o destaque e a fila Continuar lendo permanecem independentes.

Segure OK por 1 segundo sobre uma capa ou leitura recente para **Favoritar** ou **Organizar em coleções**. Essas ações também estão nos detalhes. Uma edição pode participar de várias coleções; isso não duplica arquivos. No seletor, as associações são gravadas apenas com **Salvar**. Cancelar mantém as associações anteriores. Criar uma coleção durante esse fluxo cria a coleção vazia imediatamente; cancelar a seleção não exclui essa nova coleção.

**Gerenciar coleções**, no fim da faixa horizontal, permite criar, renomear e excluir. Nomes têm de 1 a 40 caracteres e são únicos sem diferenciar acentos/caixa. Todas e Favoritos são nomes reservados aos filtros. Excluir uma coleção pede confirmação e remove somente seus vínculos; conserva quadrinhos, favorito, histórico e progresso. O agrupamento é manual, sem inferência de série ou IA.

Termo e filtro são mantidos ao navegar para uma edição e retornar. A coleção selecionada volta a Todas caso seja excluída. Botões focados têm destaque do tema; ao fechar diálogos, o foco retorna à origem ou a um destino estável quando a origem foi removida. Conferência real de D-pad/teclado/texto ampliado na TV ainda é necessária.

## Configurações → Armazenamento

A seção mostra espaço livre no volume privado e bytes em quatro categorias conhecidas: originais importados, capas salvas, cache de páginas e miniaturas. Não representa todo o espaço ocupado pelo Android/aplicativo. O cálculo ocorre em IO ao abrir/atualizar, sem varrer arquivos a cada tecla ou recomposição.

Escolha **Limpar cache de páginas**, **Limpar miniaturas** ou **Limpar ambos**. A confirmação começa em Cancelar e mostra uma estimativa. Originais, capas permanentes, banco, histórico/progresso, favoritos, coleções, configurações e registros das fontes online são preservados. Não há opção de excluir originais neste pacote.

A limpeza serializa com extração/download usando o mutex do repositório e invalida a memória de imagens. Seus alvos são somente os diretórios conhecidos de páginas e miniaturas, sem varrer cacheDir inteiro ou o cache do Coil. Links/redirecionamentos no alvo ou nos descendentes são recusados; aliases confiáveis em ancestrais do caminho privado do Android são resolvidos normalmente. Arquivos não removidos são informados como falha parcial, com bytes dos arquivos removidos e relatório atualizado.

O espaço livre efetivo pode variar por uso do sistema e alocação do volume. Após limpar, imagens serão preparadas novamente; conteúdo online pode exigir internet. Esse cache descartável não é download offline completo.

## Atualizações e limites de validação

Room 2→3 adiciona favoritos, coleções e vínculos com integridade referencial. A cadeia 1→2→3 também é mantida; não há migração destrutiva. As verificações SQL reais cobrem preservação de progresso, unicidade e exclusão de vínculos. Testes Room/Compose/links nativos foram compilados e precisam de dispositivo Android para execução.

Backup/restauração e downloads offline continuam para a próxima etapa. Resultados da compilação, testes e assinatura estão em VALIDACAO.md.
