# Lote layout e foco na TV — 31/10/2026

Levantado por vídeos do usuário gravados na 1.4.8 (comandos falados + quadros). Usuário autorizou começar em 31/10/2026 e pediu manter o que já está certo. Sem push nem publicação: Codex publica mediante autorização.

Relato do usuário: 1.4.8 resolveu o erro de instalação de extensões.

## Decisões do usuário

- Detalhes abrem com foco em Continuar leitura.
- Voltar no Navegador continua abrindo os controles.
- Max: barra inferior sempre visível com espaço reservado. Home invertida: começa no destaque acima da barra, cima avança pelas seções, baixo volta até a barra. Grades/listas longas mantêm leitura normal; Configurações, Busca e Detalhes rolam normalmente. Substitui barra oculta/recolhimento da 1.2.9.
- Adiados: vídeo do navegador, backup de sagas, montador CBZ, nuvem/comunidade, Hub.

## Correções

Comuns: foco forte nos cinco temas; manter coluna ao trocar de fileira; distinguir foco de seleção; cortes no topo/rodapé ao rolar; piscada do fundo ao trocar de edição.

Home: legibilidade sobre arte clara (Flix, Prime, Cinema, Max); ícones da lateral recolhida sobre capas e colados nos filtros; lateral aberta com fundo insuficiente; filtros/`Gerenciar coleções` cortados; `Ver mais` desalinhado.

Prime: menu superior cortado (`Cel`) e sem foco visível; menu transparente sobre conteúdo ao rolar; capas muito cortadas.

Cinema: botão Menu cobre capas; título fino ilegível; menu cortado no topo/rodapé.

Comic: foco creme quase invisível; marca colada na lateral.

Max: barra e Home conforme decisão; botão/último item coberto; título sobre a marca.

Busca: campo sempre contornado parece focado; ↑ com texto move cursor antes de sair; Baixo de Consultar livros vai a Atualizar índices; fontes em `Nos sites` com alturas diferentes; mensagens técnicas; estado de busca pouco visível; textos longos pequenos; cortes em filtros/sites; `0/0 fontes` confuso.

Novidades/Catálogo: `Capa indisponível` antes de carregar; blocos vazios enormes; Voltar de Escolher fonte volta à pasta sem cortar cabeçalho e sem aperto perdido; pasta/lupa sem nome; dois destaques no Menu.

Detalhes: foco inicial; abre sempre no topo; volta do painel de capítulos consistente; `1 edições`.

Navegador: foco invisível ao abrir controles, em Voltar/Avançar e no endereço; legenda desatualizada; saída volta ao botão Navegador; Favorito ↓ salta para `+`.

Configurações (Max): Voltar salta para aba sem sair; Baixo das abas abre a barra.

## Fora do lote

Celular não entrou nos vídeos. Investigar repetição de tecla em Detalhes exige ADB com a Box.

## Implementado

Foco: contorno 3dp/4dp nos cinco temas, cor clara por tema, botões, escolhas e menus preenchidos ao focar; seleção do menu marcada com `●`.

Colunas: causa provável removida das fileiras da Home (`focusRestorer` devolvia o último cartão visitado). `Consultar livros` desce para `Abrir Extensões`. Fileiras irregulares ainda seguem a geometria do Compose.

Campos: ↑/↓ saem do campo; ←/→ saem até o OK ativar o teclado. Foi este consumo que deixou os controles do Navegador sem foco visível ao abrir.

Max: barra sempre visível com altura reservada, sem recolhimento; Home com `reverseLayout`, títulos acima das fileiras; páginas completas e demais telas normais.

Detalhes: foco inicial em Continuar leitura, abertura no topo, volta do painel ao último botão usado, `1 edição`. Retorno do leitor continua restaurando o capítulo.

Fundo: imagem anterior fica até a nova decodificar (sem piscada); escurecimento maior; sombra extra quando o destaque sai da tela.

Menus: Prime sem `Cel` cortado em texto normal e com sombra atrás; Flix com sombra atrás dos ícones e painel mais escuro; itens laterais 44dp para caber marca e Configurações. Cinema oculta o botão Menu ao rolar (→ no fim da fileira continua abrindo).

Busca: campo só contornado com foco; contagem/estado claros, sem `0/0` antes de buscar; falhas resumidas por fonte, sem códigos HTTP; textos encurtados; botões de sites com altura uniforme; saída do Navegador volta ao botão de origem.

Novidades/Catálogo: `Carregando capa…` até falha real; `Trocar fonte`/`Buscar` com nome; Voltar de Escolher fonte vai à pasta com cabeçalho inteiro.

Navegador: endereço com foco e legenda próprios; Voltar/Avançar indisponíveis focáveis e visíveis.

Celular: relato do usuário: Voltar na tela principal abre `Sair?` e o segundo Voltar parece cortar a conexão até o controle remoto fechar a janela. Causa no código: janelas sem registro tiram o foco do app e o servidor recusa o comando (`Volte ao Comic TV para controlar`). Janela de sair e as demais `Dialog` agora registradas; Voltar no celular fecha a janela sem sair do app. Não reproduzido em aparelho.

Painel do celular (foto do usuário, setas estreitas; OK oculto no touchpad): setas ocupam a área do touchpad. No navegador, touchpad continua no cursor; setas e OK enviam teclas à página (`browser-up/down/left/right`, `browser-ok` = Enter). `Teclado` troca para setas; envio bem-sucedido limpa o campo, fecha o teclado do celular, mantém setas e fecha o teclado da TV. As setas não navegam nas letras do teclado da TV (janela do sistema); o texto vai direto ao campo. OK continua desativado no leitor. Ficam em setas até troca manual. Largura conferida a 390x800 no navegador (96dp com a coluna de páginas). Pedido usuário: ao terminar, gerar APK.

## Mantido de propósito

Capas horizontais do Prime (recorte aprovado na 1.2.7); Favorito ↓ para `+` (único item naquela direção); foco do Comic em todas as telas segue tema, sem mudar a estante.

## Não resolvido ou não confirmado

Voltar nas Configurações movendo foco à aba sem sair e aperto de Voltar possivelmente perdido em Escolher fonte: sem causa no código, conferir no próximo teste. Salto de coluna em grades irregulares e demora real das capas nas fontes podem persistir. Celular não alterado.

## Validação

319 testes JVM e 20 Node, 0 falhas; compilação release e AndroidTest e lint release concluídos, log `build/validate-layout.log`. Testes Compose atualizados apenas compilados; `companion-ui-browser.cjs` atualizado sem execução (Playwright ausente). Sem execução em Android/Box: ADB sem dispositivo.
