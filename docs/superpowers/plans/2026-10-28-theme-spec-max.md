# ThemeSpec e temaMax Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Centralizar variantes sem mudança visual inicial e implementarMax conforme o pacote fornecido.
**Architecture:** ThemeSpec imutável por preset, LocalThemeSpec no tema eThemedScaffold reaproveitando o shell/foco existente. Componentes consomem variantes/dimensões e não IDs de tema.
**Tech Stack:** Kotlin/Compose AndroidTV/API26,Coil,Room existentes.
**Spec:** docs/design/frontend-package/THEMES_SPEC.md, README.md eDISCOVERY.md; decisões posteriores deBuscar e marca prevalecem.

## Global Constraints

IDs persistidos, dados/USB/originais/leitor inalterados; cinco temas, marcaMontserrat; fundosneutros#0F0F0F;BuscarPrime superior/dockMax7itens/Cinema marca expandida. Sem assets fictícios, dependência pesada, blurporquadro ou foco roubado. Texto85–130%/HC opaco. Não afirmar matches sem capturas.

## Review Focus

Troca de tema emruntime deve atualizarLocalThemeSpec; restauraçãolegacyAmber preservada. Foco/Back/busca em todasrotas e130% preservados. Primecarrossel aprovado inalterado. Maxgrade/filtros/histórico nunca inacessíveis ao alterar faixa principal. Capturas exigemruntime real, não substituir por compilações.

## Tarefas

- [x] Task1: ThemeSpec/variantes/LocalThemeSpec eThemedScaffold; migrar consumidores de posicionamento/hero/cards sem alterar medidas/cor. Testar matriz de cinco temas/legacy e recompilar.
- [x] Task2: Max: fonte condensadaOFL, título inferior, Recentes4cartões compactos e dock7itens com mínimo14sp; preservar acesso àgrade/rodapé eimagens proporcionais. Testes de geometria/foco.
- [ ] Task3: Diagnóstico deDisplay/density/configuração e preparar capturas noemuladorTV, se disponível. Sem emulador instalado atualmente: registrar limite e não alegar execução.
- [ ] Task4: Revisar, construir/testar/lint e produzir relatórioMax com diferenças reais; não avançar aos outros quatro temas semconferência deste.

Autorização atual: usuário disse“pode prosseguir” após análise dopacote. Esta fase não publica automaticamente uma alegação de fidelidade: relatório distinguirá implementação, testes compilados e execução visual.

Verificação local375casos/0falhas,lint0erros/75avisos,build3m03. Capturas nativas não executadas;emulador softwareoffline semhipervisor. Usuário escolheu conferênciaBox;publicar1.2.4/code66 assinada,registrarpartial eaguardar relatoMax antes deetapasvisuais seguintes. Hubadiado;monetizaçãofutura.
