# Capítulos com lido e não lido no celular (02/11/2026)

Status: **IMPLEMENTADO NA 1.4.21 (código 95), AUTORIZADO PELO USUÁRIO EM 02/11/2026; Box e iPhone PENDENTES.**

## Pedido
Informações que hoje só aparecem na TV, como escolher capítulos e ver os lidos e não lidos, também devem aparecer no celular.

## Relato da foto (1.4.20)
Com Sincronizado ligado, o painel da seta direita mostra "Abra esta edição na TV para usar as opções.", enquanto a TV deveria estar nos capítulos.

## O que existe hoje, pela leitura do código da 1.4.20
Não reproduzido em aparelho.

- `Capítulos / edições` executa `viewModel.close(...)` e abre `online-editions` na TV (`ComicTvApp.kt:375`). O leitor da TV fecha, então `tvReader.active` fica falso. A mensagem acima é a resposta do painel a esse estado (`renderReaderMenu`, `mirrorOn`). É a causa provável da foto.
- O painel não tem endpoint para capítulos. `/library` traz só as edições já na biblioteca.
- O estado Não lido, Em andamento e Lido é calculado na TV em `details/DetailsChapterPolicy.kt`, a partir de `lastRead`, `currentPage` e `pageCount`.

## Direção proposta (a confirmar)
- Novo endpoint com os capítulos da edição, usando a mesma regra de estado da TV.
- O ícone de Capítulos abre a lista no celular, com o estado de cada um, sem tirar o leitor da TV.
- Tocar num capítulo abre na TV e no celular, se sincronizado.

## Pontos a verificar ao implementar
- Lista vinda de fonte online pode demorar e exige a extensão instalada na TV.
- Só edições online têm capítulos. Importados não têm essa opção hoje.
