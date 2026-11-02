# Temas no painel do celular (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.18 (código 92), AUTORIZADO PELO USUÁRIO EM 01/11/2026; Box e celular PENDENTES. Vibração ficou para o futuro.** (Texto abaixo é o registro original.) Antes: ANOTADO, NÃO IMPLEMENTADO. O usuário ainda vai acrescentar mudanças e pediu para esperar o aviso antes de começar.** Nada alterado no código, nenhuma versão nova, nada publicado por causa deste pedido.

## Pedido
- O painel web do celular (`app/src/main/assets/companion/index.html`) ganha troca de temas, como o app da TV (Comic TV, Flix, Prime, Max, Cinema).
- O tema do celular é **sincronizado com o tema que está em uso na TV**.
- O **layout e o design** do painel também mudam conforme o contexto de cada tema, como acontece na TV, e não só as cores.
- Nova opção de **Configurações** na aba **Arquivos**, no canto superior direito do cabeçalho, à direita de "TV conectada · Sincronizar" (marcação em vermelho na foto enviada pelo usuário).

## Decisões do usuário (01/11/2026, respostas aos pontos abaixo)
- **Tema próprio:** o celular pode ter tema próprio, além de seguir a TV.
- **Configurações:** as mesmas opções de tema que existem na TV, **sincronizadas nos dois sentidos**: o que for mudado no celular muda na TV.
- **Abas:** todas mudam de layout, sempre para o tema selecionado.
- **Dono:** só o Claude Code faz este trabalho; o Codex está sem tokens até 05/11/2026. Exceção ao fluxo de duas pastas, vale para este lote; publicação continua exigindo autorização explícita.
- **Ainda aguardando o aviso do usuário para começar**, que pode acrescentar mais mudanças.
- **Decisão final (substitui "tema próprio" e "dois sentidos" acima):** o celular fica **sempre sincronizado** com a TV, sem tema próprio e sem interruptor. O tema da TV vale no celular; mudar o tema pelas Configurações do celular muda o da TV, e o celular volta a mostrar o da TV.

## Mockup e regra do Controle (01/11/2026)
- Mockup em `docs/design/companion/phone-themes-mockup.html` (5 temas × Biblioteca, Leitor, Controle, Arquivos e Configurações, mais o menu aberto do Cinema). **Temas aprovados pelo usuário nesse formato.**
- **Controle não muda de layout.** Só as cores acompanham o tema. Tamanho, posição, função e espaço dos botões ficam exatamente como na foto do layout atual, que o usuário vai enviar. O bloco de setas desenhado no mockup é apenas aproximado e **não vale**; a referência é a foto `docs/design/companion/control-layout-approved-2026-11-01.png` (recebida e salva em 01/11/2026). Nada de reposicionar, redimensionar ou trocar função.
- Foto recebida. Aguardando o aviso do usuário para começar.

## Vibração ao tocar nos botões (pedido de 01/11/2026, anotado, não implementado)
- Opção nas Configurações do painel do celular: ao tocar em um botão, o celular vibra, para o usuário saber que acertou o botão sem olhar para a tela.
- **Limite técnico:** o painel é uma página web. No Android (Chrome) dá para usar `navigator.vibrate`. No **iPhone/Safari esse comando não existe**. Alternativa em estudo para iPhone: o truque do `<input type="checkbox" switch>`, que dá um toque tátil no iOS 17.4 ou mais novo, só dentro de um toque do usuário e dependendo de "Tátil do sistema" ligado nos ajustes do iPhone. **Não testado**; não prometer. Se não funcionar no aparelho do usuário, a alternativa é um clique sonoro curto opcional.
- Decidir: padrão ligado ou desligado; se vale para todos os botões ou só para as setas e o OK do Controle; força (um toque curto). A opção não muda o layout do Controle.

## Pontos a decidir antes de começar (originais; respondidos acima, exceto o que está em aberto)
- **Sincronização:** o celular só segue a TV, ou também pode escolher um tema próprio? Se puder, o que vale quando os dois divergem? Como a TV avisa a troca (o painel já lê o estado da TV a cada 0,3 s; ver `CompanionController` e `CompanionServer`).
- **O que a tela de Configurações contém:** só o seletor de tema, ou também outras opções do painel (por exemplo Inverter, tamanho do texto)? O botão fica só na aba Arquivos ou nas demais abas também?
- **Alcance do layout por tema:** quais abas mudam (Biblioteca, Leitor, Controle, Arquivos) e quanto cada tema altera a composição. Referência: os cinco layouts aprovados da TV em `docs/design/themes/`. O Controle do celular tem layout Completo aprovado (`docs/design/companion/`); decidir se o tema muda também esse bloco.
- **Fundos e cores:** a TV usa fundo neutro `#0F0F0F` e barras `#404040` nos temas neutros (Comic TV, Flix, Cinema) e preserva as paletas dos coloridos. Alto contraste e tamanho de texto também precisam valer no celular.
- **Persistência:** onde guardar a escolha do celular (armazenamento do navegador) e o que acontece sem TV conectada.
- **Dono do trabalho:** pelo fluxo de duas pastas, funcionalidade nova costuma nascer com o Codex e o acabamento de layout fica com o Claude Code. Definir no início do lote.

## Riscos
- Safari/iPhone e a Box seguem sem validação física; teste automatizado não prova o visual.
- O painel é um HTML único; cinco temas com layouts próprios aumentam muito esse arquivo e os testes (`tools/companion-ui.test.mjs`, `tools/companion-ui-browser.cjs`).
- Mudar layout por tema pode quebrar o Controle do celular aprovado; preservar funções e foco.

## Fluxo
Seguir o `AGENTS.md`. Começar só quando o usuário avisar. Publicação só com autorização explícita.
