# Zoom, filtros e antecipação — primeira etapa aprovada

Revisão de escopo do usuário: preservar detalhes ao ampliar, melhorar digitalizações antigas e evitar espera nas miniaturas. Detecção de painéis individuais fica para depois. Sem IA e sem exigência de Android 13.

1. Testar aquecimento antecipado de duas páginas e miniaturas antes da linha do tempo; manter progresso isolado e cancelamento/join antes de sair.
2. Implementar decodificação por regiões do arquivo original com cache de blocos limitado e geometria testada, usada nos modos Normal, Galeria e Livro. Trabalho fora da UI; alternativa segura para formatos sem region decoder.
3. Filtros Original, Texto e traços, HQ antiga; intensidade suave/média/forte, comparação e preferências por edição sem alterar arquivos. Testar alfa, imutabilidade, contraste e limites.
4. Miniaturas em disco e memória/Coil aquecidas; reutilizar a janela visível e antecipar duas posições além dela. Cache de leitura distinto do cache de miniaturas e proteção da página atual.
5. Verificar JVM, APK, instrumentação compilada, lint, assinatura e ABI. Compatibilidade por APIs Android 8+ e memória limitada; teste real na Xiaomi TV Box S 2nd Gen ainda obrigatório.

Implementação e limites detalhados em `docs/reader-image-quality.md`. Itens 1–4 implementados. Revisão independente identificou interrupção definitiva da antecipação ao sair da linha do tempo na mesma página; teste de regressão confirmou a falha e foi incluída retomada. Resultado final dos checks do item 5 em `VALIDACAO.md`; execução na TV continua pendente.
