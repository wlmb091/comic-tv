# Flix no celular: Leitor desregulado e cabeçalho COMIC TV (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.19 (código 93), AUTORIZADO PELO USUÁRIO EM 01/11/2026; Box e iPhone PENDENTES.**

## Pedido
- **Tema Flix, aba Leitor:** a tela ficou desregulada. Na foto (Safari/iPhone), a barra inferior Anterior/Próxima fica cortada/escondida atrás da barra do Safari (endereço 192.168.0.72) e sobra faixa azul; a altura da tela não cabe na área visível.
- **Remover a barra do cabeçalho "COMIC TV"** (marcada em vermelho na foto) **dos temas**, para sobrar mais espaço.
- O botão **Sincronizado** desce e fica **no meio da barra por onde se passam as páginas** (barra Anterior / Próxima).

## Foto enviada
- Flix, Leitor: cabeçalho "COMIC TV" + botão "⇄ Sincronizado" no topo (caixa vermelha), abas Biblioteca/Leitor/Controle/Arquivos, página do quadrinho, barra inferior cortada pelo Safari; seta vermelha ligando o botão ao meio da barra inferior.

## Decisões do usuário (01/11/2026)
- **Cabeçalho "COMIC TV" sai de TODOS os temas.** No Leitor sobram só: a imagem, a barra Anterior/Próxima e a barra recolhida dos temas (ver `2026-11-01-phone-collapsible-bars.md`).
- **Sincronizado fica ao lado de "Página X de Y"** na barra de páginas, no meio entre Anterior e Próxima, como **botão só de texto** (sem ícone/moldura pesada) para ocupar pouco espaço.

## Regras que continuam valendo
- Layout do Controle aprovado não muda (só cores). Remover o cabeçalho não pode deslocar o bloco do Controle.
- Relaciona-se com `2026-11-01-phone-collapsible-bars.md` (barras recolhidas): implementar juntos para não conflitar.
