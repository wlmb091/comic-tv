# Alinhamento dos cards da Biblioteca no celular (01/11/2026)

Status: **IMPLEMENTADO NA 1.4.19 (código 93), AUTORIZADO PELO USUÁRIO EM 01/11/2026; Box e iPhone PENDENTES.**

## Pedido
- Na aba **Biblioteca** do painel do celular, **todos os temas** ficaram com os cards **desalinhados**.
- O usuário sugere **encurtar o texto um pouco antes** (títulos longos), para os cards ficarem "certinhos".

## Fotos enviadas (3 temas)
- Tema com lateral esquerda (Comic TV): "Minha biblioteca" com cards lado a lado; títulos de 1, 2 e 3 linhas (ex.: "Tio Patinhas 423" vs "Godzilla Destrói o Universo Marvel #01 (2025) (SoQuadrinhos)") fazem os botões **Ler** e **Na TV** ficarem em alturas diferentes entre cards vizinhos.
- Tema roxo com barra inferior flutuante (Max/Prime): grade de 2 colunas, mesmo desalinho dos botões Ler / Na TV por causa de títulos com 2 e 3 linhas; barra flutuante cobre parte dos cards.
- Tema escuro com botões brancos (Cinema): fileira rolável horizontal, botões Ler / Na TV em alturas diferentes.

## Direção da correção (a validar na implementação)
- Limitar o título a um número fixo de linhas (corte com reticências) e/ou reservar altura fixa para título + páginas, para os botões **Ler** e **Na TV** alinharem na mesma linha em todos os cards, em todos os temas.
- Não mudar o tamanho das capas nem o layout do Controle (regra permanente).
- Relaciona-se com `2026-11-01-phone-collapsible-bars.md` (barra flutuante cobre cards) e `2026-11-01-flix-reader-header.md`.
