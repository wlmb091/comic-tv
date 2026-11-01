# Adblock no navegador da TV (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.15 (código 89), AUTORIZADO PELO USUÁRIO EM 01/11/2026. Nada executado na Box nem no celular.** Registro: `docs/validation/1.4.15.md`. Decisões da implementação: ligado por padrão, ícone global, lista própria curta embutida, sem encurtadores de link.

## Pedido
- Bloqueador de anúncios no navegador da TV.
- Ícone na barra superior do navegador para ativar e desativar o bloqueio.

## Pontos a decidir antes de começar (não decididos)
- **Alcance do ícone:** ligar/desligar para todos os sites ou por site. Sites que quebram com o bloqueio precisam de saída rápida.
- **Estado visível:** ícone distinto ligado/desligado e, se possível, contador de itens bloqueados na página. Foco e D-pad como nos demais botões da barra.
- **Padrão:** ligado ou desligado ao instalar a atualização.
- **Lista de regras:** de onde vêm e a licença. Listas públicas como EasyList têm licença própria (copyleft); conferir antes de embutir no APK ou baixar. Não copiar código de outros bloqueadores sem checar licença. Atualização da lista só por ação explícita, como o atualizador do app.

## Abordagem provável
- Filtrar requisições de recursos no WebView (`shouldInterceptRequest`) por domínio e padrão de URL, devolvendo resposta vazia. Cobre scripts, imagens e frames de anúncios; não depende de nova versão do WebView.
- Opcional: ocultar elementos de anúncio por regra cosmética (injeção de CSS/JS), mais frágil e mais pesada.
- Persistência no estado salvo do navegador (`BrowserSessionState`), como as demais opções.
- Teste unitário da lógica de correspondência (domínio, subdomínio, exceções) e Node/painel não se aplicam.

## Riscos e limites a avisar
- Não há garantia de bloquear todos os anúncios; anúncios servidos pelo mesmo domínio do conteúdo, e anúncios dentro de vídeo (YouTube), costumam passar.
- Pode quebrar páginas, formulários, logins e players de vídeo. Por isso o ícone de ativar/desativar.
- Custo de CPU e memória em uma TV Box: lista grande precisa de busca eficiente (conjunto de domínios, não regex por requisição).
- Pode interferir nos sites já adaptados (HQ Now, DS Club, MEGA e outros); testar com eles antes de liberar.
- Combinar com a futura opção "Permitir imagens HTTP neste site" (`docs/research/2026-11-01-hqnow-reader-mixed-content.md`): ambos são ajustes por site no menu do navegador.
- Compilação e testes automatizados não provam o efeito na Box; testar em sites reais.

## Fluxo
Seguir o fluxo de duas pastas do `AGENTS.md`: definir no início do lote de quem é cada parte (funcionalidade nova costuma nascer com o Codex; layout e foco do ícone ficam com o Claude Code). Commits só locais, e publicação só com autorização explícita do usuário.
