# Validação — Comic TV 0.8.1

23/10/2026 · versionCode 21 · Android 8/API 26 ou superior.

## Alterações

- Detalhes: título, informações e botões em um único fluxo à esquerda, sem região de título selecionável nem seu contorno amarelo. Capa maior no centro, com proporção preservada; painel do resumo à direita na mesma altura da capa. Informações/ações e resumo rolam independentemente.
- Ícone de visualização alterna Normal → Galeria → Livro → Normal, mantendo as ferramentas abertas para permitir novas trocas.
- Livro mostra a página atual e a seguinte juntas, com escala compartilhada, proporções preservadas e posição visual invertida em RTL. Navegação por pares, última página ímpar sozinha e seleção exata pela linha do tempo.
- Zoom e deslocamento funcionam sobre a abertura do livro. A posição salva é a primeira página do par.
- Leitura automática aguarda ambas as imagens serem exibidas, avança por pares e encerra no último. Falhas na segunda página têm mensagem e nova tentativa. Troca de modo limpa falhas da segunda imagem sem descartar erro da página principal.
- Fechar aguarda cancelamento das operações da página principal e da segunda página. EPUB de texto conserva navegação por seção e não é tratado como imagem adicional.
- Modo restaurado pelo SavedStateHandle, com compatibilidade para o antigo estado booleano da Galeria.

## Verificação

- 162 testes JVM passaram, sem erros/falhas: 87 core, 10 mihon, 65 app.
- Seis testes novos cobrem ciclo/restauração, pares/limites, RTL e deslocamento, espera das duas imagens antes do avanço automático, falha/nova tentativa/cancelamento e seleção exata pela linha do tempo.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Assinatura apksigner validada; aapt2 confirma 0.8.1/code21/minSdk26.
- Verificador ABI do DEX passou para GeneratedSerializer.
- Consultas reais de migração/histórico passaram novamente em SQLite local.
- SHA256: 209826293DDA05F455DAE45E85BC4E8A0998A9888027CA8DC89B17D7FCD48329.

## Conferência na TV

Sem dispositivo conectado nesta etapa. Conferir tamanho/posição da capa, navegação entre botões e resumo, visualização Livro com proporções diferentes, RTL, zoom e leitura automática. Os testes de instrumentação foram compilados, não executados em aparelho.
