# Downloads para leitura offline — desenho para aprovação

Base: Comic TV 0.11.0; executar após backup/restauração. Data: 24/10/2026.

## Objetivo e abordagem

O usuário autorizou download completo de edições online dentro do próprio Comic TV, com internet somente para obter as páginas e espaço local para guardá-las. Sem aplicativo externo, IA, conta ou serviço próprio.

Abordagem proposta: fila persistente, uma edição/página por vez, gerida em uma tela própria acessada pelos detalhes e pelo armazenamento. Essa primeira versão pausa ao sair dessa tela ou ir ao segundo plano. É uma escolha do implementador para não disputar o contexto das extensões com catálogo/leitor e manter o ciclo de vida simples; precisa estar clara na interface e na aprovação. Serviço em segundo plano contínuo fica para evolução futura, caso desejado.

## Fluxos propostos

- Detalhes de edição online já na biblioteca recebem **Baixar para ler offline**. Incluir no menu de OK longo da mesma edição. Arquivos locais importados já são locais e não recebem download redundante.
- A ação adiciona à fila e abre **Downloads**. Não marcar como lido nem alterar página, histórico oculto ou favorito ao enfileirar/baixar.
- Downloads mostra fila, edição atual, páginas concluídas/total, bytes, estado e mensagens. Comandos: Iniciar/Retomar, Pausar, remover da fila e abrir edição já concluída.
- Fila de até 100 edições, sem duplicatas, processada sequencialmente por ordem de entrada. Sem busca automática nem download de série inteira nesta primeira versão.
- Sair pausa e aguarda cancelamento das operações da extensão antes de voltar ao catálogo/leitor. Em segundo plano, pausar; ao voltar/reiniciar, mostrar pendências e exigir Retomar, sem reinício automático surpresa.
- Cancelar/remover trabalho parcial pede confirmação e apaga somente os arquivos parciais da edição indicada; excluir download completo também pede confirmação, mantendo a entrada na biblioteca, progresso, favorito e coleções.
- Mostrar **Disponível offline** somente quando todas as páginas estiverem salvas e verificadas. Uma edição parcialmente baixada não é apresentada como completa.
- Biblioteca/Histórico e detalhes mostram selo/estado discreto. Abrir uma edição concluída funciona sem internet e sem consultar a fonte ou reinstalar sua extensão. Ações de consultar outras edições continuam dependendo da fonte; não prometer catálogo offline.

## Armazenamento e isolamento

- Usar diretório permanente privado próprio de downloads, separado de cacheDir, páginas temporárias e originais importados. **Limpar cache** não toca nesse diretório.
- Manifesto versionado por edição: identidade e contagem, páginas locais, bytes e hashes/dimensões verificados, referência de título/capítulo. Escrever em área de preparação; publicar marcador de completo atomicamente só após todas as páginas.
- Partes interrompidas ficam disponíveis para retomar. Reobter a lista atual de páginas usando a extensão confiável, sem reutilizar cegamente URLs expiradas. Comparar uma identidade/fingerprint da lista; se ela mudou, reiniciar os parciais com aviso em vez de misturar sequências diferentes.
- Retomada confere arquivos existentes e integridade, baixa o que falta e descarta somente arquivos temporários inválidos. Não substituir download completo por nova tentativa parcial.
- Limites herdados: até 5.000 páginas, 32 MiB por imagem e 100 megapixels. Proposta: máximo 1 GiB por edição e reserva de 64 MiB de espaço livre, conferida antes e entre páginas; erro recuperável ao faltar espaço, sem promover pacote incompleto.
- Verificar o resultado real dos arquivos, sem depender somente de Content-Length. Cancelamento fecha resposta/recurso e aguarda o trabalho terminar antes de recarregar a extensão em outra tela.
- Rejeitar IDs/caminhos inválidos e links fora do diretório permitido. Nunca apagar cache global, originais, outras edições ou banco ao remover um download.
- Integrar categoria **Downloads offline** ao relatório de Armazenamento, com acesso ao gerenciador. Cache descartável e download permanente aparecem com nomes e comandos distintos.

## Integração técnica

- Adaptador do leitor escolhe páginas locais completas antes do repositório online. O caminho offline não chama confiança, load, pages, refresh ou download da extensão; só os fluxos online futuros fazem essas verificações.
- Preservar IDs da edição, Room/progresso e preferências existentes. Normal/Galeria/Livro, miniaturas e zoom por regiões usam os arquivos locais completos; as miniaturas podem ser recriadas offline após limpar o cache.
- Motor de fila/manifesto/arquivos testável com download falso em testes; adaptador de fonte real reutiliza os controles existentes de certificado, limite e fechamento HTTP. Não adicionar paralelismo por fonte.
- Coordenação de fila e navegação é explícita: entrada no gerenciador só após sair do leitor cancelando/aguardando operações; saída do gerenciador faz o mesmo. Corridas entre pause/remove/retomar são serializadas.
- Backup de dados inclui a referência à edição/progresso, mas não bytes nem selo de download completo. Restaurar backup em outra TV não marca uma edição como baixada sem seus arquivos.

## Testes e critérios de entrega

- Fila sem duplicatas, estados, pausa/retomada após reinício e cancelamento aguardado; falhas de rede/espaço/tamanho e manifestos incompletos/corrompidos.
- Não publicar completo com página ausente; comparação da lista de páginas na retomada; arquivo alterado e download completo protegido de tentativa parcial.
- Cache limpo sem remover download, remoção de edição sem perder progresso/favoritos, links/caminhos inválidos recusados e contagem real de bytes.
- Teste que abre download concluído com fake de fonte configurado para falhar em qualquer acesso à rede/extensão; teste de miniatura regenerada offline e preferências preservadas.
- TV: progresso legível de longe, foco/cancelar/retomar, sair e voltar, leitura com rede desligada, troca de modos, espaço e exclusão confirmada. Instrumentação e teste físico reportados separadamente de JVM/build.

## Fora desta versão

Execução contínua em segundo plano, agendamento, downloads automáticos de novas edições, fontes sem suporte ao mecanismo atual, sincronização de downloads e armazenamento externo configurável. Nenhuma dessas limitações impede a leitura local das edições que já estiverem completas.
