# Biblioteca do celular como clone do menu da TV em retrato (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
A Biblioteca do celular clonaria o menu da TV em modo retrato, praticamente um espelhamento de tela, com a mesma ideia do Leitor.

## Entendimento
Mesma ideia do Leitor quer dizer: o celular redesenha a tela com os dados da TV, em HTML. Não é transmissão de vídeo, e o desenho pode ter pequenas diferenças em relação à TV.

Reúne no mesmo trabalho:
- `2026-11-02-phone-library-sagas.md`
- `2026-11-02-phone-library-sources.md`
- `2026-11-02-phone-chapters.md`

## Decisões do usuário (02/11/2026)
- **Sincronia:** rolar e olhar no celular não mexe na TV. Com Sincronizar ligado, abrir uma edição abre nos dois.
- **Visual:** cada tema segue a própria composição da TV em retrato. Só o menu flutuante é igual em todos.

## Pontos a verificar ao implementar
- Referência visual: tela inicial da TV em retrato de cada um dos cinco temas (`ThemeSpec`), com destaque, Continuar lendo, Sua coleção, Novidades e Catálogo.
- A navegação entre abas continua pelo menu flutuante universal (`2026-11-02-universal-floating-menu.md`).
- O layout aprovado do Controle não muda.
