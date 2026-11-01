# Orientação global da tela (1.4.11)

Pedido do usuário: girar o app inteiro para usar projetor em retrato, com layout adaptado. Mockup aprovado: https://claude.ai/artifact/V89ivtremPiqr5UidwRGob

## Decisões

- **Giro por View, não por `graphicsLayer`.** `OrientedRootLayout` gira o `ComposeView` com limites trocados. Assim o Compose mede e busca foco nas coordenadas de quem vê, sem remapear teclas. Com `graphicsLayer`, a busca de foco usaria coordenadas físicas e ↑ moveria o foco para o lado. O giro antigo do leitor continua como estava em paisagem.
- **Diálogos.** Janelas de diálogo não herdam o giro. `ComicDialog` usa o `Dialog` da plataforma em paisagem e, em retrato, uma `ComponentDialog` com a mesma raiz girada. Ela implementa `DialogWindowProvider`, então o controle do celular continua alcançando o diálogo.
- **Preferência por aparelho.** `AppOrientationPreferences` fica fora do backup portátil, porque depende de como o projetor está montado.
- **Leitor.** Em retrato global, o botão "Tela: 0° · girar 90°" some e o giro próprio do leitor é ignorado, para não girar duas vezes.
- **Temas.** `ThemeSpec.forPreset(preset, portrait)` deriva a variante em retrato; as especificações de paisagem não mudam (`AppOrientationTest`).
- **Comic em retrato usa `BottomDock` só com ícones.** `maxLayout` em `LibraryScreen` passou a excluir esse dock, senão a Home do Comic esconderia a coleção como a do Max.
- **Max em retrato não usa a Home invertida.** O conteúdo segue de cima para baixo.
- **Celular.** Comando `rotate` percorre Paisagem → 90° → 270°. Topo do Controle: Zoom e Girar na mesma linha, sem o título. O Zoom vale no leitor e no Navegador; neste, o celular guarda o nível localmente, de 100% a 300%, porque a TV só recebe fator relativo (`zoomBy`) e não informa o valor de volta. Toque e rolagem remotos convertem o ponto de quem vê para a janela física.

## Verificações locais

Resultados da etapa final em `docs/validation/1.4.11.md`. Testes Android apenas compilados: sem ADB nem emulador.

## Não verificado

Nada foi executado na Box. Conferir lá:

- Foco e D-pad nos cinco temas, nos dois sentidos, incluindo entrada e saída dos menus.
- Abertura, Voltar e foco de retorno dos diálogos em retrato.
- Troca de orientação com o app aberto: nas Configurações e pelo celular, inclusive com diálogo aberto.
- Navegador: toque do cursor e páginas em retrato. Vídeo em tela cheia pode não girar, porque superfícies de vídeo nem sempre acompanham a rotação de View. Teclado do Android continua deitado.
- Zoom do celular no Navegador: o valor exibido pode divergir se a página limitar o zoom.
- Telas sem layout próprio em retrato (Catálogo, Extensões, Importar, Explorador, FTP) dependem das grades e rolagens existentes e podem ficar apertadas.
