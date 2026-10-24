# Validação — Comic TV 0.9.0

24/10/2026 · versionCode 24 · Android 8/API 26 ou superior.

## Alterações

- Referência final aprovada registrada em AGENTS.md e docs/design/DESIGN.md, com imagem preservada no projeto.
- Identidade oficial aplicada à assinatura, fundo padrão e banner. Ícone vetorial adaptado do símbolo fornecido.
- Biblioteca nativa com destaque dinâmico, ações de leitura/detalhes, leituras recentes compactas e coleção com capas grandes.
- Fundo com identidade oficial quando a capa desfocada está desligada; alto contraste mantém preto. Cores personalizáveis preservadas.
- Revisão corrigiu registro de foco antes de abrir o destaque e altura mínima flexível do destaque/abas para texto ampliado.

## Verificação

- 168 testes JVM sem erros/falhas: 90 core, 10 mihon, 68 app.
- Três novos testes verificam seleção do destaque por foco, histórico visível e biblioteca vazia/nova.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Teste Compose de inicialização recolhida atualizado e compilado; não executado em dispositivo.
- Assinatura apksigner e metadados aapt2 verificados: 0.9.0/code24/minSdk26.
- Verificador ABI DEX e consultas de migração/histórico em SQLite local passaram.
- SHA256: 4FE118F20DED4D04E0ED663E38071240E6A952C8741E963D990A038034C56760.

## Conferência na TV

Sem aparelho conectado nesta etapa. Conferir foco inicial e retorno do leitor, destaque por seleção, biblioteca vazia/preenchida, texto ampliado, cores, alto contraste e fundos. A referência orienta a composição; correspondência visual e navegação reais ainda precisam de conferência na TV.
