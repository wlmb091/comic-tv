# Miniaturas e leitura automática Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline and independent final review.

**Goal:** Prever a página selecionada e vizinhas na barra e permitir leitura automática com controle remoto.

**Architecture:** Miniaturas em cache separado de 32 MiB, criadas em disco a partir de uma página por vez, sem alterar página/progresso/proteção do leitor. ViewModel carrega até cinco prévias após 180 ms de estabilização do cursor, selecionada primeiro, cancelando e descartando resultados antigos. Automação: timer para imagens; rolagem por tela no EPUB de texto. AutoReadingState compartilhado no ViewModel com pausa explícita em interação, erro, fim, saída e segundo plano.

**Spec:** Usuário solicitou miniaturas para prever próximas páginas e slide/rolagem automática; confirmou intervalo inicial de 10 segundos por página/tela. Intervalos ajustáveis: 5,10,20,30,60 segundos. Tela de ferramentas permite iniciar/pausar e alterar intervalo; comandos manuais pausam. Manter identidade escura/âmbar e as confirmações da barra.

## Global Constraints

Até cinco miniaturas JPEG de 240×360 (proporção preservada); texto EPUB recebe cartão com trecho. Cache independente do original/cache de páginas. Não salvar progresso ao carregar miniatura. Troca automática de imagens só depois da página carregada com sucesso; nunca saltar página com erro. EPUB rola uma altura visível no intervalo escolhido, avança seção no fim e para na última. Pausar ao abrir ferramentas/barra, usar setas/Voltar, sair, perder foreground ou ocorrer erro. Não reiniciar automaticamente ao voltar ao app.

## Tasks

- [x] Testes de prévias: debounce, limites, nenhum progresso/carga principal, cancelamento e erro isolado.
- [x] Contrato thumbnail e geração/cache no repositório sem alterar protectedPage; ViewModel de até cinco prévias; UI de cartões.
- [x] Testes de automação: intervalo, espera por carga, pausa manual, erro, última página, texto sem avanço por timer de imagens.
- [x] UI de controle, timer de imagens e rolagem de texto, lifecycle e testes; revisão independente.
- [x] Build/testes/lint, assinatura e documentação; versão 0.4.0, GitHub e APK para reteste físico.
