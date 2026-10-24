# Modos de visualização — 0.8.1

O ícone de visualização das ferramentas percorre Normal, Galeria, Livro e volta a Normal. Mantém o menu aberto para permitir trocar novamente. Ao trocar, zera zoom/deslocamento e aplica ajuste de página.

Normal mostra uma página. Galeria preserva página atual maior e vizinhas menores. Livro mostra a atual e a seguinte, com escala compartilhada e proporções preservadas. A primeira página visual fica à direita quando RTL está ativo.

No Livro, as setas passam por pares a partir da posição atual. A última página sem par fica sozinha, sem repetir ou ultrapassar a edição. Selecionar pela linha do tempo mantém o número exato escolhido como início do par. O progresso salvo representa a primeira página do par, permitindo retomar esse ponto.

Leitura automática aguarda as duas imagens serem exibidas antes de iniciar o intervalo, avança um par e para no último. Erro na segunda página pode ser tentado novamente pelas ferramentas. Fechar cancela e aguarda ambas as operações antes de navegar.

EPUB de texto mantém seu leitor de texto e passa uma seção por vez. Uma seção de texto também não é usada como segunda imagem em uma abertura mista.

A escolha fica no SavedStateHandle da leitura, inclusive restaurando o antigo estado booleano da Galeria.
