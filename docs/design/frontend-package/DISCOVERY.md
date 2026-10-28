# Descoberta e conciliação com1.2.3

## Temas e estrutura

AppearancePreferences.kt define ColorPreset/AppearanceOptions, seleciona e persiste preset, texto85–130%, contraste e preferências legadas emSharedPreferences. IDsAmber/Flix/Prime/Max/Cinema e restauração de backups devem permanecer compatíveis; ComicTv da proposta corresponde aAmber, não renomear IDpersistido.

TvShell emComicTvApp.kt constrói navegação e entrega de foco. ThemeNavigationPolicy centraliza apenas posição/direções: Sidebar,TopBar,BottomDock,ImmersiveMenu. Layout atual já muda de estrutura: Prime temPrimeLibraryHero/carrossel; Cinema drawerRight; Max dock; Comic capa esquerda; Flix lateral. A afirmação do pacote de que hoje sótroca cores é desatualizada, mas ainda há condicionais espalhadas na biblioteca e estilos compartilhados demais. FaltaThemeSpec/LocalThemeSpec completo.

LibraryScreen controla grade,Recentes,rodapé e restauração; LibraryHero desenhaComic/Flix/Max/Cinema; PrimeLibraryHero/PrimeEditionCard desenham banner/carrosselPrime. LibraryHeader/LibraryFilters/LibraryFooter organizam busca/coleções/histórico. TvButton/TvChoice/GlassPanel eComicTvTheme concentram alguns materiais, mas não tipografia/geometria/foco por variante. Próxima fase técnica: extrairThemeSpec e scaffold sem alterar visual; depois corrigir um tema por vez, começandoMax, com comparação de capturas.

## Dados e imagens

ComicEntity contémid,título,páginas,página atual,última leitura,importação,preferências,ocultação de histórico e favorito. Coleções/membros têm tabelas próprias; offline é informado por conjunto deIDs. Sinopse, número deedição e créditos não são campos universais deComicEntity: sóexibir quando houver origem real. Nunca usar conteúdo fictício das prévias.

LibraryRepository.cover aponta para capa local. libraryBackdrop/CinemaArtworkStore preferem arte manual por edição, depois primeira página local disponível e capa salva. CinemaBackdrop usaCoil, espera180ms para estabilizar escolha, transição160ms, decode limitado porCinemaImagePolicy/tamanho/baixaRAM, proporção original eCrop centralizado. Esse request desativa cacheCoil próprio; armazenamento das artes e cache de páginas são separados. Leitor/originais/USB e sua proteção de cache não fazem parte da mudança visual. BrandedBackground/CoverBackdrop têm caminhos separados para identidade/fundo de detalhes.

MontserratExtraBold está incorporada para a marca; outras fontes do documento ainda não estão incorporadas. Textos atuais incluem11/12/13sp, aquém do novo mínimo14sp: inventariar e adequar sóna camada deHome mantendo hierarquia/escala e alvos. Blur cinematográfico descrito no pacote ainda não existe de forma geral; não confundir reflexo deGlassPanel com desfoque óptico. EvitarRenderEffect obrigatório/API31 e desfoque por quadro.

## Diferenças e decisões a preservar

| Documento | Conciliação com o pedido atual |
| --- | --- |
| Max com6itens | Usuário acrescentouBuscar: preservar7itens. |
| Prime comcampoBuscar separado | Último pedido moveBuscar para navegação superior transparente; preservar. Carrossel foi aprovado. |
| Cinema semlogo | Logo foi reposta apenas no drawer expandido em1.2.3. |
| FundoComic#0b0b0d/Cinema#0a0a0c | Usuário especificou#0F0F0F nos neutros; preservar atépedido explícito de troca. |
| ApenasInter/Bebas/Barlow/Cinzel | ManterMontserrat da marca oficial; avaliar demais fontesOFL para conteúdo sem substituir a identidade. |
| BACK fecha painel→Home→sai | Preservar navegação interna/diálogos/leitor e confirmação explícita de saída; nenhuma interceptação global que pule níveis internos. |
| Max/Cinema collectionNONE | Composição principal pode ocultar a faixa, mas biblioteca/Histórico/filtros/coleções continuam acessíveis; não remover dados/funções. |
| Imagens≥1920×1080/never upscale | Preferir fonte completa disponível, não inventar resolução nem nova consulta. Capas verticais ainda são fallback autorizado, com recorte proporcional e eventual blur limitado. |
| Precisão pixel a pixel | Referências geradas têm arte/tipografia ilustrativas e texto deve acomodar85–130%/mínimo14sp. Registrar diferenças; não declarar igualdade sem capturas. |

Há inconsistências geométricas internas: Prime diz16:9, mas270×118 éaprox2.29:1; Comic prevê196×226 para capa vertical e194×194 para coleção. A implementação deve manter a capa inteira dentro do contêiner, sem deformá-la, e comparar a composição com a imagem antes de escolher proporção. Coordenadas do documento assumem1280×720; as prévias existentes não devem ser tratadas como esse canvas sem normalização.

## Verificação e limites da descoberta

Não háemulador nemsystem-images noSDK local inspecionado; nenhumaTV foi conectada/executada nesta descoberta. ValoresDisplay/densityDpi/screenWidthDp/screenHeightDp emruntime não foram coletados;960×540dp éhipótese de projeto, não medição daBox. MainActivity jáoculta barras e usa janela seminsets de sistema. Registrar as dimensões reais na fase de execução; não inferir UI4K a partir da resolução do painel.

Ainda não existem novas capturas de implementação resultantes deste pacote. Não classificar nenhum tema como matches. A validação deve compararHome, foco emcard, menu e fileira rolada, em100%/130% ealto contraste, com D-pad/Voltar/restauração. Compilação sozinha não satisfaz esse critério.

Esta entrega édocumental: pacote preservado, descoberta registrada, decisões conciliadas. Sem alterações de código/versão/APK nesta leitura. Não requer repetir build para mudança apenas documental.
