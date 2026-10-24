# Qualidade de imagem e antecipação — 0.10.0

Etapa aprovada após a revisão do pedido: melhorar zoom, legibilidade de digitalizações antigas e espera nas imagens, preservando a compatibilidade com Android TV. Navegação por painéis individuais fica para uma etapa futura. Não há IA, detecção automática de quadros, interpolador Lanczos próprio ou shader personalizado nesta versão.

## Uso

No leitor de imagens, segure OK por 1 segundo e abra **Imagem e mais opções**:

- **Original** mantém os pixels decodificados sem filtro.
- **Texto e traços** aplica nitidez moderada e limitada para realçar contornos.
- **HQ antiga** combina ajuste de sombras/gama e nitidez leve para digitalizações apagadas.
- **Intensidade** alterna suave, média e forte.
- **Comparar original** alterna a visualização temporariamente, preservando a escolha do filtro.

Filtro e intensidade são salvos por edição. Arquivos importados, imagens da fonte e miniaturas não são alterados. Texto EPUB mantém seu leitor e ajuste de fonte próprios.

## Zoom

Normal, Galeria e Livro usam a mesma geometria de ajuste, zoom e deslocamento. Primeiro é exibida uma visão geral pequena; os recortes visíveis são decodificados do arquivo original por `BitmapRegionDecoder`, fora da interface, em blocos de 512 pixels decodificados com margem para o filtro. A amostragem acompanha a escala e evita perder detalhes que a resolução disponível possa mostrar.

O Canvas usa `FilterQuality.High`. A nitidez adicional é calculada na CPU por bloco, com contraste limitado, preservação de alfa e branco e cancelamento entre linhas. Não depende de `RuntimeShader`/Android 13. Quando o formato não aceita decodificação regional, a alternativa limita a decodificação completa a aproximadamente 2 milhões de pixels; nesse caso pode haver menos detalhe em zoom alto.

Ampliar não recupera letras ou traços inexistentes no arquivo. Filtros podem acentuar ruído; por isso o padrão é Original e há comparação rápida.

## Cache e prioridades

- A página atual é carregada primeiro. Após sua exibição, há uma pausa curta de 150 ms antes de preparar as próximas duas páginas. Em Livro, a antecipação começa depois do par atual.
- Arquivos antecipados também geram uma visão geral e blocos para a escala inicial. Ao ampliar/deslocar, até dois blocos vizinhos são preparados depois dos visíveis. O Android controla o envio dessas imagens às texturas da GPU; não há gerenciamento manual de texturas OpenGL.
- O cache LRU de bitmaps tem orçamento de 8 a 24 MiB, conforme a classe de memória do processo, compartilhado pelos blocos atuais e antecipados. A tela mantém referências aos blocos que está exibindo e há memória transitória de decodificação/filtros; o orçamento não representa toda a RAM do aplicativo. Itens antecipados podem ser descartados para respeitar o limite.
- As miniaturas usam arquivos próprios de 240×360, cache de disco de 32 MiB e a memória compartilhada do Coil de até 32 MiB. O aquecimento usa a mesma requisição/chave da linha do tempo.
- Antes de abrir a faixa, são aquecidas posições próximas da leitura. A faixa mostra até cinco posições, reutiliza imediatamente arquivos válidos conhecidos e antecipa mais duas além da janela na direção de movimento. Existe debounce curto para posições ainda frias.
- Páginas completas usam o cache de disco existente de 256 MiB. A página atual fica protegida durante extração/download antecipados. Miniaturas reaproveitam o original já disponível; originais obtidos para miniaturas também podem ser retidos nesse cache, evitando repetir o download/extração na leitura.
- Antecipação não registra progresso. A entrada na linha do tempo cancela o aquecimento da leitura e prioriza as miniaturas; a saída retoma o aquecimento. Operações de fonte são canceladas e aguardadas antes da navegação para outra tela.

O cache reduz espera quando há tempo para antecipar. Primeira abertura, navegação muito rápida, imagens grandes, fontes lentas e itens expulsos do cache ainda podem exigir carregamento.

## Xiaomi TV Box S de 2ª geração

As [especificações oficiais da Xiaomi](https://www.mi.com/global/product/xiaomi-tv-box-s-2nd-gen/specs/) informam 2 GB de RAM, GPU Mali-G31 MP2 e saída 4K. A implementação evita texturas de páginas inteiras em resolução ilimitada, limita caches e mantém `minSdk 26` (Android 8). A [API de decodificação regional do Android](https://developer.android.com/reference/android/graphics/BitmapRegionDecoder) é anterior a esse mínimo.

Isso fundamenta a escolha técnica; não comprova fluidez na TV Box. Sem aparelho ADB conectado durante o desenvolvimento, os testes de decodificação Android foram compilados, mas precisam ser executados no dispositivo. Conferir zoom de 100% a 300%, deslocamento, Galeria/Livro/RTL, filtros em HQ antiga, abertura rápida da faixa, retorno dela e saída durante downloads. Qualidade visual, memória real e tempo de resposta precisam dessa conferência.
