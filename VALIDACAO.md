# Validação — Comic TV 0.7.5

23/10/2026 · versionCode14 · Android8/API26 ou superior.

## Alterações

- Catálogo com cabeçalho, controles e busca dentro da grade rolável. Busca recolhível, capas inteiras 2:3 e nomes em até três linhas sobre fundo escuro. Grade adaptativa de aproximadamente cinco colunas na área de TV comum, dependendo da resolução/densidade.
- Barra lateral de 60dp com ícones, nomes acessíveis e rótulo ao receber foco; substitui botões largos de166dp.
- Resultados sem capa consultam detalhes pela própria extensão somente para cartões compostos pelo catálogo. Até duas consultas simultâneas, cache LRU128 por fonte/título, cancelamento ao sair da composição. Falhas não são armazenadas: revisitar um item permite nova tentativa, podendo gerar outra consulta.
- Evidência: busca HQNow por Spawn retornou nomes completos, sem campo de capa; detalhes do título1454 trouxeram hqCover com resposta HTTP200/jpeg. O código da extensão não solicita hqCover na busca. Os números isolados na imagem eram compatíveis com texto cortado pelo viewport; não foram inventados nomes ou substituídos dados da fonte.
- Leitor de imagens: toque central inferior a1s alterna Zoom/Pan; segurar2s abre ferramentas, consumindo a soltura para evitar clique acidental no menu. Intervalo1–2s não executa ação. Repetições não reiniciam o cronômetro; pausa, saída e perda de foco cancelam o gesto. Timeline mantém confirmação rápida e EPUB de texto mantém controles anteriores.

## Verificação

- 136 testes JVM passaram, sem falhas/erros: 87 core,10 mihon,39 app.
- Novos testes cobrem cache de capas, isolamento por fonte, retentativa, limite de concorrência/cancelamento e limiares/repetição/cancelamento do botão central.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Verificador DEX confirma implementação padrão GeneratedSerializer preservada no APK.
- apksigner verificou assinatura; aapt2 confirma versão0.7.5/code14/minSdk26.
- SHA256: EC0E90ECFCFFF60EEA6E42AAA8899EBE0A1979B2B02D213BBA5D254638BF605F.
- Revisões independentes de catálogo/ícones e gesto do leitor sem bloqueios.

## Verificação no aparelho pendente

Não houve execução em TV/emulador nesta etapa. Testar foco/D-pad, busca Spawn e carregamento gradual das capas, rolagem completa, retorno dos detalhes ao cartão e gesto rápido/longo no controle físico. Algumas fontes podem não fornecer capa ou retornar erro; nesse caso o título e a indicação de capa indisponível permanecem visíveis.
