# Validação — Comic TV 0.12.0

24/10/2026 · versionCode 32 · minSdk26. Base anterior 0.11.0.

## Entregue

- Backup de dados JSON versionado .ctvbackup, sem originais/páginas/credenciais/confiança. Inclui biblioteca/progresso/histórico/favoritos/coleções e preferências de aparência/leitura/filtros. Limites de arquivo, contagens, tipos e referências validados.
- Seletor Android para salvar/abrir e sessão HTTP local com URL/token/QR, upload/download, Host/Origin/tamanho conferidos, expiração e encerramento por ciclo de vida. Envio abre somente prévia; confirmação ocorre na TV.
- Restauração por mesclagem, preflight agregado antes de journal/mutação, transação Room e journal recuperável. Preferências resolvidas são preservadas no replay e gravadas em lote. Originais ausentes ficam pendentes por SHA-256; reimportação confere também os bytes copiados.
- Fila offline persistente, até100 edições e um worker sequencial, pausa/retomada e remoção confirmada. Pausa/aguarda ao sair; não retoma automaticamente ao voltar.
- Downloads permanentes separados do cache, publicação completa atômica, hashes de páginas, lista de fonte conferida na retomada, limites de32MiB/página,100MP,1GiB/edição e64MiB de reserva (96MiB para iniciar página máxima).
- Leitor offline-first: pacote completo verifica integridade e lê sem fonte/extensão/rede; pacote alterado não cai silenciosamente na rede. Miniaturas regeneram pelo arquivo local. Contagem do manifesto atualiza a contagem usada para progresso.
- Selos/ações nos detalhes e biblioteca/menu de OK longo; gerenciador e bytes offline em Armazenamento. Limpeza de cache preserva páginas permanentes e remover download preserva metadados/progresso.

## Testes e revisão

- 213 casos JVM:212 aprovados,1 ignorado,0 falhas/erros. Core117 (116 aprovados,1 ignorado); mihon10; app86.
- O teste ignorado continua sendo criação de links simbólicos no Windows por permissão indisponível; teste nativo Android correspondente foi compilado.
- core:test, mihon:testDebugUnitTest, app:testDebugUnitTest, assembleDebug, assembleDebugAndroidTest e lintDebug passaram em work/backup-offline-0120-batch-final.log.
- Testes JVM novos: roundtrip/tipos/versão/duplicatas/referências/limites/merge de backup; HTTP real em loopback para transferência/expiração/arquivo inválido e retomada após falha ao criar temporário; manifesto parcial, recuperação da mesma lista, integridade/mesmo tamanho alterado, espaço/caminhos e arquivos separados; leitura e miniatura com fonte configurada para falhar; fila persistente, pausa/retomada, saída imediata e duas edições explícitas.
- Revisão independente de backup apontou limites agregados após commit e preferências inconsistentes no replay/pêndencias; corrigidos e confirmados em revisão dirigida. Revisão offline apontou retomada explícita bloqueada e selo incorreto em corrupção do mesmo tamanho; ambos reproduzidos RED, corrigidos GREEN e confirmados em revisão dirigida.
- Preparado teste instrumentado Room/arquivo local para pendência, reimportação SHA, vínculos/favorito, backup posterior e recuperação de falha injetada após commit. Compilado, NÃO executado em Android.
- Assinatura apksigner verificada. aapt2:0.12.0/code32/minSdk26/targetSdk36. Gate ABI DEX GeneratedSerializer passou; SQL real de histórico e migrações1→3/2→3 de coleções passou. Nenhuma alteração de esquema Room além da versão3 já existente.
- SHA256 do APK:F157C96D5334F5BF95F6B28084B14B8ECB8BB8FF865957C86571B3C1C231C0AA.

## Limites de validação

ADB sem dispositivo conectado. Fluxos reais de seletor/QR/HTTP pela rede da TV, Room/restauração interrompida no Android e fonte real baixando edições NÃO foram executados. Foco/IME, desempenho, espaço real e consumo de memória na Xiaomi TV Box precisam da conferência física; testes JVM/fontes falsas não comprovam essas condições.

Na TV, conferir backup exportado e restauração duplicada preservando dados; cancelar prévia; reimportar o mesmo original quando ausente; pendências; receber/enviar pelo navegador e encerrar sessão; download/pausa/retomada após reiniciar; sair rapidamente; leitura com Wi-Fi desligado/extensão indisponível; Galeria/Livro; cache limpo sem perda do download e remoção com progresso mantido. Preservar originais separados do arquivo de backup.

Não há backup automático, nuvem/sincronização, serviço contínuo em segundo plano, download automático de séries, inclusão de originais/páginas no backup nem restauração de confiança/credenciais. URLs transitórias podem alterar o fingerprint e reiniciar parciais com aviso. Guia:docs/backup-offline.md.
