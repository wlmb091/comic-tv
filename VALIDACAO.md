# Validação — Comic TV 0.8.2

23/10/2026 · versionCode 22 · Android 8/API 26 ou superior.

## Correções

- Vídeo do usuário analisado localmente. A lateral mostrava o foco em Biblioteca enquanto exibia Novidades/Catálogo. A marcação ativa era derivada do resultado remoto (latest) e faltava um destino explícito para foco na nova tela.
- Item ativo passa a seguir a rota. Navegação pela lateral solicita foco uma vez no item acionado; conclusão da consulta não redefine foco/seleção. Retornos de detalhes/leitor não solicitam esse foco, preservando a restauração do conteúdo.
- Resumo travava porque a rolagem exigia isFocused no painel, ignorando foco no botão interno. Modificador compartilhado usa hasFocus e consome ↑/↓ enquanto houver texto a rolar. Aplicado aos detalhes da biblioteca e da fonte.
- Remover do histórico agora abre confirmação nos detalhes e no menu das capas. Aviso sobre manter quadrinho e página salva foi movido para o alerta. Cancelar recebe o foco inicial; somente Remover confirma a gravação. Restaurar continua direto.

## Verificação

- 162 testes JVM sem erros/falhas: 87 core, 10 mihon, 65 app.
- Três testes Compose adicionados para foco por seção com atualização de conteúdo, rolagem do resumo a partir do botão e cancelamento/confirmação de remoção. Compilados no APK de instrumentação; não executados em dispositivo.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Consultas reais de migração/histórico passaram em SQLite local.
- Assinatura apksigner e metadados aapt2 verificados: 0.8.2/code22/minSdk26.
- ABI do APK DEX validada para GeneratedSerializer.
- SHA256: 19B05F9C4670E734ECB1D9CD439E18A06AE5C7F26A2AB702C8D9D12A3A01B0BF.

## Conferência na TV

adb não encontrou aparelho conectado. Validar: abrir Novidades/Catálogo repetidamente e conferir foco; abrir resumo, expandir e rolar com ↑/↓; cancelar e confirmar remoção nos dois pontos; retornar do leitor e conferir restauração do conteúdo.
