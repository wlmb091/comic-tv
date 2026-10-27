# Vidro fosco e fundo escuro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Atender ao recorte marcado pelo usuário: barra lateral e cartões Continuar lendo em vidro fosco, fundo atual mais escuro e cantos discretos.
**Architecture:** Compose nativo/API26, sem dependências. Superfície compartilhada com transparência/degradê/borda sobre fundo já desfocado; alto contraste permanece opaco. Animações curtas de opacidade da barra e escala dos ícones/foco dos recentes com animação padrão Compose, respeitando escala de animação do sistema; sem loop ou alteração de foco/posição/conteúdo.
**Tech Stack:** Kotlin, Compose TV e animation-core existentes.
**Spec:** Imagem marcada e instruções explícitas do usuário em27/10/2026: somente barras destacadas, fundo mais escuro, animações compatíveis e cantos pouco arredondados. Isso substitui a geração da segunda prévia; não alterar a home para copiar títulos/capas fictícios.

## Global Constraints

Preservar presets, IDs/backup, texto85–130%, alto contraste, rotas/restauração de foco, capa original e USB. Escurecer identidade atual sem novo fundo figurativo. Não afirmar desfoque real de conteúdo vivo, FPS ou teste físico. Superfícies atingidas com8dp.

## Review Focus

Transparência não afeta legibilidade; alto contraste opaco. Movimento não altera tamanho/posição de alvos D-pad. Nenhuma animação infinita. Escala do sistema respeitada pelo runtime Compose. Imagens originais e grade sem mudanças de conteúdo.

- [x] Criar GlassPanel compartilhado e aplicar à lateral/recentes; cantos8dp, também nos containers da coleção.
- [x] Escurecer camada da identidade e introduzir transições limitadas a opacidade/escala.
- [x] Conferir build/testes/lint e AndroidTest compile existentes;343casos/1ignorado/0falhas, lint0erros. Sem aparelho, não alegar execução física.
- [ ] Revisão independente final, documentação/versão1.1.21/code60 e publicação autorizada; verificar APK/manifesto públicos.
