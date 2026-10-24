# Validação — Comic TV 0.12.1

24/10/2026 · versionCode33 · minSdk26 · base0.12.0.

## Correção

Resultado da restauração passa de mensagem inline potencialmente fora da tela a diálogo explícito. Sucesso contém contagens e botão Entendi focado, emitido somente após retorno do repositório. Erros mostram Restauração não concluída e mantêm a prévia para nova tentativa. A sessão local não aceita outra prévia enquanto esse resultado aguarda reconhecimento. Não altera formato do backup, banco, progresso ou arquivos offline.

## Verificação automatizada

- 213 casos JVM:212 aprovados,1 ignorado (Windows sem permissão de symlink),0 falhas/erros. Core117, mihon10, app86.
- core:test, mihon:testDebugUnitTest, app:testDebugUnitTest, assembleDebug, assembleDebugAndroidTest e lintDebug passaram em work/restore-feedback-green.log.
- Dois testes Compose novos para resultado visível/foco/fechamento e erro sem sucesso falso foram compilados. Não executados no Android por falta de ADB.
- Revisão independente não identificou bloqueios na mudança de feedback. Foco do erro sobre a prévia e ciclo de vida continuam sujeitos à conferência física.
- Assinatura apksigner, versão aapt2 (0.12.1/code33), ABI DEX e scripts SQL de histórico/migrações1→3/2→3 passaram.
- SHA256 APK:7F8F9C2F70DC55BE5E46A3D6707FDFEC180CD9D5B7545E394678B1E0397E6052.

## Validação manual recebida

Na0.12.0, o usuário confirmou preparo/exportação do backup no celular, prévia com6 edições, biblioteca normal após acionar confirmação, download e abertura offline depois de limpar ambos os caches. O aviso de conclusão da restauração não ficou visível; as contagens/conclusão efetiva ainda não foram observadas. Evidências e limites:docs/validation/2026-10-24-mi-box-manual.md.

A Box usa Android TV11 e USB como armazenamento expandido. USB mantido conectado; ADB recusado, sem testes instrumentados executados. Esses resultados são relatos manuais, sem medição de rede/memória/desempenho.

## Próxima conferência

Atualizar para0.12.1 sem desinstalar nem retirar o USB; repetir restauração do mesmo backup e conferir o diálogo Restauração concluída/contagens/Entendi. Depois verificar favoritos/coleções, retorno de foco, texto ampliado/temas e pausa/retomada de downloads. Não anunciar1.0 estável antes de encerrar os fluxos pendentes.
