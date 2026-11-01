# Modo escuro para qualquer página no navegador da TV (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.15 (código 89), AUTORIZADO PELO USUÁRIO EM 01/11/2026. Nada executado na Box nem no celular.** Registro: `docs/validation/1.4.15.md`. Decisão da implementação: inversão pura, sem hue-rotate, porque o teste mostrou os quadrinhos desbotando; as cores do próprio site mudam de tom.

## Pedido
Poder transformar qualquer página em escura quando necessário. Motivo: algumas páginas de quadrinhos têm fundo branco, e o usuário quer ler os quadrinhos sem esse fundo.

## Abordagem proposta
- Injetar um estilo na página que inverte as cores (`filter: invert(1) hue-rotate(180deg)` no documento) e reinverte imagens, vídeos e fundos de imagem, para que as páginas dos quadrinhos mantenham as cores originais e só o fundo escureça.
- Ícone na barra superior do navegador para ligar e desligar, junto com o do adblock (`2026-11-01-browser-adblock.md`).
- Opção de lembrar a escolha por site (host), no estado salvo do navegador, para que o site de HQ já abra escuro.

## Por que não o modo escuro nativo do WebView
O app aceita Android 8 (`minSdk 26`). `setForceDark` só existe a partir do Android 10 e está obsoleto; o modo atual (`WebSettingsCompat.setAlgorithmicDarkeningAllowed`) exige a biblioteca `androidx.webkit`, que o projeto não tem, e depende da versão do WebView instalada. O estilo injetado funciona em qualquer aparelho, inclusive Boxes mais antigas.

## A decidir antes de começar
- Por site ou global; padrão desligado.
- Reaplicar a cada navegação e em páginas de página única (injeção em `onPageStarted`/`onPageFinished`).
- Foco/D-pad do ícone, estado visível ligado/desligado e item correspondente no menu "Opções do navegador".

## Riscos e limites a avisar
- Cores aproximadas; sites com muitas imagens de fundo, botões coloridos ou gradientes podem ficar estranhos.
- Imagens colocadas por CSS como fundo podem sair invertidas; páginas em iframes podem não escurecer.
- Custo em TV Box fraca: filtro na página inteira pode pesar na rolagem; medir e, se necessário, limitar o filtro ao corpo da página.
- Não vale para conteúdo que o site já entrega escuro (evitar inverter de volta para claro).
- Compilação e testes automatizados não provam o resultado visual; testar em sites reais de HQ na Box.

## Relação com outros itens futuros
Ajustes por site no menu do navegador, a reunir no mesmo lote: "Permitir imagens HTTP neste site" (`docs/research/2026-11-01-hqnow-reader-mixed-content.md`) e adblock.

## Fluxo
Seguir o fluxo de duas pastas do `AGENTS.md`: definir no início do lote de quem é cada parte (funcionalidade nova costuma nascer com o Codex; layout e foco do ícone ficam com o Claude Code). Commits só locais, e publicação só com autorização explícita do usuário.
