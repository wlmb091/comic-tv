# Validação — Comic TV 0.7.9

23/10/2026 · versionCode 18 · Android 8/API 26 ou superior.

## Alterações

- Galeria opcional nas ferramentas, substituindo o ícone de mover/zoom: página atual central maior e páginas anterior/próxima menores nas laterais, ajustadas inteiras por padrão.
- Páginas laterais carregam sob demanda sem modificar progresso; primeira/última página têm o espaço vizinho ausente. RTL inverte os vizinhos visuais.
- Prévia que falha não impede ler a página central. Abertura da linha do tempo continua usando miniaturas; ao fechar, voltam os vizinhos da Galeria.
- Zoom e deslocamento pelo controle continuam disponíveis para a página central. Ativar/desativar Galeria retorna ao ajuste de página e zera zoom/deslocamento.
- Estado da Galeria preservado no SavedStateHandle da leitura. EPUB de texto mantém seu leitor próprio.
- Clique curto no OK (<1 segundo) alterna mover/zoom; segurar por 1 segundo abre as ferramentas uma única vez, sem ativar botão ao soltar.

## Verificação

- 153 testes JVM passaram, sem erros/falhas: 87 core, 10 mihon, 56 app.
- Dois testes novos verificam vizinhos, limites, progresso, desativação, restauração e transição da linha do tempo; testes do clique central atualizados para o limiar de 1 segundo.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Assinatura APK validada com apksigner; aapt2 confirma 0.7.9/code18/minSdk26.
- Verificador de ABI DEX passou para o método padrão executável de GeneratedSerializer.
- SHA256: 98D7ABD869A1C1CC95FA7A5A3098B33383ACE85118EA30D1ADAC22F2558E78FE.

## Limites

Sem aparelho ou emulador conectado. Conferir disposição visual, foco, Galeria em fontes online/PDF/arquivos e leitura automática na TV. Galeria pode baixar duas páginas vizinhas adicionais; elas não contam como lidas. O teste de build do APK de instrumentação não equivale à execução em dispositivo.
