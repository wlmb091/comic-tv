# Leitor: manter zoom ao virar página — 30/10/2026

Implementado depois de concluir otimização de carregamento, como solicitado pelo usuário. Pacote continua aberto,semAPK/versionamento/push/CI.

## Comportamento

Trocar página na leitura conserva a porcentagem dezoom da edição em vez de voltar a100%. Sezoom>100%, a nova página começa no topo (panY=-1 na geometria lógica), com horizontal centralizada. A mesma regra atende0°/90°,LTR/RTL,modoNormal/Galeria e as viradas em pares doLivro. Voltar uma página também usa essa regra. Tentar avançar fora do intervalo conserva a página e a posição atuais. Em100% mantém enquadramento normal.

Comandos dezoom/pan,giro do controle,escolha explícita da linha do tempo,abertura de nova edição,narração e configuração defit não foram redesenhados. Nenhuma nova preferência global/backupnem alteração dosoriginais.

## Alteração e testes

`ReaderState.turnPage` centraliza regra; reducerusa na virada eReaderViewModel usa também nas viradas em pares doLivro.

- Teste antigo ajustado à nova regra e regressões executadas primeiro:2falhas esperadas antes da implementação (`build/reader-zoom-red.log`).
- Core: preservazoom,topocorreto em1280x720 e720x1280,RTL/LTR,limites e100%. Linha do tempo ePan mantêm testes existentes.
- ReaderViewModel: novo casoLivro mantém200% apóspar/retorno e começa notopo.
- BUILD SUCCESSFUL2m10 (`build/reader-zoom-final.log`),testsReaderapp41/0falhas,AndroidTestKotlin compilado.
- Checagemfinalcore24testes/0falhas (`build/reader-final-core.log`,34s), incluindoasnovasproteções derenderização.
- Lint da etapa decarregamento:0erros/70avisos; nenhuma novaAPIAndroid nesta regra dezoom. `git diff --check` limpo.

## Conferência física

Ainda pendente naBox apóspróximoAPK autorizado:200%/150%,moveratéembaixo,voltaraomodo deleitura evirar; nova páginadeve manterporcentagem eabrirnotopo;repetir90°,RTL,Livro,primeira/últimapágina. Testes egeometria não comprovam interação física noaparelho.
