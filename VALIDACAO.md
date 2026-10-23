# Validação — Comic TV 0.7.6

23/10/2026 · versionCode15 · Android8/API26 ou superior.

## Alterações solicitadas

1. Lateral flutuante de64dp recolhida e228dp expandida com foco/ponteiro; conteúdo mantém largura e a expansão sobrepõe a borda esquerda. Ícones alinhados em caixas fixas e seleção visual distinta do foco. Desenhos de biblioteca/configurações ajustados.
2. Biblioteca em uma grade rolável, incluindo cabeçalhos e recentes. Espaçamentos menores, capas completas em2:3 e títulos/progresso sobre gradiente, removendo altura adicional abaixo das capas.
3. Removido take(2) das leituras recentes. Faixa horizontal contém todas as leituras registradas e Histórico mostra todas em grade, ordenadas pela última leitura, incluindo concluídas. Sem alteração/exclusão dos registros existentes.
4. Fontes abre o catálogo da seleção salva; Trocar fonte abre grade compacta pesquisável e Gerenciar separa repositórios/extensões. Formulário e detalhes técnicos ficam recolhidos. A seleção usa pacote+ID e só é restaurada dentre fontes carregadas e verificadas. Fonte removida/indisponível gera alternativa explícita, sem selecionar outro pacote por coincidência de nome/ID.

Durante atualização anterior à existência dessa preferência, escolher HQ Now uma vez grava a seleção para próximas entradas/inicializações. Gerenciamento aberto explicitamente durante restauração permanece aberto. Atualizar instaladas renova handles sem perder a seleção.

## Verificação

-139 testes JVM passaram sem falhas/erros (87core,10mihon,42app).
-3 novos testes verificam identidade exata de seleção, isolamento entre pacotes e retorno seguro para fonte removida/falha.
-assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
-Verificador do DEX confirmou implementação padrão de serialização preservada.
-apksigner verificou assinatura; aapt2 confirmou0.7.6/code15/minSdk26.
-SHA256 APK:6073ECA75B88D5DCE40C84B3D975D028C0140E25CED1E464291E0385752F1D09.
-Revisão independente de biblioteca/lateral/seleção identificou uma corrida na restauração; corrigida com nova conferência após persistência e confirmada pelo revisor.

## Teste no aparelho pendente

Sem execução em TV/emulador nesta etapa. Conferir: lateral expande/recolhe sem deslocar catálogo; histórico com três ou mais leituras; rolagem de cabeçalho e capas; sair/reentrar em Fontes mantém HQ Now; selecionar outra fonte troca catálogo; gerenciamento e Voltar preservam seleção; fonte indisponível permite escolher outra. Controles do leitor permanecem como na0.7.5.
