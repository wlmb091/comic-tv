# Validação — Comic TV 0.9.3

24/10/2026 · versionCode 27 · minSdk26.

## Alterações

- Cabeçalho da barra lateral expandida reutiliza o texto Montserrat ExtraBold: COMIC branco, TV na cor do tema, sem imagem retangular. Símbolo recolhido também acompanha o tema.
- Detalhes passam a rolar para uma seção Relacionados. Consulta inicia ao focar a seção ou usar seu botão; leitura não aguarda essa consulta.
- Sugestões da biblioteca e amostra limitada do catálogo da fonte, com motivo explícito. Autores/desenhistas são preservados pelo adaptador Mihon; demais papéis só correspondem quando há valores explícitos. Sem inferência por sinopse.
- Registros antigos continuam legíveis. Identidade de série, créditos normalizados, exclusão da edição atual, deduplicação e prioridade à cópia da biblioteca.
- Consulta limitada a 60 registros locais, até 6 hidratações locais e 16 obras de uma amostra remota; limites de tempo por prévia e etapa remota. Resultados parciais sinalizados.
- Consultas usam a confiança de certificados existente e não alteram progresso. Consulta de relacionados é cancelada e aguardada antes de navegar para leitura/obra.
- Sugestões da biblioteca abrem os detalhes; sugestões remotas permitem escolher a edição, só então adicionada à biblioteca.
- Arquivos locais sem créditos não recebem sugestões artificiais. Resultados dependem da fonte e a amostra não cobre todo o catálogo.

## Verificações

- 178 testes JVM passaram: 94 core, 10 mihon, 74 app.
- Novos testes cobrem créditos/normalização, ausência de inferências, identidade por fonte, exclusão/deduplicação/ordenação/limite de sugestões e persistência de metadados sem mudar identidade da edição.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram. Instrumentação compilada, não executada.
- apksigner e aapt2: assinatura válida, 0.9.3/code27/minSdk26.
- ABI DEX de extensão e consultas SQLite de migração/histórico passaram.
- SHA256: 87A6A04E05F4A3F7338424A1442CB227C097BFF0894728CDF43B20BF0681FD52.

## Conferência pendente na TV

Sem aparelho conectado. Verificar cabeçalho lateral e troca de temas, acesso/retorno aos relacionados pelo D-pad, resumo rolável, sugestões com/sem metadados, conexão indisponível e abertura de edição remota. As respostas reais das extensões e a navegação visual ainda não foram validadas em dispositivo.
