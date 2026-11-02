# Novo layout universal do modo Leitor no celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.20 (código 94), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
O usuário editou uma captura do Comic TV para testar um layout diferente no modo Leitor e quer que ele seja **universal** (entendido como igual em todos os temas; confirmar). As funções de cada elemento serão descritas por ele.

## Foto enviada (montagem do usuário, não é o app atual)
Página do quadrinho ocupando a tela, com quatro áreas marcadas em vermelho:
- **Topo, centro:** pílula arredondada com seta ⌄.
- **Lateral esquerda, meio da altura:** aba estreita com seta ›.
- **Lateral direita, meio da altura:** aba estreita com seta ‹.
- **Base:** barra com ‹ Anterior, Próxima › e, no meio, o texto da página e Sincronizar.

## Decisões do usuário (02/11/2026)
- **Universal:** mesmo layout no Leitor de todos os temas, mudando só as cores.
- **Seta do topo:** abre um menu flutuante com as abas Biblioteca, Leitor, Controle e Arquivos.
- **Barra de baixo:** flutuante, só no modo Leitor, com Anterior, Próxima, Sincronizar e número da página. **Sempre visível.**
- **Seta da direita:** abre uma opção lateral igual à do leitor da TV, com as mesmas funções, sincronizada com a TV.
- **Seta da esquerda:** abre um slider de rolagem e um slider de zoom, um em cima do outro, exibidos apenas no celular.
- **Painéis laterais persistentes:** só fecham ao tocar de novo para recuar.
- **Dois toques rápidos na página:** zoom in no ponto tocado; dois toques de novo: zoom out.
- **Setas laterais só com Sincronizar ligado.** Desligado, o Leitor fica só com a seta do topo e a barra de baixo.
- **Sliders com Sincronizar ligado:** aparecem só no celular, mas zoom e rolagem também vão para a TV.

## Funções do leitor da TV (`ReaderTools.kt`)
- **Painel:** Continuar, Escolher página, Capítulo anterior, Próximo capítulo, Capítulos / edições, Visualização (Normal, Galeria, Livro), Ajuste (Página, Largura, Altura), Imagem e mais opções, Tentar novamente (só com erro) e Sair da leitura.
- **Imagem e mais opções:** Voltar ao menu, Direção, Girar 90° (fora do modo retrato), Usar página atual como capa da saga, Filtro, Intensidade, Comparar com original, Leitura automática e Intervalo.
- **Hoje ocultos:** Ouvir livro e traduzir, pausado desde a 1.1.8.

## Pontos a verificar ao implementar
- Hoje o painel não tem comandos para a maioria das funções do leitor da TV.
- Nível do zoom de dois toques: proposta de 200%, dentro do limite de 300%.
- Painel aberto cobre parte da página, porque ele só fecha pelo toque do usuário.

## Regras que continuam valendo
- O layout aprovado do Controle não muda.
- Este layout substituiria, no Leitor, a seta única por tema da 1.4.19.
