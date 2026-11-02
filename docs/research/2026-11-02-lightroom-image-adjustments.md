# Ajustes de imagem: análise do Lightroom Mobile como referência

Data: 02/11/2026. Serve ao item 9 do próximo pacote: restaurar quadrinhos desbotados, com valores salvos por quadrinho na TV. Nada foi implementado.

## Artefato

- Arquivo: `Lightroom_11.8.05_1791718922_latestmodapks.com.apk`, 170.231.128 bytes.
- SHA256: `89101434938ed91f70f52800c77197c34323227f815f62a1a7563b0faa17b1b9`.
- Pacote `com.adobe.lrmobile`, versão 11.8.05, código 711108050, min SDK 26, só arm64-v8a.
- Método: análise estática com REA/JADX e inventário do ZIP. O APK não foi executado nem instalado, e a assinatura não foi verificada.
- Evidências REA: `ev_64438f434646e0b6f39f83c66791a7dcd7be1395bbc9396ba76e0d97b5882693` (pacote), `ev_ac819fe66d12d6b1f333cef9e2383f9a296f42199c70799f8e89b92ed660feac` (classes de revelação), `ev_3fe032ed19f37a8a5b48e32bd9af26af062a53ee7c1268d42b48b93fa280e944` (lista de ajustes), `ev_6e6c2a19bee16235c0b7c1fdb6183e73578f68d3c359deb8f46027189abd3e2b` (parâmetros).

## Cópia modificada por terceiros: não instalar

O APK não é a versão original da Adobe. Três bibliotecas têm nomes ou textos alheios ao app:

| Biblioteca | Observado no arquivo |
| --- | --- |
| `libGETMODPC.so` | `verifySignatures`, `expectedSignature`, `createPremiumDialogBackground`, `HttpURLConnection` |
| `libstub.so` | endereço iniciado por `https://raw.githubusercontent.com/getmodpc/new_upda` |
| `libjiagu.so` | referência cruzada em `libGETMODPC.so`; datas de empacotamento iguais |

Inferência: um terceiro alterou o pacote e ele pode buscar conteúdo na internet. O comportamento em execução não foi observado.

Por isso, e por ser software proprietário, este APK é só referência de estudo. Nenhum código, tabela, shader, perfil ou arquivo dele entra no Comic TV.

## O que foi observado

**O cálculo fica no nativo.** O Java/Kotlin só guarda valores e chama a biblioteca: `TIAdjustParamsHolder` e `TILoupeDevHandlerAdjust` usam métodos nativos `ICB...` e um ponteiro `long`. O motor é `libLrAndroid.so` (69,5 MiB). O código Java não contém as fórmulas.

**Modelo de parâmetros.** Cada edição é um conjunto de números aplicado ao original ao exibir, em vez de gravar pixels alterados. A lista `TIAdjustmentApiType` inclui `EXPOSURE`, `CONTRAST`, `HIGHLIGHTS`, `SHADOWS`, `WHITES`, `BLACKS`, `VIBRANCE`, `SATURATION`, `TEXTURE`, `CLARITY`, `DEHAZE` e a curva por faixas (`TONE_CURVE_*`).

**Mistura de cores.** São 8 faixas (vermelho, laranja, amarelo, verde, água, azul, roxo, magenta), cada uma com matiz, saturação e luminância: 24 valores (`HueAdjustment*`, `SaturationAdjustment*`, `LuminanceAdjustment*`).

**Realces e sombras parecem locais.** Nomes como `MakeBaseToneMapMaskCache`, `DownsampleToneMapMask`, `UpsampleToneMapMask` e `ComputeLocalContrastMask-Blur` indicam uma máscara desfocada de iluminação. Inferência pelos nomes: o ajuste depende da vizinhança do pixel. Isso custa mais processamento.

**Outros.** Há estágios em versão simples e SIMD (`RefIncreaseVibrance32`, `SIMDIncreaseVibrance32`), parâmetros separados para aumentar e reduzir vibração, `AutoToneHandler` e `TIWhiteBalanceMode`. `assets/LUT/Identity.png` tem 512×512; onde é usado não foi determinado.

## Não respondido

- As fórmulas exatas e a ordem completa do processamento. Decisão: não descompilar o motor nativo. Fórmulas públicas de imagem bastam, e o objetivo é implementação própria, não cópia.
- As faixas numéricas dos sliders (`GetScaleFactor` não foi lido). Serão definidas por nós.

Consequência: o resultado será parecido em função, não idêntico ao Lightroom.

## Proposta para o Comic TV (implementação própria)

Escolhas de projeto, baseadas em técnicas públicas:

1. **Valores por quadrinho:** seguir `ReaderImagePreferences` e `ImageEnhancement`, com o arquivo original intacto e os valores no backup.
2. **Ordem:** exposição, contraste, realces e sombras, brancos e pretos, vibração e saturação, mistura de cores.
3. **Tabela de cor pré-calculada (3D LUT):** ao salvar, os valores geram uma tabela pequena, e cada pixel só consulta essa tabela. O custo por página não cresce com a quantidade de ajustes, o que favorece a Mi Box. A mesma tabela no celular ajuda a manter a mesma aparência nos dois. O tamanho (17³ ou 33³) será decidido medindo na Box.
4. **Realces e sombras globais**, em vez da máscara local do Lightroom: mais leve e sem halos em volta do traço. Quadrinho tem áreas chapadas, então a perda deve ser pequena. A confirmar com páginas reais.
5. **Cálculo em luz linear** na exposição (multiplicar por 2 elevado ao valor), técnica fotográfica padrão.

Sugestões além do pedido, úteis para desbotado:

- **Temperatura e matiz**, com conta-gotas no papel: papel amarelado costuma ser o maior defeito.
- **Vibração**, que reforça as cores fracas sem estourar as fortes.
- **Botão Auto**, calculado pelo histograma da página.

## Pendências de medição

- Tempo de abrir página com ajustes na Mi Box.
- Diferença visual entre iPhone e TV com a mesma tabela.
