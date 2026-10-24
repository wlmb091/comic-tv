# Validação — Comic TV 0.8.0

23/10/2026 · versionCode 20 · Android 8/API 26 ou superior.

## Alterações

- Detalhes em três regiões: capa, informações/ações e resumo separado à direita. Resumo com rolagem e expansão independentes.
- Remover/restaurar do histórico nos detalhes e em menu sobre capas e leituras recentes. Clique curto abre detalhes; segurar OK por 1 segundo abre opções. Menu inclui continuar/começar leitura direta, detalhes, remover/restaurar e fechar.
- Remoção oculta somente histórico e continuar lendo. Coleção, arquivos, página salva, data de leitura e preferências permanecem. Ler novamente reativa o histórico.
- Banco Room v2 com migração aditiva 1→2. Coluna hiddenFromHistory com padrão 0; nenhuma recriação destrutiva.
- Configurações separadas em Acessibilidade e aparência / Controle remoto. Presets preto e amarelo, verde, azul ou violeta; textos 100%, 115%, 130%; alto contraste; restaurar padrão.
- Preferências de aparência persistidas no aparelho e publicadas após gravação confirmada. Alto contraste usa painéis pretos, texto secundário branco e foco reforçado nos botões.
- Cor padrão clara do texto definida na raiz da interface. Cores dos quadrinhos preservadas. Seletor de páginas reserva espaço adicional ao ampliar textos.

## Verificação

- 156 testes JVM passaram, sem erros/falhas: 87 core, 10 mihon, 59 app.
- Três novos testes cobrem valores salvos inválidos, opções por nome estável e contraste dos quatro destaques contra preto (mínimo 7:1).
- Script tools/check-history-migration.mjs passou em SQLite local, executando a migração e SQL reais do DAO: dados preservados, remoção reversível e retomada reativa histórico com progresso limitado à edição.
- Dois testes Android adicionados cobrem migração Room de um banco v1 e remoção/restauração/retomada. APK de instrumentação compilou; testes em dispositivo não foram executados.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Assinatura APK apksigner e metadados aapt2 verificados: 0.8.0/code20/minSdk26.
- Verificador ABI do APK DEX passou para o método padrão executável de GeneratedSerializer.
- SHA256: 3DDE586C56CE396E8606CA71E8B8AF23CB6645FC1F9B3074810FEC0BAB741AE1.

## Conferência no aparelho

adb não encontrou dispositivos conectados. Conferir resumo independente, menu por OK longo sem abrir leitura acidentalmente, remoção/restauração nas duas telas, atualização sobre versão anterior, retomada da página e preferências após reiniciar. Conferir textos 130% e foco nos presets. As opções de contraste não constituem uma auditoria completa de acessibilidade.
