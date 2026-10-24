# Validação — Comic TV 0.12.5

24/10/2026 · versionCode37 · minSdk26 · base0.12.4.

## Alterações finais

- Ícones distintos por modo Normal/Galeria/Livro no leitor.
- Temas em grade adaptativa lado a lado.
- Controle horizontal D-pad85–130%, passo5%, faixa acessível e saída por Acima/Abaixo. Persistência em sequência sem descartar mudanças enquanto salva.
- Compatibilidade com preferências/backups100/115/130. Novo campo opcional textPercent validado por tipo/faixa/passo; novos backups com esse campo exigem aplicativo atualizado.
- Detalhes online recebem Escolher capítulo / edição. A lista usa a fonte existente e retorna à origem; detalhes usam capa em vez de prévia antiga. Consultar lista não abre páginas nem muda progresso; selecionar capítulo abre o leitor existente. Arquivos locais não recebem sequência inventada.

## Verificações

- work/photo-corrections-0125-chapters.log: BUILD SUCCESSFUL em1m26s; core:test, mihon:testDebugUnitTest, app:testDebugUnitTest, assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- 215 casos JVM:214 aprovados,1 ignorado (permissão Windows para symlink),0 falhas/erros. Core118, mihon10, app87.
- Testes novos de porcentagem e backup roundtrip/validação passaram. Testes Compose do controle e retorno da lista com falha de fonte compilados, não executados.
- Revisões independentes da aparência/backup e da entrada de capítulos sem bloqueios. Teste nativo não cobre o NavHost completo ou hardware Back em execução.
- Assinatura v2 verificada; mesmo certificado SHA2565f60801ffa010bb913a59857e851d5eed2d4b63bc79de38747f2db15ae0395ae. Metadados0.12.5/code37/minSdk26 confirmados.
- DEX real do APK passou check-extension-abi. SQL de histórico e migrações de organização1→3/2→3 passaram.

## Aceitação física pendente

Conferir ícones, grade de temas, setas mantidas no controle85–130%, persistência após reabrir, foco/rolagem e capítulo escolhido a partir dos detalhes. Testar Voltar visível e do controle retornando aos detalhes, além do acesso antigo pelo leitor. Lista depende da fonte online disponível e confiável. Sem ADB conectado; não afirmar desempenho/aparência final antes da conferência.

Instalar como atualização, sem desinstalar, mantendo o USB de armazenamento conectado.

SHA256 APK: A21C5A0B653FF4B8631BD2EC03EF35C52C0E15AA866C965B0EBDB563D2FC8B9C.
