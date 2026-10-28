# Inspeção delimitada de código público para TV

Data: 28/10/2026. Leitura de fontes públicas pelo conector GitHub, sem instalar, compilar ou executar projetos de terceiros. Nenhum código, dependência, credencial ou recurso visual desses projetos foi incorporado ao Comic TV. Os números de duração abaixo são parâmetros encontrados no fonte; não são tempos medidos em uma TV.

## FlixQuest: vínculo com o APK fornecido

O [site oficial](https://flix.quest/) aponta para a [release v4.3.0](https://github.com/flixquest/flixquest/releases/tag/v4.3.0). O [registro da tag](https://api.github.com/repos/flixquest/flixquest/git/ref/tags/v4.3.0) resolve para o commit `fb85c50134a9a485116dbf7710279887a86d83bc`.

A [API pública dessa release](https://api.github.com/repos/flixquest/flixquest/releases/tags/v4.3.0) informa para o asset `app-armeabi-v7a-prod-release.apk`:

- Tamanho: 36.808.723 bytes.
- Digest: `sha256:320ed9cb0bec19e6a2b620ebf92efeb2a410e7b8c41bc439a1beb7e4ba87b174`.

São exatamente o tamanho e o hash registrados para o APK fornecido no [estudo estático anterior](2026-10-27-flixquest-backdrops.md). O [pubspec fixado no commit](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/pubspec.yaml) declara `4.3.0+10`, compatível também com os metadados Android anteriores. Isso vincula os bytes do arquivo local ao asset publicado nessa release. Não prova reprodução independente do binário a partir do fonte, nem execução dos caminhos examinados. Não houve novo download do APK ou decompilação nesta etapa. A [licença do projeto](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/LICENSE) é GPLv3; esta pesquisa registra observações, sem importar implementação.

### Menu e separação entre foco e seleção

Fonte: [`lib/tv/widgets/tv_navigation_rail.dart`](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/lib/tv/widgets/tv_navigation_rail.dart), especialmente linhas 23–27, 122–126 e 210–287.

Observado: a coluna não desenha fundo próprio. A largura muda quando expandida; nomes aparecem sem quebra de linha. O item com foco recebe preenchimento forte, enquanto a seleção persistente sem foco é indicada por uma barra estreita. O menu não aumenta a escala do item. A largura usa 220 ms/easeOutCubic, os rótulos usam 180 ms e o preenchimento usa 150 ms. São parâmetros declarados, sem observação de quadros reais.

Fonte: [`lib/tv/app/tv_shell_layout.dart`](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/lib/tv/app/tv_shell_layout.dart), linhas 74–100, 139–143, 178–229 e 281–340.

Observado: conteúdo e menu têm escopos de foco distintos e passagem explícita. Direita entra na página selecionada; confirmar outra seção solicita entrada após a montagem, condicionada à seleção e à permanência do foco de origem. Esquerda tenta primeiro navegar no conteúdo e, sem destino, volta ao menu. O fundo de escurecimento é separado dos controles, com alfas 1/0,97/0,6/0,45 nas posições 0/0,27/0,5/1; aparece em 220 ms. A troca de página usa fade de 240 ms na página entrante e exclui a anterior da interação/foco durante a transição.

### Arte, cartões e movimento

Fonte: [`lib/tv/widgets/tv_spotlight.dart`](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/lib/tv/widgets/tv_spotlight.dart), linhas 26–149 e 248.

Observado: o fundo prefere backdrop e usa poster como fallback, com recorte proporcional e alinhamento superior direito. Há degradês independentes horizontal e vertical. Esse componente monta URL com variante w1280 e limita a largura de decodificação a 1280. Guarda até dois caminhos e espera estabilização antes da troca; o padrão é 350 ms, com uso específico de 0/250 ms. O fade de entrada é 450 ms. Isso acrescenta evidência de fonte ao estudo anterior de strings; não comprova resolução baixada ou comportamento de todas as telas/configurações do APK.

Fonte: [`lib/tv/widgets/tv_media_card.dart`](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/lib/tv/widgets/tv_media_card.dart), linhas 43–124; [`lib/tv/focus/tv_focusable.dart`](https://github.com/flixquest/flixquest/blob/fb85c50134a9a485116dbf7710279887a86d83bc/lib/tv/focus/tv_focusable.dart), linhas 18 e 157–170.

Observado: cartões verticais têm proporção 2:3, imagem recortada proporcionalmente e dimensões de decodificação relacionadas à área exibida. O escurecimento é uma superfície sobre a imagem com transição de 200 ms. O componente comum de foco tem escala padrão 1,04, duração 150 ms/easeOut e contorno de 2; os chamadores podem substituir esses valores, como faz o menu.

## Amazon: amostra oficial Fire TV

Repositório: [AmazonAppDev/hello-world-fire-tv](https://github.com/AmazonAppDev/hello-world-fire-tv). O [ref main consultado](https://api.github.com/repos/AmazonAppDev/hello-world-fire-tv/git/ref/heads/main) resolve para `50108f4fc4a9dc054be86d851286355df027524a`. É uma amostra oficial de desenvolvimento Fire TV com licença MIT-0, não o fonte do Prime Video nem evidência do seu APK proprietário.

Fonte: [`app/src/main/java/com/amazonappstore/helloworldtv/HomeDrawer.kt`](https://github.com/AmazonAppDev/hello-world-fire-tv/blob/50108f4fc4a9dc054be86d851286355df027524a/app/src/main/java/com/amazonappstore/helloworldtv/HomeDrawer.kt).

Observado: usa ModalNavigationDrawer de TV Material, inicialmente fechado, com scrim em degradê horizontal da superfície a transparente. O conteúdo reserva 80 dp à esquerda. A seleção usa cor de superfície com alfa 0,5; o clique fecha o drawer antes de chamar a seleção. O fonte desse arquivo não fornece uma sequência própria de tempos de animação nem um contrato completo de restauração de foco.

Fonte: [`app/src/main/java/com/amazonappstore/helloworldtv/MainActivity.kt`](https://github.com/AmazonAppDev/hello-world-fire-tv/blob/50108f4fc4a9dc054be86d851286355df027524a/app/src/main/java/com/amazonappstore/helloworldtv/MainActivity.kt).

Observado: grade de cinco colunas com cartões 16:9, borda de foco de 3 dp e escala focada igual a 1. A animação modifica somente o alfa da superfície de degradê (0,2→0,6), deixando o texto em outra camada. O chamador não especifica duração para animateFloatAsState; não foi inferido um tempo a partir da biblioteca atual. Os itens e rótulos são demonstração, sem dados reais do Prime Video.

## Aplicação à composição aprovada do Comic TV

As [cinco prévias aprovadas](../design/themes/previews-1.2.2/README.md) e os complementos do usuário continuam determinando posições, identidade e controles. A pesquisa ajuda a explicar mecanismos, sem substituir as imagens aprovadas por menus de outros produtos.

- Flix: os fontes corroboram coluna sobre arte sem caixa própria e escurecimento separado atrás dos nomes. O degradê final aprovado pelo usuário pode terminar transparente; não há obrigação de reproduzir o alfa residual 0,45 do outro aplicativo.
- Todos os temas: foco real precisa continuar mais forte que seleção persistente; entrada/saída explícita e guardas contra resposta tardia são mecanismos coerentes com o contrato já aprovado. Transparência pertence à superfície; texto/ícones não recebem alfa global. Foco e alto contraste permanecem opacos.
- Prime mantém a navegação superior e marca à direita da imagem aprovada; a amostra Amazon não autoriza deslocar esse menu para uma lateral. Cinema mantém drawer à direita e saída por esquerda/Voltar; Max mantém dock inferior com Buscar. Comic TV original permanece estante/fundo sólido.
- Os tempos declarados no FlixQuest são referência estática, não medição ou garantia de fluidez. Não foi autorizado novo serviço de imagens, mudança de qualidade, cache ou dependência por esta leitura. A composição nativa usa dados/capas reais, arte manual/fallback existente e preserva originais.

Não foram obtidos, decompilados ou executados APKs Netflix, Prime Video ou HBO Max nesta inspeção. O HTML público/documentação primária previamente consultados estão em [Netflix/Max](2026-10-28-streaming-netflix-max.md) e [Prime/TV](2026-10-28-streaming-prime-tv.md). Fonte público, build e testes compilados não substituem conferência visual e D-pad física na Box.
