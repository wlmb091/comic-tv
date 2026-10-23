# Fontes Mihon Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development with independent tasks, task reviews and a final integration review.

**Goal:** Adicionar múltiplos repositórios, instalar extensões pelo Android, navegar catálogos/títulos/capítulos e ler online no leitor compartilhado.

**Architecture:** Núcleo JVM lê descritores e índices legados/v2 (JSON/protobuf/gzip). Módulo Android mihon hospeda as APIs exigidas pelos APKs, baseado no módulo Apache-2.0 da tag OtakuWorld nightly_2026_10_16, preservando pacotes e atribuições. App gerencia repositórios e pins, download e instalação Android, fonte ativa e navegação. ReaderRepository online compartilha progresso Room e o leitor atual, com downloads de página pelo cliente da fonte.

**Spec:** Requisitos já aprovados na conversa: vários repositórios Mihon, extensões instaláveis, HQ Now como uma fonte possível, catálogo/sagas fornecidos pela fonte e leitura online adaptada à TV. Não copiar design Hikari nem prometer compatibilidade universal.

## Global Constraints

minSdk 23; Android36; não instalar APKs no PC. Usuário instala/confirma no Android pelo fluxo nativo. Validar pacote, versão e certificado do APK contra entrada/pin; não executar fonte sem confiança no certificado. Repositórios HTTPS, tamanho e timeout limitados, formatos v2 atuais e legados. Índices não podem substituir silenciosamente a chave pinada. Limite de página32MiB, cache256MiB, miniaturas32MiB, dimensões100MP preservados. Progresso só ao abrir/ler, não ao navegar capa/lista. Usar o cliente/getImage da extensão para cabeçalhos/cookies. Falhas por fonte isoladas e mostradas ao usuário.

## Tasks

- [x] Repo core: normalização de URL, parser legado e v2/protobuf/gzip, limites e testes; validar índice real Keiyoushi e um segundo índice disponível.
- [x] Runtime mihon Android: APIs vendorizadas, cliente/cookies, carregamento por pacote/certificado, catálogo/detalhes/páginas/download e testes; licença e minSdk.
- [x] App de fontes: armazenamento de repositórios/pins, extensões e fluxo de instalação nativo, fonte/catálogo/busca/detalhes/capítulos; foco D-pad.
- [x] Leitor online: registro/progresso Room, caches limitados, retomada na biblioteca e downloads pela fonte. Saga retornada como título/capítulos mantém sequência da fonte.
- [x] Integração, testes, build/lint e revisão independente. Registrar validações possíveis sem TV e limitações por extensão. Documentar e entregar APK/código; atualizar GitHub.

Contratos comuns estão em core/sources/SourceModels.kt. Root coordena builds Gradle: não executar builds concorrentes. Arquivos/ownership separados por módulo/pacote para trabalho independente.
