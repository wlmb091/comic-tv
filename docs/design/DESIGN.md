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
