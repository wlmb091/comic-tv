# Ícone do Catálogo e ordem Novidades/Catálogo na TV (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.20 (código 94), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
1. **Ícone.** No menu dos temas da TV, Catálogo e Navegador estão com o mesmo ícone de globo (foto do menu lateral, tema neutro). Trocar o do **Catálogo**; o do Navegador fica.
2. **Ordem.** Hoje Catálogo aparece antes de Novidades. Inverter: Novidades primeiro.

## Onde está a ordem, pela leitura do código da 1.4.19
- **Fileiras da tela inicial:** `LibraryScreen.kt:751`, `listOf(false, true)` desenha Catálogo e depois Novidades. É a troca pedida.
- **Menu lateral:** já mostra Novidades antes de Catálogo (`ComicTvApp.kt:498` e foto). Não muda.
- **Botões da Busca:** `GlobalSearchScreen.kt:195-196` também têm Catálogo antes de Novidades. Proposta: inverter junto, para a ordem ficar igual em todo o app.

## Pontos a verificar ao implementar
- O ícone novo precisa valer em todos os temas e ser diferente dos demais do menu.
- Restauração do foco ao voltar para as fileiras (`homeOriginRow`) e a navegação ↑ ↓ entre elas depois da troca.
