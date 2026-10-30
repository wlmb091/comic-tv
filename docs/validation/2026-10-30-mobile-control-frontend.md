# Painel celular — revisão frontend 1.4.6

Implementação do documento aprovado `mobile-control-revision` e complemento contextual do usuário. Arquivo: `app/src/main/assets/companion/index.html`.

- Removidas imagem da TV, captura `/screen`, atualização automática e ampliação de prévia. Controle usa estado leve `/reader-state`, consultado a cada 900 ms somente na aba Controle visível, sem consultas simultâneas.
- No leitor da TV, setas e touchpad enviam exclusivamente pan da imagem; Anterior/Próxima enviam `reader-turn`; slider horizontal envia índice de página; slider vertical envia zoom e permite restaurar 100%. Nenhum OK é enviado por toque do touchpad durante o leitor.
- Fora do leitor, setas/OK/Voltar/Menu/rolagem/texto mantêm comandos normais; touchpad envia `pointer {dx,dy}` relativo. Pan e pointer são limitados a ±1; setas pan usam passos 0,12. Repetição de seta: início imediato, espera 350 ms e repetição a cada 140 ms, encerrada ao soltar/cancelar/perder captura, aba, visibilidade ou foco.
- Layout Controle e Leitor usa altura dinâmica `100dvh` com `visualViewport` para barras/teclado e safe areas, rodapé fora do conteúdo. Pinça não recalcula altura durante escala maior que 1. Touchpad impede gestos do navegador apenas na área própria; botões usam `touch-action:manipulation`, imagem móvel preserva pan/pinch-zoom.
- Leitor móvel compacto: título discreto, imagem ajustada à área disponível, Anterior/Próxima e contador, sem ferramentas sobre a página. Mantida sessão independente de leitura e carga abortável.
- Arquivos: cards com tipo ou capa disponível, nome à direita, status terminal explícito, progresso real XHR e cancelamento explicado. “Limpar concluídos” remove apenas os cards terminais locais em memória, sem qualquer chamada de exclusão ou alteração dos originais. Novos formatos anunciados conforme pacote 1.4.6.

## Verificação

`node --test tools/companion-ui.test.mjs`: 8 testes aprovados, 0 falhas. Os seis casos iniciais falharam na versão anterior (RED), depois passaram. Casos cobrem remoção do espelho, contextualização pan/remoto, limites página/zoom, sliders inativos fora do leitor, limpeza local, pinça e superfície mínima.

`NODE_PATH=C:/Users/wlmb/.codex/skills/_mcp-runtime/node_modules node tools/companion-ui-browser.cjs`: aprovado no Microsoft Edge headless com biblioteca/estado da TV simulados; 0 erros JavaScript. Geometria medida em 390×844, 844×390 e 390×600: nenhuma largura excedente, painel acima do rodapé, rodapé dentro da viewport. Comandos pan, virar página e navegação contextual conferidos; leitura móvel muda apenas sua própria página; upload real XHR da fixture conclui e limpar mantém download original disponível.

Capturas e relatório reproduzível em `docs/result/companion-146/`. Fixtures não provam execução no Safari físico nem controle real da Box; essas verificações permanecem manuais. Nenhum Gradle, APK, commit, push ou publicação foi executado por esta subtarefa.

## Integração Kotlin e navegação TV

`CompanionServer` agora limita `reader-pan` e `pointer` a deltas finitos ±1, página 0–4999, virada ±1 e zoom finito 1–3. GET `/reader-state` substitui `/screen`; PixelCopy e seu cache foram removidos do controller. Estado do leitor é consultado na thread principal, com associação apenas enquanto a entrada Reader está RESUMED e remoção no pause/dispose.

ReaderViewModel expõe operações explícitas de pan/zoom/página/virada. Pan nunca vira página nem exige mudar o modo físico; zoom mínimo restaura pan centralizado; virar usa `turnPage` já existente, preservando zoom e topo na próxima página. RTL e rotação visual não são remapeados. Modo Livro mantém passos de pares; seleção por slider usa o índice explícito. Operações são recusadas enquanto inicializa, encerra ou narra. Progresso e cancelamento/prefetch usam o mesmo caminho das ações físicas. O controller também contextualiza setas/pointer/scroll antigos enquanto Reader está vinculado, impedindo que um poll atrasado do celular cause virada por seta.

Touchpad fora do leitor usa o cursor existente de `RemoteBrowserWebView` apenas quando tem foco e está habilitado; em outras superfícies navega pela direção dominante. Rotas próprias `browser` e `companion`, com novos ícones vetoriais, entraram na lista compartilhada Sidebar/TopBar/Dock/Drawer dos cinco temas. Áreas de foco e rolagem mantidas; ícones/gaps discretamente menores. Hooks de importação protegida foram ligados às telas Importar e Explorador.

Verificação JVM isolada, sem Gradle: Kotlin compiler 2.2.0/JDK17 executou `CompanionServerTest`: RED na implementação HEAD anterior (`reader-state` retornava 404), GREEN com **6 testes, 0 falhas** na implementação nova. Script local de evidência `build/mobile-kotlin/check.ps1`. Dois testes ReaderViewModel adicionais cobrem pan/zoom sem mudança de modo/página e mudança de página com zoom/topo/RTL/Livro; cinco testes Compose novos cobrem os destinos separados a 130% e alto contraste, e o teste da Sidebar curta foi atualizado para a nova ordem. Compilação/execução desses testes Android/app será feita na verificação integrada pelo agente principal; não se alega execução física.


Integração final:96JVM/17Node passaram, AndroidRelease eAndroidTest compilados,lint0erros99avisos. Capturas Edge repetidas após integração,3viewports/5comandos/0erros. Revisão identificou scroll próprio do texto e rotas ausentes; ambos corrigidos antes de6379942. As imagens usam fixtures, não indicam testeSafari físico. Publicação1.4.6 autorizada pelo usuário.
