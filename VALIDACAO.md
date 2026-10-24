# Validação — Comic TV 0.12.2

24/10/2026 · versionCode 34 · minSdk 26 · base 0.12.1.

## Escopo

Acabamento da biblioteca sobre a referência aprovada: margens, separação do cabeçalho, padding do destaque, progresso da edição em leitura, ações em FlowRow e cartões recentes com capa inteira maior/título de duas linhas. Mantém temas, foco/restauração, chaves da grade, filtros e fundo da identidade Comic TV. Sem dependências ou migração de dados.

## Verificação

Compilação debug, compilação AndroidTest e lint passaram em work/home-polish-0122-final.log. Os 86 testes JVM do aplicativo passaram, sem falhas/erros/ignorados. Assinatura APK verificada e certificado igual ao da 0.12.1; metadados confirmam versão 0.12.2/code34/minSdk26. Testes nativos são apenas compilados; sem conexão ADB ou captura renderizada nesta rodada. Revisão independente do diff não encontrou bloqueios.

## Aceitação física

Conferir na Box os títulos longos, texto ampliado, quebra das ações, rolagem até Continuar lendo e foco nos temas. Essa conferência permanece pendente. Fluxos anteriores aprovados pelo relato do usuário estão em docs/validation/2026-10-24-mi-box-manual.md. Manter USB conectado e instalar como atualização sem desinstalar.
