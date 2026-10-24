# Validação — Comic TV 0.10.0

24/10/2026 · versionCode 30 · minSdk 26.

## Alterações

- Zoom por regiões do arquivo original em Normal/Galeria/Livro, blocos decodificados fora da UI, visão geral inicial e cache LRU de 8–24 MiB conforme a classe de memória.
- Filtros opcionais Original, Texto e traços, HQ antiga; intensidade suave/média/forte, comparação com o original e escolha salva por edição. Alfa preservado e arquivos inalterados.
- Após a página atual ficar pronta, antecipação das próximas duas páginas e aquecimento de miniaturas próximas. Linha do tempo reutiliza arquivos conhecidos e cache Coil, com cinco posições visíveis e duas adicionais antecipadas na direção da navegação.
- Extração/download reutilizados entre miniaturas e leitura. Cache de páginas de 256 MiB, miniaturas em disco de 32 MiB e proteção da página visível preservados. Antecipação não grava progresso.
- Saída cancela e aguarda também trabalhos antigos substituídos; fechar a linha do tempo na mesma página retoma a antecipação interrompida. Revisão independente identificou esse último caso, reproduzido com teste falhando antes da correção.
- Escopo aprovado: melhorar zoom, filtros e cache. Detecção de painéis, Lanczos próprio e shader personalizado ficam fora desta etapa. Detalhes em docs/reader-image-quality.md.

## Verificação

- 191 testes JVM sem falhas/erros: 103 core, 10 mihon, 78 app.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram. Testes instrumentados somente compilados; incluem decodificação regional conservando pixel original, reaproveitamento do bitmap, filtro e integridade do arquivo.
- Testes de regressão comprovam antecipação de duas páginas/miniaturas, retomada após linha do tempo, espera pelas cargas canceladas e preferências de filtro por edição. Geometria, amostragem, limites de nitidez/alfa e LRU cobertos em core.
- Assinatura apksigner verificada. aapt2: versão 0.10.0/code30/minSdk26.
- ABI DEX de GeneratedSerializer e SQL real da migração/histórico passaram nos scripts do projeto.
- SHA256 do APK: C5A395928D7DF75EF01C3F946021F0B4CC5B9D4C81EDF5123223E9BE739CB722.

## Conferência na TV

ADB sem dispositivo conectado. Compatibilidade técnica projetada para Android 8+ e memória limitada, incluindo a Xiaomi TV Box S de 2ª geração; desempenho real não medido. APIs usadas não exigem Android 13. Sem emulador disponível, testes instrumentados não executados.

Conferir zoom de 100% a 300%, mover, Galeria/Livro/RTL, HQ antiga com texto apagado, intensidades e comparação, abrir/fechar a linha do tempo rapidamente, avançar após antecipação e sair durante cargas da fonte. Testar também PDF e EPUB de imagens/texto. Primeira abertura e navegação rápida ainda podem mostrar carregamento; filtros não recuperam detalhes ausentes da digitalização. Formatos sem suporte a recorte nativo usam alternativa de resolução limitada.
