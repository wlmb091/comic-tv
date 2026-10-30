# Explorador local — 30/10/2026

## Entrega local

Entrada em Importar → Explorar arquivos · TV / USB. Origens Downloads concluídos do Comic TV (nomes originais, sem expor registros de sessão), arquivos da biblioteca com títulos reais e acesso direto a Ler agora, pastas TV/USB escolhidas pelo seletor Android. Permissão SAF de leitura persistente; nenhuma permissão MANAGE_EXTERNAL_STORAGE acrescentada. O Android pode restringir raízes/Downloads ou não disponibilizar seletor de pastas; nesse caso o app explica o limite e mantém Escolher arquivo/envio pela rede.

Pastas acima/Back, filtros Todos/Quadrinhos/Livros/APK, busca na pasta atual, diretórios primeiro; enumeração sem recursão limitada a 2.000 itens com aviso. Links simbólicos locais não são seguidos. Estados por pasta preservam consulta, filtro, índice/offset e foco durante navegação da sessão, limitados a 128 locais. Cabeçalho e lista rolam juntos; safe area 48/27dp e folga vertical24dp. Arquivos desconhecidos permanecem visíveis, mas não oferecem importação/instalação.

Importação usa LibraryViewModel/LibraryRepository existentes (progresso/cancelamento/duplicatas/coleções e limites mantidos). APK selecionado oferece instalação separada: cópia interrompível em cache privado, máximo1GiB/reserva64MiB, validação PackageManager, FileProvider restrito, autorização para fontes desconhecidas e confirmação do instalador Android. Não modifica confiança de extensões/atualizações nem bloqueio de APK no navegador. Sem exclusão de arquivos do usuário.

Pesquisa: APK X-plore4.45.03 fornecido, inventário REA+aapt2+stringsDEX, complementado por documentação oficial e fonte pública do Amaze3.11.4. Não houve decompilação pelo JADX, execução do alvo nem cópia de código/assets/chaves. Evidências em docs/research/2026-10-30-file-explorer-cloud.md.

## Verificação

- Teste RED inicial em build/explorer-red.log: classes de política ainda ausentes.
- Revisão independente explorer_review encontrou dois P2: campo de filtro perdia foco durante digitação e retorno não guardava índice/offset/consulta. Ajustados; regressões registradas em ExplorerLocationsTest e ExplorerNavigationTest. Estado da lista é restaurado depois do carregamento, evitando clamp durante lista vazia.
- RED de navegação em build/explorer-navigation-red.log; GREEN em build/explorer-final.log, BUILD SUCCESSFUL3m30.
- 4 testes core ExplorerFilesTest +2 JVM ExplorerLocationsTest: zero falhas/erros/pulados. Cobrem tipos/APK separado, confinamento de caminhos, ordenação/limite, posição por pasta e proteção de foco ao editar.
- compileReleaseKotlin e compileDebugAndroidTestKotlin aprovados. Um teste Compose de filtro/retorno compilado, NÃO executado.
- lintRelease:0erros/81avisos (sugestões KTX/espaço livre entre os avisos). git diff --check aprovado.

## Estado

Versão permanece1.4.4. Correção7z anterior preservada no mesmo lote aberto. Nenhum novo APK assinado, push, CI ou publicação. Nuvem e comunidade adiadas explicitamente. Ainda requer teste físico na Box: autorização TV/USB, D-pad/teclado/Back e scroll, importação de arquivo real e instalação/cancelamento Android. Compilação e testes JVM não comprovam execução na Box.
