# Validação manual na Box do usuário — 24/10/2026

Relatos de uso das versões 0.12.0 a 0.12.5, conforme os fluxos e a confirmação final abaixo. Aparelho identificado na tela Sobre como MiTV-AFKR0, Android TV 11. Resultados abaixo foram relatados pelo usuário nesta conversa, não observados por instrumentação/ADB.

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

Testes Compose do diálogo foram preparados e compilados; não executados no aparelho. Após a orientação para repetir a restauração na 0.12.1 e conferir a confirmação visível, o usuário respondeu “tudo certo, pode prosseguir com o proximo passo”. Etapa aceita conforme relato; sem observação direta ou captura das contagens.

## Rodadas seguintes relatadas pelo usuário

Após o roteiro de favoritar, criar coleção Teste, adicionar edição, fechar/reabrir e remover favorito/coleção preservando edição/progresso, o usuário respondeu “tudo certo”. Após o roteiro de pausa ao sair, reabertura, retomada e remoção do download preservando edição/progresso, respondeu “tudo funcionando”. Fluxos aceitos conforme relato, sem observação direta ou instrumentação.

Ainda pendentes: foco em todos os temas/texto ampliado, seleção SAF e simulação isolada de falhas/interrupção de restauração. O acabamento visual 0.12.2 requer conferência na Box. Não executar falhas destrutivas nos dados reais nem declarar toda a rodada nativa concluída com base nestes relatos.

## Aceitação final relatada — promoção a1.0.0

Após a entrega0.12.5 e o roteiro de conferir capítulos nos detalhes/retorno, texto85–130% com persistência, grade de temas e ícones dos modos, o usuário confirmou: “ja foram confirmados,até entao tudo funcionando”. Em seguida aceitou corrigir eventuais problemas nas próximas atualizações. Os últimos ajustes e o uso na Box ficam aprovados conforme esse relato, sem captura individual de cada combinação ou observação por ADB.

Essa confirmação substitui a pendência de aceitação visual acima para o aparelho do usuário. SAF e simulações nativas isoladas de interrupção/falha não foram explicitamente relatados; a suíte instrumentada continua compilada, sem execução nesta máquina. Não executar testes destrutivos nos dados reais.
