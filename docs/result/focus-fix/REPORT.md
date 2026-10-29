# Reserva do foco — implementação local, validação visual pendente

## Causa confirmada

As fileiras compartilhadas usavam somente 4–6 dp verticais e nenhum recuo horizontal; a grade principal reservava 0–16 dp no fim. A escala/borda dos Cards podiam ultrapassar o viewport recortado. Faltava elevação do cartão focado. As fotos originais estão em `reference/`: são evidência do defeito anterior, **não capturas do resultado corrigido**.

## Alteração

`HomeFocusSafety.kt` centraliza `ceil((scale - 1) / 2 * max(largura, altura) + borda + brilho)`, mínimo 24 dp, usando medidas reais dos cartões. O raio de brilho adicional existente é zero; não foi criado novo efeito nem alterado foco/cores. Medidas maiores aumentam a reserva compartilhada.

A grade aplica a área útil uma vez: horizontal `max(48, overflow)`, topo 27 e fim `27 + overflow`. Os viewports das fileiras se estendem através do recuo da grade e reaplicam esse mesmo recuo como `contentPadding`; portanto título e primeiro cartão começam na mesma coluna, sem soma de 48 + 48. A rolagem continua com recorte normal. A arte permanece fullbleed.

Fileiras recebem padding vertical de overflow e os cartões focados recebem zIndex, mantendo borda/brilho/escala no Card Material existente. Hero, marca, botões e títulos deixam de adicionar recuos horizontais próprios na Home. Navegação lateral/superior/dock preservada. Larguras/alturas fixas, cores, escalas, bordas, espaçamento entre cartões e callbacks de navegação não foram alterados.

A grade Comic TV mantém a política anterior de cinco colunas proporcionais. Sua largura se adapta à área útil menor: não é possível manter simultaneamente cinco células proporcionais com a mesma largura física anterior e acrescentar 96 dp de margem sem mudar a rolagem/colunas. Não foi introduzida nova rolagem nem mudança de quantidade de colunas.

## Verificações

`build/focus-safe-area.log`: testes JVM de fórmula/limites/cinco temas e regressões ThemeSpec/HomePreviewPolicy passaram; compilação AndroidTest aprovada (1m36s). Revisão estática independente não encontrou bloqueio de produção ou margem duplicada.

Teste nativo `HomeFocusGeometryTest`: fixture de 640×360 dp, Card Material real, cinco temas, primeiro/último focados, origem horizontal e reserva vertical. **Compilado, não executado**. Não equivale a capturas de fidelidade visual.

## Capturas de aceitação pendentes

`adb devices -l` não encontrou dispositivo. A decisão anterior permanece: conferir na Box, sem instalar aceleração/driver ou alterar USB. Nenhum APK foi gerado/publicado por pedido explícito do usuário. Por isso não há capturas reais do resultado nesta pasta e a aceitação visual está **pendente**.

Na Box, capturar primeiro e último cartão focado em cada fileira visível de cada tema: Continuar lendo, Sua coleção, Catálogo e Novidades; Max também Explore suas coleções. Comic TV tem duas linhas por prévia: conferir extremidades de ambas. Incluir a última fileira junto ao rodapé e conferir as quatro bordas. Repetir 100% e texto 130%, incluindo alto contraste. Nome sugerido: `<tema>-<secao>-primeiro.png` / `<tema>-<secao>-ultimo.png`.

Não há push, incremento de versão, CI ou fechamento de pacote. Ajustes anteriores de navegador/busca/leitor foram preservados.

Fixture640×360dp recompilada com sucesso em build/focus-fixture-compile.log (33s). A execução física e as capturas continuam pendentes.
