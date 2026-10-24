# Design final aprovado — Comic TV

Registrado em 24/10/2026 por solicitação explícita do usuário.

## Referência vinculante

![Tela inicial aprovada](approved-home.png)

A imagem define a composição final: lateral estreita, logo no alto, destaque com fundo visual e ação de continuar, leituras recentes compactas e coleção com capas grandes. Implementar em Compose nativo; não substituir por uma recoloração dos antigos painéis.

## Identidade oficial

O usuário forneceu duas artes posteriores à proposta. brand_symbol.jpg é o símbolo oficial; brand_wordmark.jpg é a assinatura COMIC TV com sua tipografia. Essa identidade tem precedência sobre o símbolo provisório da proposta. Nenhum arquivo de fonte TTF/OTF foi fornecido; preservar a tipografia da assinatura na própria arte.

A assinatura com texto também serve de fundo padrão da página principal e alternativa quando o fundo com capa desfocada está desligado.

## Requisitos permanentes

- Cores personalizáveis: amarelo, verde, azul, violeta e futuros presets.
- Alto contraste e texto ampliado preservados.
- Lateral inicia recolhida e expande ao focar ícones.
- Arte de fundo escurecida para legibilidade; capas visíveis em primeiro plano conservam proporções.
- Dados reais da biblioteca: títulos da imagem são ilustração da proposta.
- Retomada de leitura, histórico completo, confirmação de remoção e controles existentes preservados.
- Ajustes por tamanho de TV/acessibilidade devem preservar a composição aprovada.

## Refinamento após teste na TV — 0.9.1

A tela principal usa sempre a identidade Comic TV desfocada e mais escura. A opção de capa desfocada aplica-se somente aos detalhes da edição selecionada. Destaque recebe margem interna de 18 dp e separação de 14 dp do cabeçalho para afastar textos e botões das bordas.

## Temas aprovados — 0.9.2

Comic TV (dourado), Tema Flix (preto/vermelho), Tema Prime (marinho/ciano), Tema Max (índigo/violeta), Tema Cinema (preto/branco). As cores abrangem fundos, painéis, barras, botões, progresso e foco. Logo e ilustrações mantêm cores originais. Verde, azul e violeta antigos preservados para não invalidar preferências salvas.

As ações do destaque inicial (Continuar/Começar leitura e Importar) ficam neutras sem foco; a cor preenchida indica foco ou interação, evitando aparência de seleção permanente.

Cabeçalho da biblioteca: somente texto COMIC TV, usando Montserrat ExtraBold incorporada como aproximação da referência aprovada. COMIC branco e TV na cor do tema ativo; sem símbolo nem fundo retangular. Símbolo lateral e artes de fundo preservados. Fonte: https://github.com/JulietaUla/Montserrat; licença SIL OFL incluída no APK e em docs/licenses.

0.9.3: o topo da barra lateral expandida usa o mesmo cabeçalho textual Montserrat ExtraBold da biblioteca, sem imagem retangular; COMIC branco e TV na cor do tema.

Relacionados ficam abaixo da ficha, com capas e motivo explícito. Incluir biblioteca e amostra do catálogo da fonte; não inferir créditos pela sinopse. A API atual fornece principalmente escritor/desenhista; editora, personagens e direção dependem de metadados explícitos. Arquivos locais sem créditos mostram indisponibilidade. O símbolo recolhido acompanha o tema.

0.9.4: Relacionados ficam ocultos, com consultas desativadas, por solicitação do usuário. Preservar código para revisão futura. A implementação existente compara metadados sem IA. Centralizar o wordmark no menu lateral expandido; manter alinhamento do cabeçalho da biblioteca.

0.9.5: acabamento de catálogo e extensões. Capas do catálogo preservadas inteiras, com legenda de duas linhas abaixo; cabeçalho e busca rolam com a grade, mantendo restauração do item. Grades se adaptam ao texto ampliado. Extensões e fontes usam ícones compactos e cartões com altura flexível; abas distinguem seleção e foco e exibem contadores. Lista de extensões por repositório também em grade. Temas, instalação e Relacionados ocultos preservados.
