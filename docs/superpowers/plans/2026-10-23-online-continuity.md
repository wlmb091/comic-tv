# Continuidade online Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development, separate repository/model task and UI integration, then independent review. Root coordinates Gradle only.

**Goal:** Facilitar a passagem entre edições de uma saga/série e a retomada online, seguindo a prioridade confirmada pelo usuário, mantendo cadastro de repositórios já entregue.

**Architecture:** A partir das ferramentas do leitor online, salvar/sair e abrir a lista atualizada de edições desse mesmo título. Tela própria destaca edição atual, oferece anterior/próxima, sequência invertível e seleção pelo D-pad; a edição escolhida usa o mesmo OnlineReaderRepository e conserva seu progresso. Reabrir pela biblioteca mantém acesso ao título via registro persistido.

**Decisões:** Não inferir cronologia nem ordenar títulos lexicograficamente; seguir lista da fonte por padrão, com ação explícita inverter sequência. Não trocar edição automaticamente durante slides. Carregar lista fora do leitor ativo evita invalidar contexto Page das extensões. Portal web fica em preparação futura, sem hospedagem ou dependência para leitura pelas fontes.

- [x] Repositório: contexto de título/capítulo a partir do registro persistido, consulta atualizada de capítulos com pin/certificado, abertura de edição selecionada e testes de sequência/lacunas/duplicatas. Sem escrita de progresso na navegação.
- [x] UI: tela online-editions com foco/rolagem, atual/anterior/próxima/inverter, abrir edição preservando progresso, erros/retry; atalho no leitor online. Salvar leitor antes de sair para consultar fonte.
- [x] Revisão independente, testes/build/lint/instrumentação compilada, versão0.7.0/code9, docs/APK/código e GitHub. Validar fontes reais no dispositivo continua pendente.
