# Validação — Comic TV 0.11.0

24/10/2026 · versionCode 31 · minSdk 26.

## Alterações

- Favoritos e coleções manuais integrados à biblioteca, menu de OK longo e detalhes. Uma edição participa de várias coleções sem duplicar arquivos; seleção múltipla salva em transação somente ao confirmar.
- Criar, renomear e excluir coleções com validação de nomes (1–40 caracteres, unicidade sem caixa/acentos) e confirmação de exclusão preservando quadrinhos/progresso.
- Busca local por título combinada com Todas/Favoritos/coleções. Termo e filtro em SavedStateHandle; contador, estado vazio e limpeza de consulta. Destaque e Continuar lendo permanecem independentes da grade filtrada.
- Room 3 com migração aditiva 2→3 e cadeia 1→2→3, favorito padrão falso, vínculos com FK/índices e exclusão em cascata limitada aos vínculos.
- Configurações → Armazenamento: espaço livre, originais, capas e caches conhecidos. Limpeza confirmada somente de páginas/miniaturas temporárias, serializada com materialização, com invalidação de memória de imagens e resultado parcial quando necessário.
- Travessia recusa links/redirecionamentos no alvo e descendentes; permite aliases confiáveis dos ancestrais do caminho privado Android. Não apaga originais, capas permanentes, banco, progresso ou registros online.
- Revisão independente não identificou P1/P2; apontou restauração de foco ao fechar diálogos. Corrigido retorno ao botão Gerenciar e às ações Renomear/Excluir sobreviventes; após exclusão efetiva, destino Criar é usado.
- Preservados temas, histórico, leitor, cache antecipado e fontes. Backup/downloads offline permanecem para o pacote seguinte.

## Verificação

- 200 casos JVM: 199 aprovados, 1 ignorado, 0 falhas/erros. Core: 108 casos (107 aprovados, 1 ignorado); mihon: 10 aprovados; app: 82 aprovados.
- Caso ignorado: criação de links simbólicos bloqueada pelas permissões do Windows. Teste Android StorageSafetyTest preparado para aliases de ancestral e rejeição de links de alvo/descendente.
- core:test, mihon:testDebugUnitTest, app:testDebugUnitTest, assembleDebug, assembleDebugAndroidTest e lintDebug passaram. Instrumentação Room/Compose/links somente compilada; sem execução em dispositivo.
- Ciclos RED/GREEN registrados para regras de busca/coleções, migração ausente, estado da biblioteca e armazenamento. Testes de arquivos temporários cobrem categorias, preservação de originais/capas, cache ausente, exclusão seletiva e falha parcial. Estado de armazenamento cobre serialização de comandos e tentativa após erro.
- SQL real: migrações 1→3 e 2→3 preservam progresso/histórico; unicidade, FK, rollback e cascade dos vínculos passaram. Script de histórico existente também passou.
- ABI DEX GeneratedSerializer passou. Assinatura apksigner verificada. aapt2: versão 0.11.0/code31/minSdk26.
- SHA256 do APK: 13BA5D8B105A06915C5AAE6A7FBC0C3EA753A3D0B53295308B8EB16503150692.

## Conferência na TV

ADB sem dispositivo conectado. Foco/IME e aparência não validados fisicamente. Conferir: abrir busca explicitamente e voltar; acentos/nenhum resultado; filtro Favoritos; selecionar múltiplas coleções e cancelar/salvar; criar/renomear/excluir com nomes longos; retornar ao botão de origem; favorito removido do filtro ativo; texto 130% e temas; limpeza cancelada/confirmada e reabertura de páginas/miniaturas. Conferir Room migrando uma instalação com dados reais em vez de reinstalar apagando os dados.

Os valores de armazenamento representam diretórios conhecidos, não todo o aplicativo. Bytes removidos referem-se aos arquivos; espaço livre pode variar com o Android. Cache descartável não equivale a download offline. Desempenho e segurança de links no Android ainda dependem dos testes nativos no aparelho.
