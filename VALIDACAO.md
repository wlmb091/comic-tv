# Validação — Comic TV 0.9.4

24/10/2026 · versionCode 28 · minSdk26.

- COMIC TV centralizado no topo do menu lateral expandido; texto da biblioteca mantém o alinhamento anterior. Fonte e cores preservadas.
- Atalho e seção Relacionados ocultos. Guarda de execução impede consultas enquanto showRelated permanece false (padrão usado pelo aplicativo). Código preservado para retomada futura.
- Nenhuma IA foi adicionada; implementação de relacionados existente compara metadados explícitos.
- assembleDebug e lintDebug passaram. Mudança visual/de visibilidade pequena; testes JVM não foram repetidos nesta versão.
- apksigner e aapt2 verificaram assinatura, versão 0.9.4/code28 e minSdk26.
- SHA256: 081BD2C99838FFE0301156BF9AABA8FE62BD94E9C490E417BB18E35294BAC4A6.
- Sem TV conectada: conferir centralização, cores por tema e ausência de Relacionados/consultas na navegação da ficha.
