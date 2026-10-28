# Netflix e HBO Max para TV — pesquisa oficial

Consulta: **28/10/2026**. Pesquisa pública limitada a fontes dos fabricantes, Google Play e imagens vinculadas por essas páginas. Nenhum login, APK baixado, instalação, extração de código ou execução dos aplicativos. HTML consultado é das páginas públicas; não equivale ao código da interface nativa Android TV.

## Precedência das fotos do Comic TV

[Fotos e decisões 1.2.2](../design/themes/photos-1.2.2/README.md) continuam vinculantes: Flix com lateral em degradê, Max com dock inferior; arte e dados reais, texto 85–130%, alto contraste e D-pad preservados. As referências abaixo informam composição e legibilidade, sem autorizar alteração dessas estruturas.

## Netflix: distinguir a TV atual do material antigo

- [Tudum: novo layout de TV](https://www.netflix.com/tudum/articles/netflix-new-tv-layout), publicado em 07/05/2025: lançamento gradual a partir de 19/05/2025, atalhos no topo, fileiras responsivas e informações do título durante a navegação. A [captura vinculada](https://dnm.nflximg.net/api/v6/a01MIaQTUgSJoLaS_OCn68Eg3hU/AAAAQedxxnj125xOBTrlSsm5w2DmZfPdKXmD4OELbkQB7kym3HcM6c0BagQi1dh9vthiY7GQT2ZlpkVLWrvx1bwnFd8nbteMRUUyPMRRfczMIjTLPBx6pM6jhxbduZBKMp9RRPVpoWEr5ofTwckAFWyd54vU.jpg?r=bc8) foi inspecionada: cartão ativo horizontal, contorno branco, cartões verticais vizinhos, fundo preto e metadados/sinopse abaixo. É material de TV, sem controles táteis ou moldura de celular. Não comprova o layout de todo aparelho/região em 2026.
- [About Netflix: anúncio da experiência de TV](https://about.netflix.com/en/news/unveiling-our-innovative-new-tv-experience), 07/05/2025: confirma mudança de atalhos da esquerda para o topo. O [GIF oficial](https://downloads.ctfassets.net/4cd45et68cgf/61IUzZnwqhPnnjpf5OhOmf/400fcfd2c00744f7375bac0b584cd8b4/Wednesday-slide16.gif?w=2000) foi aberto no navegador e observado em dois estados: fileiras diferentes, cartão ativo largo, vizinhos verticais, contorno claro e texto associado. É demonstração de marketing animada; duração, easing, atraso de foco e desempenho não foram medidos. Recursos de IA descritos nessa página pertencem ao experimento móvel, não são evidência de busca de TV.
- [Ficha oficial Google Play, `com.netflix.ninja`](https://play.google.com/store/apps/details?id=com.netflix.ninja&hl=en_US), consultada mostrando atualização em 22/10/2026: a [primeira imagem](https://play-lh.googleusercontent.com/bdpCjYHbYEjH2-YmQw-gYkMu9S6IhrieOpV9rc6NIi_mtd6ScGPfGVN0A2o9fpEvz1q-HfVU8-p5nLs_hovpqg=w526-h296) é publicidade com TV, destaque panorâmico, texto/ações à esquerda e ícones laterais. Ela retrata composição anterior; a atualização da ficha não data a imagem. Não usar como prova da navegação atual.

## HBO Max: referência comprovadamente de TV

- [WBD: novo menu Max](https://press.wbd.com/na/media-release/hbo-max/new-max-navigation-menu-launches-today), 28/01/2025: anúncio para TVs conectadas, com distribuição gradual e diferenças regionais. Reúne navegação na esquerda e libera área superior para conteúdo. A [imagem oficial “New Navigation”](https://static.cdn.turner.com/inline-images/future_nav_1.jpg), identificada no HTML e inspecionada, compara lateral recolhida/expandida: arte até as bordas, título/descrição/CTA à esquerda, primeira fileira abaixo e degradê preto sobre a arte ao expandir. Marca no alto à direita; não há dock inferior nessa referência. A imagem estática não comprova animação.
- [Ajuda: navegação do HBO Max](https://help.hbomax.com/aw-en/answer/detail/000002559), consultada em 28/10/2026: distingue TV de celular/computador; descreve Home, fileiras de retomada/lista/recomendações, My Stuff e busca com lupa. Os exemplos de ícones oficiais incluem casa, marcador e lupa. Não documenta medidas, tipografia, curva de animação ou geometria de foco.
- [Google Play, `com.wbd.stream`](https://play.google.com/store/apps/details?id=com.wbd.stream&hl=en_US), consultado mostrando atualização em 21/10/2026: ficha atende múltiplos dispositivos. Imagens inspecionadas incluíam peça vertical e interface com barra de estado/controles de dispositivo móvel; foram descartadas como prova visual de TV. A ficha também não identifica a origem de cada screenshot por plataforma. A referência de TV adotada é a imagem explicitamente identificada no comunicado WBD.

## HTML, APK, fontes e movimento: limites da evidência

[Netflix orienta instalar por loja/preinstalação](https://help.netflix.com/en/node/101653); [HBO Max orienta Google Play em Android TV](https://help.hbomax.com/US/Answer/Detail/000002506). Não foi localizado link oficial público para baixar diretamente APK de TV nas páginas consultadas. Isso não prova inexistência de toda distribuição oficial. Nenhum APK ou fonte nativa foi obtido; não há base para afirmar tecnologia de renderização, assets internos ou comportamento D-pad em execução.

As páginas não fornecem especificação da família/peso tipográfico da UI de TV. O CSS editorial do site não determina a fonte do aplicativo. Observa-se apenas texto sem serifa, hierarquia de tamanho e peso e alto contraste. Não extrair ou incorporar fontes, logos, capas ou títulos de streaming no Comic TV.

## Aplicação possível ao recorte aprovado

| Tema | Direção sustentada pelas referências | Limite vinculante |
| --- | --- | --- |
| Flix | Arte panorâmica, degradação escura progressiva na esquerda, menu legível sem caixa clara; metadados concisos e diferença clara do alvo ativo. | Manter lateral aprovada e apenas Buscar/Continuar/Detalhes no destaque; controles secundários no final real. O topo da Netflix atual não substitui essa decisão. |
| Max | Arte até as bordas, contraste localizado para leitura, título/progresso/ação perto da retomada e primeira fileira próxima. | Manter dock e marca aprovados; arte continua atrás do dock. O menu lateral do serviço não substitui o dock. |

Movimento sugerido é **inferência de design**, não reprodução comprovada: transições discretas de opacidade e indicação de foco podem acompanhar a composição, sem mover alvos ou depender de vídeo automático. Acesso, saída por Voltar, restauração de foco e fluidez continuam exigindo verificação no aplicativo e teste físico na Box. Sem mudança de código ou novo APK nesta pesquisa.
