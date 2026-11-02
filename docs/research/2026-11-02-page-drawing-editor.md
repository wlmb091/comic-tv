# Edição de desenho na página: pesquisa e proposta (02/11/2026)

Status: etapa 1 na 1.4.23 e etapas 2 a 4 na 1.4.24 (`docs/validation/1.4.24.md`). Páginas de texto e backup seguem pendentes.

## O que foi examinado

| Programa | Artefato | Como | Limite |
| --- | --- | --- | --- |
| Photopea | `www.photopea.com/code/pp/pp1793266415.js`, 2.646.875 bytes, SHA256 `551F441E53A1F893831F6A1BBFEAA14EA65D5C090FDCCA0AA515AE10FABF426A`; captura REA da página inicial `ev_6970d334ae5217206de9594a1bb4d7fe08cfccaf8379d0c0d1bd0810aabfb4ea` | Leitura estática de trechos do script minificado, mais o blog do autor | O editor não foi executado. O grafo REA estourou 120 s, com evidência parcial `ev_f4d1a76b62ef7c6d871f33634b70f95d45b7afd1979bbddf877c250c9ea427ef`. `ext` e `DBS` não foram lidos |
| GIMP | `app/paint/gimppaintcore.c` e `gimpbrushcore.c`, branch master | Fonte público lido por resumo | GIMP não está instalado aqui, então nada foi executado |
| PhotoCraft (ArtCraft, suposição a confirmar) | README de `storytold/photocraft` | Só o README | Código Rust não lido |

Nenhum código ou recurso de terceiros deve ser copiado. O Photopea é proprietário e o GIMP é GPL. Só as ideias gerais servem de referência.

## O que cada um faz

Observado no GIMP:
- Um traço vira uma série de carimbos, colocados em múltiplos de um espaçamento ao longo do caminho, com posição e pressão interpoladas (`gimp_brush_core_interpolate`).
- A suavização é uma média ponderada gaussiana dos pontos recentes (`gimp_paint_core_smooth_coords`).
- Durante o traço existe um buffer próprio de cobertura (`canvas_buffer`). No modo constante, cada carimbo é calculado sobre os pixels originais, então a sobreposição dentro do mesmo traço não escurece.
- O desfazer guarda só o retângulo sujo do traço, copiado do buffer original em `gimp_paint_core_finish`. Cancelar restaura esse retângulo.

Observado no Photopea:
- Usa WebGL2 com `BLEND` desligado. Os modos de mistura são feitos em shaders que leem origem e destino.
- Atualizações parciais de textura usam `texSubImage2D` com um retângulo.
- Lê `pressure` do evento de ponteiro e trata o mouse como pressão total.
- Há classe `History`, módulo WebAssembly e uso de `indexedDB`.
- O blog do autor diz que cada camada fica na memória WebGL: 850 ms sem WebGL e 55 ms com WebGL em 2048x1152 com 10 camadas e 3 efeitos.

Não confirmado no Photopea: o algoritmo do pincel, como o histórico guarda os dados e se existem blocos. Os nomes estão ofuscados, e as ocorrências de `Tile` e `Spacing` encontradas eram de outras funções.

Segundo o README do PhotoCraft (não verificado no código): blocos 256x256 esparsos com cópia sob escrita, traços determinísticos que podem ser refeitos, um compositor de CPU de referência e outro de GPU.

## O que pesa no Comic TV

- A TV só tem D-pad. O toque e a caneta estão no painel web do celular (`app/src/main/assets/companion/index.html`), que já usa eventos de toque e de ponteiro no Leitor.
- O celular já baixa a página por `/page/{livro}/{índice}`.
- A TV desenha em `HighResolutionPages.kt`, em um `Canvas`, com a posição vinda de `zoomPlacements`. Uma camada extra pode usar a mesma posição, e assim acompanha zoom e deslocamento.
- `tileIdentity` usa caminho e tamanho do arquivo. Páginas online vêm de cache temporário, então o desenho precisa de chave própria: edição, capítulo e índice da página.
- O cache de blocos é de 8 a 24 MiB, conforme o aparelho. A camada precisa ser decodificada no tamanho da tela e ter orçamento próprio.
- Regras já vigentes: originais nunca alterados e layout do Controle congelado.

## Proposta técnica

- Desenho no celular com canvas 2D. WebGL fica para quando muitas camadas exigirem.
- Traço: eventos de ponteiro, suavização, carimbos com espaçamento fixo, buffer próprio do traço e composição na camada ao soltar.
- Desfazer: retângulo sujo com orçamento de memória.
- Armazenamento: PNG transparente por camada e um JSON em pasta privada do app, fora da limpeza de cache. Envio à TV por rota nova com limite de tamanho, ao fim de cada traço.
- TV: mostra a camada por cima da página. Os filtros do leitor continuam só na página de baixo.

Valores a medir no aparelho, ainda não verificados: limite real de canvas do Safari no iPhone do usuário, latência do traço e memória da camada na Box.

## Referência visual do usuário (02/11/2026)

Foto: `docs/design/companion/page-editor-reference-2026-11-02.png`. O usuário pediu "algo mais ou menos assim só que com as cores de cada tema". É uma referência aproximada, e a implementação só começa com o aviso dele.

Na foto, de cima para baixo no Leitor do celular:
- **Topo:** a seta do menu flutuante continua no lugar.
- **Esquerda:** coluna de ferramentas no estilo Photopea (mover, seleção, laço, corte, conta-gotas, correção, pincel, carimbo, borracha, gradiente, texto, caneta, forma, mão, lupa, cores de frente e fundo).
- **Centro:** a página.
- **Direita:** coluna estreita de ícones de painéis, seguida dos painéis Histórico/Amostras e Camadas/Canais/Demarcadores. O painel de camadas tem modo de mistura, opacidade, bloqueios, preenchimento, olho, miniatura, nome e, no rodapé, as ações de camada.
- **Baixo:** a barra flutuante do Leitor (Anterior, Página X de Y, Sincronizado, Próxima) continua igual.

Anotações para a implementação:
- As cores acompanham os 5 temas, usando as variáveis de cor do painel. O layout do editor é um só em todos os temas.
- Na foto, a página ocupa cerca de 42% da largura da tela, medida a olho. Painéis recolhíveis são uma opção a confirmar com o usuário.
- A foto mostra mais recursos que a 1ª versão proposta. As dúvidas de escopo seguem em aberto.

## Barras do Leitor no modo edição (02/11/2026)

Foto: `docs/design/companion/page-editor-reader-bars-2026-11-02.png`. Palavras do usuário: "os sliders que ficavam a direita, vão para a barra da esquerda, funcionando somente do lado esquerdo. quando o modo edição for clicado, o icone da barra direita some e vice versa".

Estado atual no código (`companion/index.html`): no Leitor, os sliders de Zoom e Rolagem já ficam na barra esquerda (`readerSliders`), e a direita tem as opções do leitor (`menuHandle`). Os sliders que ficam à direita são os do Controle, que tem layout congelado.

Entendimento a confirmar com o usuário:
- Zoom e Rolagem do Leitor ficam só na barra esquerda.
- O Controle não muda.
- Ao entrar no modo edição, some o ícone da barra direita. Ao sair, ele volta e o editor some.

Em aberto:
- O que "vice versa" cobre exatamente.
- Onde ficam os sliders no modo edição, já que a foto do editor tem as ferramentas à esquerda.

## Decisão das barras (02/11/2026, substitui a seção anterior)

Respostas do usuário:
- Só os sliders do Leitor mudam. O Controle fica como está.
- A seta e a barra da esquerda passam a ser do modo edição.
- Barra esquerda expandida: somem a seta e a barra da direita.
- Barra direita expandida: somem a seta e a barra da esquerda.
- Zoom e Rolagem passam a fazer parte da barra direita. Esta resposta vale sobre a frase anterior, que falava em barra esquerda.

Proposta a confirmar:
- A barra direita tem 40 px e já mostra 9 ícones, o que não deixa espaço bom para dois sliders verticais. Um ícone "Zoom e rolagem" na barra abriria os sliders em painel ao lado, como o + faz hoje (`toggleMore`/`#menuMore`).
- Durante a edição a barra direita some, então os sliders não ficam disponíveis. Zoom e movimento ficam com dois dedos e com as ferramentas mão e lupa.
- Hoje as setas só aparecem com Sincronizar ligado (`body:not(.syncActive)`). Falta decidir se a seta de edição também depende disso.

## Respostas seguintes (02/11/2026)

- Ícone "Zoom e rolagem" na barra direita, abrindo os sliders em painel ao lado. Sliders menores e estreitos, para ocupar pouco espaço e não atrapalhar a imagem. A área de toque do controle deslizante não deve encolher junto, para o dedo continuar acertando.
- O usuário respondeu "sim, perfeito" a um bloco com três recomendações: dois dedos, mão e lupa na edição; Histórico e Camadas à direita; seta de edição sempre visível em páginas de imagem. Sem Sincronizar, a TV recebe o desenho quando abrir aquela página. Tratadas como aprovadas.

## Traço ao vivo na TV (pedido de 02/11/2026)

Pedido: com Sincronizado ligado, um risco feito no celular aparece na TV enquanto é feito, como o espelhamento que já existe.

Proposta, sem transmitir vídeo, como no arrasto de página atual (`reader-drag`, cerca de 20 comandos por segundo):
- Ao começar o traço, o celular envia ferramenta, cor, tamanho e opacidade.
- Durante o traço, envia lotes de pontos em coordenadas relativas à página, independentes de zoom e tela.
- A TV desenha o traço provisório na própria camada, usando a mesma posição da página.
- Ao soltar, o celular envia a camada em PNG, que substitui o provisório e garante o mesmo resultado nos dois.
- Desfazer, refazer, ocultar e trocar de camada são comandos à parte.
- Ferramentas que não são traço (balde, filtros, recorte) aparecem na TV ao terminar a ação.

Riscos a medir: atraso real na rede do usuário, fluidez do desenho na Box e pontos perdidos em Wi-Fi ruim (o PNG final corrige o resultado).

## Decisões de escopo (02/11/2026)

- 1ª versão: pincel, lápis, borracha, cor, tamanho, opacidade, desfazer/refazer e zoom, em uma camada. A tela já nasce com o visual da foto, e os botões ainda sem função ficam apagados, como no Controle.
- Tipos de página: o usuário respondeu "sim para todos os tipos". Isso inclui páginas online. Texto (EPUB e similares) depende da dúvida abaixo.
- Salvar: ao sair da edição ou trocar de página com alterações, abrir pergunta para salvar ou não, porque nem todo desenho será guardado. Proposta: Salvar, Descartar e Continuar editando. Só o salvo fica guardado e, proposta a confirmar, entra no backup. Descartar também limpa a TV.
- PhotoCraft é mesmo o do ArtCraft.

Decisão do usuário sobre texto: fica para a etapa seguinte, com a imagem fixa do trecho visível. A 1ª versão cobre as páginas de imagem, incluindo as online. Motivo: `EpubTextScreen.kt` ajusta o texto à tela com fonte de 18 a 40, e o celular e a TV têm larguras diferentes. Por isso, um traço preso à posição da tela não cairia sobre a mesma palavra nos dois, e mudar a fonte também o desalinharia.
## Apps de desenho para iPhone (02/11/2026)

O que não foi possível analisar por artefato:
- Procreate e Procreate Pocket não têm versão Android, e os IPA da App Store são criptografados. Depois o usuário forneceu um IPA do Procreate, analisado na seção "Procreate 5.4.4". Procreate Pocket segue só pela documentação.
- Sketchbook (suposição: é o "sketch" que o usuário citou) tem APK, mas só encontrei em sites de terceiros, que não foram baixados. Nada do Sketchbook passou pelo REA. Se o usuário fornecer um APK, dá para fazer análise estática.
- Esta seção vem da documentação oficial.

Procreate Pocket, segundo o manual oficial:
- Interface mínima ao pintar: ícone da ferramenta e sliders laterais de tamanho e opacidade. O resto abre em um menu expansível, e Cores e Camadas sobem como cartões condensados que mantêm a tela visível.
- Gestos: toque com dois dedos desfaz, com três refaz, e segurar repete rápido. Quatro dedos escondem a interface. Pinça rápida ajusta à tela.
- Desfaz até 250 ações, e o histórico é limpo ao sair para a Galeria ou fechar o app.
- Espaçamento define a frequência dos carimbos, e StreamLine suaviza os tremores do traço.
- Project Canvas mostra a tela em um segundo monitor sem interface nem zoom, a ideia mais próxima do traço na TV.

Sketchbook, segundo a ajuda oficial:
- Predictive Stroke e Steady Stroke calculam o traço depois de desenhado e podem atrasar em traços longos. No Comic TV, a suavização precisa funcionar durante o traço, para não atrasar a TV.

Aproveitamento proposto (ainda a aprovar):
- Gestos de dois e três dedos para desfazer e refazer.
- Botão ou gesto para esconder os painéis e desenhar com a página grande.
- Slider de suavização simples.
- Em Tamanho e Opacidade, usar sliders compactos na lateral.

Detalhe técnico: o MDN indica `getCoalescedEvents` restrito a contexto seguro em parte dos navegadores, e relatos do projeto Flutter apontam suporte no Safari a partir do iOS 18.2 com propriedades faltando. O painel roda em HTTP na rede local, então não dá para contar com ele. O traço deve funcionar só com `pointermove` e interpolação, usando o recurso apenas se estiver disponível. A testar no iPhone do usuário.

## Procreate 5.4.4: análise do IPA fornecido pelo usuário (02/11/2026)

Artefato: `Procreate-v5-4-4.ipa`, 777.430.382 bytes, SHA256 `7B1DAD3AC95F8AECA3C22B432B5D6FD94182A3BF9F84FB93588ADB126368B54E`. Identificador `au.com.savageinteractive.procreate`, versão 5.4.4, build 56832, `UIDeviceFamily` só iPad. Executável arm64 de 19.119.424 bytes, SHA256 `76d3ff1249a987b8f03ac3ecc64c54e48f618bac467e9fbb1f238b91fab1487b`. `cryptid` 0 e sem `SC_Info`: o executável já veio sem criptografia.

Como foi analisado:
- Inventário do pacote, comandos de carga do executável, textos do executável, nomes da biblioteca de shaders `ValkyrieMetal.metallib` e ajustes dos 394 arquivos `.brush`.
- O REA abriu o alvo, mas neste Windows não há analisador para Mach-O: `inspect_macho` exige macOS e `get_objc_classes` não tem provedor (Ghidra não configurado e sem suporte a Mach-O no Windows). A leitura foi feita com scripts próprios.
- Sem descompilação e sem execução. Os nomes são observados, e o papel de cada um é inferência. Nenhuma lógica interna foi confirmada.
- Nada do app entra no Comic TV: nem código, nem pincéis, fontes ou imagens.

Observações e inferências:

| Observado | Inferência | Uso proposto no Comic TV |
| --- | --- | --- |
| `predictedTouches`, `Predicted Stroke Pass`, `predicted_stroke_attachment`, `PredictedTiles`, `uncommittedTiles`, `commitTiles`, `_rollbackTiles` | Parte do traço é provisória e separada do que foi confirmado | Reforça o plano da TV: traço provisório, trocado pelo definitivo ao soltar |
| `paintSize`, `eraseSize`, `paintOpacity`, `eraseOpacity`, `savedPaintSizes`, `savedEraseSizes` em cada pincel | Pintar e apagar guardam tamanho e opacidade próprios | Pincel, lápis e borracha lembram o próprio ajuste |
| `plotSmoothing`, `plotMovingAverageStabilization`, `plotFFTSmoothingAmount` por pincel | A suavização é parte do pincel, e não só um ajuste geral | Suavização própria por ferramenta |
| `pencilTaperStartLength`, `pencilTaperEndLength`, `pencilTaperSize`, `dynamicsSpeedSize`, `dynamicsPressureSize` | Início e fim do traço afinam, e a velocidade também pode mudar o tamanho | Afinar as pontas para dar traço de nanquim no dedo, onde não há pressão real |
| `SiUndoManager`, `SiUndoGroup`, `undoDirectory`, `compressUndos`, `undoName`, `Paint stroke`, `Erase stroke`, `levelsOfUndo` | Histórico com nomes por ação e dados comprimidos em disco | Painel Histórico com nomes, como na foto, e etapas antigas comprimidas |
| `ValkyrieTile`, `ValkyrieLayerChunk`, `modifiedTiles`, `nonEmptyTiles`, `ValkyrieKernelTileEmpty` | Camada em blocos, com controle de blocos alterados e vazios | Enviar à TV só o retângulo alterado, e a camada inteira ao salvar |
| `gestureTap1` a `gestureTap4`, `gestureHold`, `undoTap`, `rapidUndoDelay`, `multiUndoHold` | Gestos configuráveis, batendo com o manual | Gestos de dois e três dedos |
| `Eyedropper`, `ColorDrop`, `QuickShape`, `Clone Puck`, `SmudgeTool`, `Liquify`, `Selections` | Ferramentas das etapas futuras | Referência para ordenar as etapas |

Ajustes de `plotSpacing` (escala interna não confirmada), mediana por grupo: Inking 0,000; Calligraphy 0,000; Sketching 0,002; Painting 0,006; Airbrushing 0,018. Isso sugere carimbos muito próximos, com traço quase contínuo.

Ajustes de `plotSmoothing`, mediana por grupo: Calligraphy 0,666; Inking, Sketching, Painting e Airbrushing 0,000. Ou seja, a suavização forte aparece na caligrafia, e a maioria dos pincéis de esboço e pintura não usa este ajuste.

Não confirmado: como o Valkyrie desenha o traço, o tamanho dos blocos, a fórmula do espaçamento e o formato do histórico em disco.

## Sugestões aprovadas para a 1ª versão (02/11/2026)

O usuário respondeu "Sim, todas" às sugestões vindas do Procreate:
- Dois dedos desfazem, três refazem, e segurar repete rápido.
- Botão ou gesto para esconder os painéis e desenhar com a página grande.
- Sliders compactos de Tamanho e Opacidade na lateral.
- Suavização própria por ferramenta, em vez de um slider geral.
- Pontas afinadas no traço.
- Pincel, lápis e borracha lembram o próprio tamanho e opacidade.
- Painel Histórico com nomes por ação, e etapas antigas comprimidas.
- A TV recebe só o trecho alterado ao soltar o dedo, e a camada inteira ao salvar.

Fora desta aprovação, seguem como ideias opcionais: comparar com o original, texto e balões logo depois da 1ª versão, e exportar como CBZ. A implementação continua aguardando o aviso do usuário.

## ibisPaint X 11.0.5 Mod: análise do APK fornecido pelo usuário (02/11/2026)

Artefato: `Ibis Paint XV11.0.5Mod.apk`, 54.628.712 bytes, SHA256 `1776E9DD38A1C81B2D73F2B8AA429F092D39EDA236F403DCBCA3727DAE1B70C1`. Pacote `jp.ne.ibis.ibispaintx.app`, versão 11.0.5, code 1100050018. `lib/arm64-v8a/libibispaint.so`: 34.234.576 bytes, SHA256 `5fca7131631b91cfd7f569bd6664a6f19d0630d8c5b33a4d401d2972730574ec`.

Procedência: é uma versão modificada, assinada com o certificado genérico "Android" (SHA-256 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`), o que indica que não veio assinada pelo fabricante. O que o mod alterou não foi examinado, e as conclusões podem não valer para o app original. Não foi instalado nem executado. Recomendação: não instalar este arquivo na Box nem no celular.

Como foi analisado:
- REA com JADX: pacote (`ev_47e43a44f0072a8fd476dd3d8ebeb54f3a8481cea37b598bed58ce4e5dc8ae82`), classes Java e `GlapeView.onTouchEvent` descompilado (`ev_b78776ca16dcd728877253bc520030647b9419e809e64181057e3d39e6784e89`).
- `aapt2` e `apksigner` para identidade e assinatura.
- Scripts próprios para nomes de classes e métodos da biblioteca nativa, que vieram preservados (775 classes, 32.135 símbolos).
- O motor nativo não foi descompilado, porque não há Ghidra disponível. Os nomes são observados, e o que cada um faz é inferência. Só o Java do toque foi lido de fato.

Observações e inferências:

| Observado | Inferência | Uso no Comic TV |
| --- | --- | --- |
| `LayerManager.composeCanvasWithDrawingRectangle`, `EditTool.composeDrawingWithBoundingBox`, `getDrawingLayerBoundingBox`, `isComposeAtTouchUp`, `isPendingCompose` | O traço fica em camada temporária e é aplicado ao soltar, dentro de um retângulo | Mesmo plano do buffer de traço; confirma GIMP |
| `isRealTimeStabilizer`, `isStabilizationAfter`, `canRemoveSamePointStabilizationAfter`, `StabilizationWindow` | Há suavização durante o traço e depois dele | Só durante o traço, para a TV não atrasar |
| `DrawChunk.setPoints`, `setOriginalPressure`, `setCurrentBrushParameter`, `getEndTime`, `PointSubChunk`, `BrushParameterSubChunk` | O traço é gravado como pontos, pressão e parâmetros do pincel | Mesmo formato para o envio ao vivo |
| `UndoCacheFile` (`compact`, `truncateNowPosition`, `fixDamage`), `UndoCacheChunk` (`isDifferenceImageUndoType`, `isFullImageUndoType`), `MemoryHistory`, `EditTool.executeUndo` | Desfazer em arquivo, com imagens de diferença ou inteiras e compactação | Guardar a diferença do retângulo; manter a página original fora do histórico |
| `getThicknessOfStart`, `getThicknessOfEnd`, `getAlphaOfStart`, `getAlphaOfEnd`, `getThicknessToSpeed`, `getOpacityToSpeed`, `getThicknessToPressure`, `getSpacing` | Pontas e velocidade controlam espessura e opacidade | Pontas afinadas e espessura por velocidade no dedo |
| `TouchType.Cancelled` em `GlapeView.onTouchEvent`, `BrushBaseTool.cancelAction`, `notifyCancelledAndDecompose` | O toque pode ser cancelado e o traço provisório, desfeito | Se um segundo dedo chegar logo, cancelar o traço para o gesto não deixar risco |
| `PaintTool.isTemporaryEraserMode`, `BrushToolEraser` | Borracha temporária | Possível etapa futura |
| `FillGap`, `FillExpansion`, `SpuitTool`, `LassoTool`, `RulerTool`, `SymmetryRulerTool`, `TextTool`, `FrameDividerTool`, `MangaManuscriptSettingsWindow`, `VectorLayer` | Ferramentas de quadrinhos para etapas futuras | Balde que respeita falhas no contorno, régua, texto e divisão de quadros |
| `TouchEventRecorder`, `TouchEventPlayer`, `VectorPlayer` | Gravar e reproduzir o desenho | Ideia futura: reproduzir o desenho na TV |

Comparação para escolher a referência:
- ibisPaint: feito para celular, com foco em quadrinhos e mangá, em Android e com a análise mais profunda possível até aqui. Melhor referência para traço, histórico e ferramentas.
- Procreate: feito para iPad com Metal. Boa referência de gestos e ajustes de pincel.
- GIMP: código aberto legível, mas não pode ser copiado por ser GPL. Boa referência do ciclo do traço.
- Photopea: referência do visual escolhido pelo usuário, em painéis.

Decisão do usuário (02/11/2026): ibisPaint é a referência principal do comportamento, com o visual da foto do usuário. "Replicar" significa reproduzir as ideias com código próprio. Nada do ibisPaint entra no Comic TV: nem código, nem shaders, pincéis ou imagens, porque é um app proprietário.

Não confirmado: algoritmo de suavização, fórmula do espaçamento, formato do arquivo de histórico e detalhes dos shaders do pincel.

## Subferramentas no mesmo ícone (02/11/2026)

Pergunta do usuário: cada ferramenta, como borracha e pincel, tem subcategorias no mesmo ícone, e a foto só mostra a primeira. Isso ainda não estava na 1ª versão.

Aprovado pelo usuário ("pode incluir"):
- Toque seleciona a ferramenta. Segurar o ícone, ou tocar de novo no ícone já ativo, abre a lista ao lado.
- Uma marca pequena no canto indica que o ícone tem subferramentas.
- O ícone passa a mostrar a última subferramenta usada.
- Pincel: Pincel redondo, Nanquim e Lápis.
- Borracha: Dura e Macia.
- Cada subferramenta lembra o próprio tamanho, opacidade e suavização.
- Nos demais ícones, a lista já existe, com as opções apagadas até a etapa de cada uma.

## Mapa de menus e subcategorias do ibisPaint (02/11/2026)

Pedido do usuário: replicar as subcategorias de tudo, incluindo botões que abrem outras opções.

Fonte: `assets/lang/pt_Localizable.txt` (3.230 textos, com seções comentadas) e nomes de classes de janelas da biblioteca nativa. O app não foi executado, então a organização vem dos nomes e das seções. A ordem exata e o caminho de toques não foram vistos.

- Seletor de ferramentas: Transformar, Varinha Mágica, Laço, Filtro, Pincel, Borracha, Borrar, Desfoque, Lata de Tinta, Texto, Moldura, Conta-gotas, Tela, Definições, Especial.
- Pincel e Borracha:
  - Listas: Básico, Personalizado, Online, Histórico, além de grupos como Desfocagem, Dispersão, Textura e Avançado.
  - Parâmetros por aba: Básico, Desvanecer, Forma, Distorção, Tipo, Textura, Dinâmica, Definições.
  - Famílias: caneta de tinta, feltro, digital, aerógrafo, lápis, água, óleo, carvão, pastel, giz.
- Estabilizador: Força, Comprimento do Início e do Fim, Forma do Desvanecer (Suave, Linear), Método (Tempo Real, Posterior).
- Ferramenta de Desenho: Não, Linha, Retângulo, Círculo, Elipse, Polígono Regular, Curva de Bézier, Polilinha; com Preencher, Cor de Preenchimento e número de cantos.
- Lata de Tinta: Layer Atual, Específica ou Tela; Força, Reconhecimento de Espaços, Preenchimento Contínuo, Sob a Linha, Expansão, Balde Borracha, Definições Fáceis.
- Varinha Mágica: Definir, Adicionar, Subtrair.
- Especial: Preenchimento Laço, Borracha Laço, Preenchimento e Borracha Circundante, Mosaico, Caneta de Dissolver (Arrastar, Encolher, Expandir, Suavizar, Reverter), Caneta de Cópia (Relativo, Fixo, Mover), Forma de Preenchimento (Sobreposição, Saturação, Exclusão).
- Cor: Palete, Roda, HSB/RGB, Código de Cor, Conta-gotas Rápido.
- Camada: Cortando, Alpha Lock, Pasta, Fundo, Renomear, Rasterizar, Selecionar Opacidade, Branco Claro, Guardar como PNG; modos de mistura em grupos Escurecer, Clarear, Contraste, Diferença, Cor, Especial.
- Seleção: Remover, Inverter, Eliminar, Selecionar Opacidade, Cortar, Copiar, Colar, Gama de Cores.
- Transformar: Transladar e Escala, Perspetiva, Rede; Interpolação, Repetir, Operação.
- Tela: Tamanho, Aparar, Redimensionar, Modo de Cor, Rodar, Inverter.
- Filtro, cerca de 60 no total: Ajustar Cor, Borrão, Enquadramento, Camada Inferior e Superior, Desenho, Transformar, Artístico, Inteligência Artificial, Píxelar, Filme.
- Régua: Reta, Circular, Elíptica, Radial, Simetria.
- Texto: Texto, Tamanho, Estilo, Fundo, Espaçamento, Ordem, Eliminar.
- Moldura: Adicionar, Dividir, Selecionar; Espaço Horizontal e Vertical, Espessura.
- Ver: Janela de Referência, Mostrar ao ampliar (Suavizar, Pixelizado), Grelha e suas definições.
- Definições: Gesto (dois dedos desfazem, três refazem), Interface, posição da ferramenta (Esquerda, Direita, Pop-up), Caneta (rejeição da palma, pressão, botões).

Etapas aprovadas pelo usuário:
1. Pincel, Borracha, Cor, Estabilizador, Histórico, Gestos. Os demais ramos aparecem apagados.
2. Camadas, Conta-gotas, Lata de Tinta, Ferramenta de Desenho.
3. Laço, Varinha, Seleção, Transformar, Texto, Moldura.
4. Borrar, Desfoque, Especial, Régua, Filtros.

Fica de fora, aprovado pelo usuário:
- Animação, Inteligência Artificial, nuvem, publicação, assinatura e galeria.
- Em Tela: Tamanho, Aparar e Redimensionar, porque a camada é presa ao tamanho da página.

Nomes e organização dos menus servem de referência. Os pincéis, texturas, textos e ícones não são copiados: serão criados no projeto.

## Fontes

- https://raw.githubusercontent.com/GNOME/gimp/master/app/paint/gimppaintcore.c
- https://raw.githubusercontent.com/GNOME/gimp/master/app/paint/gimpbrushcore.c
- https://blog.photopea.com/photopea-1-3.html
- https://raw.githubusercontent.com/storytold/photocraft/HEAD/README.md
- https://help.procreate.com/pocket/handbook/interface-gestures/interface
- https://help.procreate.com/pocket/handbook/interface-gestures/gestures
- https://help.procreate.com/pocket/handbook/brushes/brush-studio-settings
- https://help.sketchbook.com/docs/steady-and-predictive-stroke
- https://developer.mozilla.org/en-US/docs/Web/API/PointerEvent/getCoalescedEvents
- https://dart.googlesource.com/external/github.com/flutter/engine/+/23447c4674b0837ada66d29099147e05a695cec4
