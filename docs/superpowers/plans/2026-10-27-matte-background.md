# Vidro mais fosco e fundo #0F0F0F — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Corrigir o resultado físico relatado da1.1.21: fundo sólido#0F0F0F e conteúdo atrás da lateral sem legibilidade.
**Architecture:** Background#0F0F0F somente em Comic TV/Flix/Cinema; fundos coloridos preservados, preto em alto contraste. Não desenhar identidade sobre o fundo neutro sólido. Capa opcional dos detalhes permanece explícita. Superfície fosca na cor do painel com opacidade mínima247/255; eliminar alfa animado do contêiner inteiro, animar só reflexo decorativo. Manter escala dos ícones e cantos8dp. BackHandler de saída registrado antes do conteúdo/NavHost, deixando handlers internos vencerem; confirmação com Continuar focado inicialmente.
**Tech Stack:** Compose TV/API26, animation-core já existentes, JUnit.
**Spec:** Pedido e foto do usuário em27/10/2026. Escopo preciso autoriza correção direta, sem nova prévia. Alfa78% e alfa96% do contêiner permitiam leitura do conteúdo; cobertura98% da identidade não alterava Background de cada preset.

## Global Constraints / Review Focus

Fundo sólido exato nas seções principais dos temas neutros; fundos dos temas coloridos preservados por correção explícita do usuário. Alto contraste opaco, texto85–130%, foco/chaves/layout/dados/USB inalterados. Não alegar blur real de conteúdo vivo; material fosco reduz transparência em API26. Não inferir validação física da compilação. Confirmação só no Voltar que encerraria a Activity, sem interceptar Home/Recentes ou consumir o Voltar interno do leitor/diálogos. Pedido adicional de estudo do APK de referência é análise independente, não autorização para implementar um novo tema cinematográfico neste pacote.

- [x] Reproduzir regressão da cor em JUnit antes da mudança: Amber0x090A0C em vez0x0F0F0F, falha real em31s.
- [x] Aplicar token de fundo neutro e superfície fosca; animar só brilho e não alfa de texto/painel. Fundos coloridos preservados.
- [x] Implementar confirmação no fallback de Voltar, Continuar focado e Sair explícito; caso de prioridade de leitor/NavHost e cancelamento preparado em AndroidTest.
- [x] Acrescentar lateral#404040 e botões escuros somente nos neutros; foco cinza com contorno/White. Preservar cores/alto contraste. Caso isolado de pop NavHost adicionado à cobertura preparada.
- [x] Verificar cor em todos os presets, contribuição máxima do fundo e botões neutros/coloridos/alto contraste; build/testes/lint/AndroidTest compile passaram.346casos/1ignorado/0falhas; lint0erros/71avisos. Native somente compilado.
- [x] Revisão independente sem bloqueantes, release1.1.22/code61 publicada pelo CI autorizado e arquivos públicos conferidos (hash/versão/certificado/ABI). Resultado físico depende do relato na Box.
