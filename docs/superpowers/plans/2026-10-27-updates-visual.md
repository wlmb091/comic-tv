# Atualizações visuais 1.1.20 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Organizar Atualizações com resumo, estado claro, progresso MiB e foco estável.
**Architecture:** Preservar AppUpdateViewModel/protocolo. Separar conteúdo Compose do coletor para testar transições de apresentação; ações continuam nos callbacks atuais. Resumo e ações fixos, novidades roláveis abaixo, sem duas colunas obrigatórias.
**Tech Stack:** Kotlin, Compose Android TV, JUnit/Compose AndroidTest existentes.
**Spec:** docs/superpowers/specs/2026-10-27-updates-visual-proposal.md, aprovada pelo usuário em 27/10/2026.

## Global Constraints

Texto 85–130%, alto contraste, temas e D-pad preservados. Sem alterações de download, assinatura, consulta por sessão, dados, USB, home ou leitor. Versão 1.1.20/code59.

## Review Focus

- Texto ampliado e notas longas: ações acessíveis, painel rolável.
- Transições ocupadas: foco permanece no principal; secundário focado transfere foco antes de desaparecer.
- Checking/Verifying/Installing: não inventar progresso ou confirmação de instalação.
- Dados incompletos: nenhuma divisão por zero, porcentagem fora de 0–100 ou valores negativos.
- Falha/cancelamento: mensagens e ações existentes mantidas.

## Task 1: apresentação e foco

- [x] Acrescentar casos de transição e navegação ao AppUpdateSettingsTest; compilados, sem aparelho para executar.
- [x] Implementar resumo tematizado, estado real por fase, MiB, ação principal estável e secundária condicional com handoff de foco.
- [x] Conferir painel rolável, alto contraste e escala130% em revisão/casos compilados; testes JVM existentes, AndroidTest compile, build release/lint e scripts do pipeline passaram.

## Task 2: entrega

- [x] Registrar versão, release e validação, incluindo relato manual da1.1.18 e pendência física da atualização.
- [x] Revisão final independente; erro longo movido ao painel rolável e verificação final passou.
- [x] Enviar ao main autorizado e acompanhar CI/assinatura/publicação; manifesto e APK públicos da1.1.20 conferidos, incluindo assinatura histórica e compatibilidade. Resultado físico continua manual.

## Execução

Base local27bef57; manutenção1.1.19 já enviada ao main494e392. CLI gh sem login; usar connector GitHub e API pública para verificações disponíveis. Sincronizar Git local preservando arquivos de trabalho. Aprovação de publicação já persistente em AGENTS.md e reforçada nesta tarefa.
