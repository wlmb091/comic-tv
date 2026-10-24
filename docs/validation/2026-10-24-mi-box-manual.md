# Validação manual na Box do usuário — 24/10/2026

Aplicativo testado: Comic TV 0.12.0. Aparelho identificado na tela Sobre como MiTV-AFKR0, Android TV 11. Resultados abaixo foram relatados pelo usuário nesta conversa, não observados por instrumentação/ADB.

## Condição do aparelho

O USB é usado como armazenamento expandido. Mantê-lo conectado e preservar a configuração em que funciona; não exigir remoção nem alternância de depuração para esta validação. A tentativa de conexão ADB pela rede foi recusada; a rodada passou a ser manual pelo controle remoto.

## Resultados relatados

| Fluxo | Evidência e resultado |
|---|---|
| Preparação do backup | Foto da tela exibiu “Backup preparado”; passou. |
| Exportação pela rede local | Usuário confirmou download do arquivo no celular; passou. |
| Recebimento e prévia | Foto mostrou “Restaurar backup?” com 6 edições, 0 coleções e 0 favoritos; passou. |
| Confirmação da restauração | Usuário informou que confirmou, sem mensagem visível, e que o aplicativo continuou normal. Preservação relatada; conclusão/contagens da operação não foram confirmadas visualmente. |
| Download completo | Usuário relatou funcionamento. |
| Abertura offline | Usuário relatou abertura normal seguindo a sequência solicitada com Wi-Fi desligado e USB mantido. |
| Limpeza dos dois caches e reabertura offline | Usuário confirmou explicitamente ter limpado ambos e a edição continuou abrindo; passou conforme relato. |

## Ajuste decorrente — 0.12.1

O resultado da restauração ficava num item da LazyColumn de configurações, sujeito a ficar fora da área visível quando a sessão QR estava em foco. A atualização torna sucesso/erro um diálogo explícito, com botão Entendi focado. Sucesso só é exibido após retorno do repositório; erro não informa conclusão falsa. O texto inline continua disponível após fechar o diálogo.

Testes Compose do diálogo foram preparados e compilados; não executados no aparelho. O próximo teste manual é repetir a restauração do mesmo arquivo na 0.12.1 e conferir a confirmação visível.

Ainda pendentes: foco em todos os temas/texto ampliado, coleções/favoritos em filtros ativos, pausa/retomada de download após reinício, remoção com progresso preservado, seleção SAF e simulação isolada de falhas/interrupção de restauração. Não executar falhas destrutivas nos dados reais nem declarar toda a rodada nativa concluída com base nestes relatos.
