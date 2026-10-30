# Navegador TV e downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** substituir a seção antiga por navegador TV com abas, cursor/controles e downloads acompanháveis.
**Architecture:** modelos testáveis de sessão/downloads separados dos WebViews e da interface Compose. Site sem reflow por overlays; download com armazenamento privado e fila sequencial.
**Tech Stack:** Kotlin, ComposeTV, AndroidWebView, OkHttp/coroutines/Gson existentes.
**Spec:** docs/design/browser-tvbro-2026-10-29.md

## Global Constraints
- SEMAPK/push/publicação; preservar correção de capas e demais dados/USB.
- API26/Box2GB/alto contraste/texto85–130%/teclado somenteOK.
- Não copiar código/assets nem alegar teste físico pelo estudo estático.

## Tasks
- [x] Inspecionar artefato, manifest/resources/DEX e fonte tag; registrar origem/limites.
- [x] Download HTTP: testes de filename/cookie/referer/redirecionamento/Range/truncamento; atualizar BrowserDownloader mantendo fachada compatível para testes existentes.
- [x] BrowserDownloads: fila, estados, progresso, pausa/retomar, persistência atômica, importação e exclusão privada; testes com transporte simulado.
- [x] Sessão: abas limitadas, favoritos/histórico, persistência; host WebView limitado2com save/restore, cursor contínuo e opções do link.
- [x] UI: remover antiga tela/toolbar, usar overlays endereço/abas/nav, downloads próprios, menus e vídeo fullscreen; compilar testes de foco/viewport.
- [x] Integração/checagens locais focadas, revisão final e registro; sem release.

## Review Focus
- Falha de rede e bytes parciais, HTTP206/200/416 e mudança deETag, 403/HTMLlogin.
- Nenhum cookie/Authorization copiado entre destinos; nome nunca vira caminho externo.
- Foco ao abrir/fechar overlay, IME, abas e downloads; página sem redimensionamento.
- Memória com8abas, cancelar/reiniciar sem apagar completados ou importados.
- Site blob/popups/filechooser/vídeo: limites explícitos e sem permissões de sistema novas.

## Ledger
- Ruling: implementação própria sobre WebView existente — mesmo motor da varianteAPK, sem embutir outro app/dependências/código de terceiros.
- Ruling: executar sequencialmente neste chat conforme preferência do usuário, sem gates por subtarefa.

- Ruling: blob por canal de mensagens de uso único, origem idêntica e confirmação explícita, chunks196608bytes/ACK/limites; sem JavascriptInterface global. Evita falta de suporte do antigo transferidor HTTP sem alocar arquivo inteiro.
- Ruling: manter proibição anterior de downloadsAPK também no nome/MIME da resposta e no blob; documentos/imagens/outros arquivos genéricos podem ser salvos, importação continua só formatos suportados.
- Ruling: Back do controle recupera/fecha controles como referênciaTVBro; navegar na história fica nos botões voltar/avançar. Teclado/vídeo/diálogo têm prioridade no Back.
- Review: fresh-context browser_final_review identificou restriçãoAPK e estado de tecla retido ao abrir controles. Corrigidos e rechecados pelo revisor, sem pendências concretas relatadas. Sem teste físico.

- Verification: tv-browser-final36testes/0falhas+AndroidTestcompile; lintDebug0erros/70avisos2m29; tv-browser-complete21testes/0falhas1m52apósrestaurarbytesreaisdoparcial. SemAPK/push/CI. Registrodocs/validation/2026-10-29-tv-browser.md.
