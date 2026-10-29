# Capas automáticas e escolha da página — pacote aberto

## Automático

Ao abrir Detalhes ou Leitor de edição online, uma tarefa opcional da tela procura a capa principal ausente pela identidade da fonte/obra salva. Carrega a extensão instalada/autorizada correspondente, sem alterar a fonte selecionada. Usa o cliente e headers/cookies da fonte; nunca usa primeira página do capítulo para a capa automática.

Se a URL antiga não existe/falha, atualiza metadados da obra e tenta a nova capa. Não solicita páginas de capítulos. Extensões podem retornar também listas de capítulos como metadados em seu método de detalhes. Veja a pesquisa pontual HQ Now em `docs/research/2026-10-29-hqnow-title-cover.md`: `hqCover` vem de API GraphQL própria e pode usar HTTP. O parser valida e converte para HTTPS; HEAD HTTPS da capa Saga do Infinito respondeu200.

Cache automático separado:8MiB/imagem,64MiB total,64MiB de reserva, validação de imagem estática até48MP. Chegar ao limite recusa novas cópias sem apagar as existentes; limpar/evicção desse cache pode exigir novo download. Transferências são sequenciais e coalescidas por saga, com cooldown5min após falha. Cancelamento ao sair/segundo plano não impõe cooldown. A transferência Detalhes→Leitor aguarda a limpeza do dono cancelado e assume trabalho pendente. Não bloqueia o leitor nem muda progresso/foco. Ao concluir, sagaRevision atualiza a coleção com arquivo local.

Escolhas manuais são verificadas antes e depois do download: uma imagem escolhida durante a transferência vence o automático. Arquivos importados sem fonte continuam dependendo de escolha manual.

## Página escolhida no leitor

No leitor, navegue até a página desejada (inclusive1), abra **Imagem e mais opções → Usar página atual como capa da saga**. Só pode salvar quando a imagem realmente foi exibida e não está carregando/com erro; texto EPUB não oferece essa opção.

A cópia vem do arquivo original materializado, não de screenshot, recorte do zoom ou bitmap filtrado. Usa o armazenamento manual já existente: JPEG/PNG/WebP estático,20MiB/48MP,128MiB no conjunto. A validação mantém a capa anterior se falhar. A identidade/associação da saga é conferida novamente após preparar a imagem. Originais, capa da edição, progresso e direção/ordem do controle permanecem intactos.

O botão permanece montado durante a gravação, impede envio duplicado, mostra resultado e não fecha o menu. A tarefa é cancelada ao sair/segundo plano. Escolha de imagem por arquivo, capa da obra e capa explícita de edição continuam disponíveis na saga. A prioridade manual impede substituição automática posterior.

Imagens/associações manuais continuam fora do backup portátil, conforme limite já documentado. O cache automático também não é backup.

## Verificação e publicação

Builds automáticos aprovados: `auto-saga-cover.log`2m27s; `auto-saga-cover-final.log`1m31s; `auto-saga-cover-hqnow.log`2m03s. Incluem testes de coalescência, transição com cancelamento sobreposto, cooldown, isolamento de fontes/esperas e normalização URL, mais agrupamento.

Teste `SagaPageCoverTest` cobre imagem exibida/carregando/erro/texto/ausente. Teste nativo `SagaPageCoverMenuTest` cobre alvo mantido durante gravação, clique duplicado e bloqueio antes da imagem pronta. Execução física depende da Box; compilação não equivale a validação do controle.

Usuário reforçou: **não gerar/publicar APK**, avisar quando terminar para receber mais alterações. Sem incremento de versão, push ou CI neste recorte.

Verificação final: build/saga-page-cover.log SUCCESS1m41s; testesJVMTitleCoverUrl/SagaPageCover/SagaCoverCoordinator/SagaGrouping passaram; AndroidTestcompilado,nãoexecutado. diff--checklimpo. Versão permanece1.4.0/code74.
