# Backup, restauração e leitura offline — 0.12.0

## Backup de dados

Abra **Configurações → Backup e restauração → Criar backup**. O aplicativo prepara um arquivo `.ctvbackup`, identificado por versão/data, com edições, progresso/histórico, favoritos, coleções, ajustes de leitura, filtros de imagem e aparência.

Escolha **Salvar backup em arquivo** para usar o seletor Android. Se a TV não oferece seletor, use **Transferir pela rede local · QR**. Abra o endereço exibido no navegador de celular/computador na mesma rede e escolha Baixar backup preparado. Não precisa instalar aplicativo auxiliar na TV. Guarde uma cópia fora dela.

O arquivo não contém os próprios quadrinhos, downloads completos, caches, capas, APKs, cookies/senhas ou autorizações de confiança. Não é compatível com backups de Mihon. É um formato próprio de dados JSON UTF-8 versionado, com limite de 32 MiB, 20.000 edições, 2.000 coleções e 100.000 vínculos.

## Restauração

Use **Escolher backup para restaurar**, ou envie o arquivo pelo navegador da sessão local. Escolher/enviar somente abre a prévia; a TV mostra data e quantidades e exige **Confirmar restauração**. Cancelar inicialmente focado não grava dados.

A restauração mescla com a biblioteca atual: conserva o progresso de leitura mais recente, une favoritos e vínculos, reutiliza coleções por ID/nome normalizado e não exclui edições por ausência no arquivo. Empates conservam o estado atual. A aparência pode ser desmarcada na prévia. A mesclagem tem verificação de limites antes de gravar e registro recuperável para concluir uma aplicação interrompida no próximo início.

Desde 0.12.1, ao terminar é exibido um diálogo com as contagens e o botão **Entendi**. Falhas mostram **Restauração não concluída**, permitindo reconhecer o erro e tentar novamente; a confirmação não depende de rolar a tela até uma mensagem.

Originais locais já presentes são conferidos por SHA-256. Se faltarem, os metadados ficam pendentes, sem uma capa quebrada oferecendo leitura impossível. Reimporte **o mesmo arquivo original** para recuperar identidade/progresso/coleções; renomear o arquivo não altera sua impressão digital, mas recompactar/editar os bytes pode alterá-la. A contagem de pendências aparece nas Configurações e continua incluída em novos backups.

Edições online restauradas precisam da fonte/extensão configurada e confiável no aparelho para voltar a buscar páginas. O backup não concede confiança automaticamente. Restaurar os metadados em outra TV não recria os arquivos baixados nem anuncia disponibilidade offline sem eles.

## Sessão de rede local

A sessão tem URL/token/QR próprios, dura até 15 minutos e termina ao encerrar, sair, trocar de rede ou ir ao segundo plano. Aceita um envio por vez, de até 32 MiB. Host/Origin/token e formato são conferidos; backups recebidos nunca são aplicados pelo navegador sem confirmação na TV. A sessão é HTTP na rede local; não há portal remoto ou conta/nuvem.

## Downloads completos

Depois de abrir uma edição online na biblioteca, use **Baixar para ler offline** nos detalhes ou no menu de segurar OK por 1 segundo. A tela Downloads gerencia uma fila persistente de até 100 edições, uma edição/página por vez. Mostra páginas/bytes, pausa, retomada e remoção confirmada. Também fica em **Configurações → Armazenamento → Gerenciar downloads offline**.

O download pausa ao sair dessa tela ou ir ao segundo plano. Use **Iniciar / Retomar fila** para continuar; voltar ao aplicativo não reinicia sozinho uma fila pausada. Partes válidas são reaproveitadas quando a lista atual da fonte corresponde à tentativa anterior. Se a lista mudar, o aplicativo informa e reinicia os parciais, sem misturar sequências. Certas fontes usam URLs transitórias e podem exigir esse reinício.

Somente depois de salvar e verificar todas as páginas aparece **Disponível offline**. Abertura de edição completa verifica integridade em IO e usa os arquivos locais; não carrega extensão, confiança ou rede. Miniaturas podem ser recriadas offline depois de limpar o cache. Catálogo, outras edições e novos downloads ainda dependem da fonte. Pacotes alterados não são anunciados como completos; retomar pode recuperar suas páginas verificadas.

Limites: 5.000 páginas, 32 MiB por imagem, 100 megapixels e 1 GiB por edição. A reserva é de 64 MiB após gravar; iniciar uma página exige 96 MiB livres para comportar a imagem máxima. Falha de rede/espaço/formato deixa o trabalho parcial, nunca um pacote completo fictício.

Os arquivos permanentes estão separados do cache. **Limpar cache** não remove downloads; **Remover download** apaga somente os arquivos daquela edição e mantém biblioteca/progresso/favoritos/coleções. Armazenamento mostra downloads completos e parciais em categoria própria.

## Validação e evolução

Não foram adicionados aplicativos auxiliares, servidor remoto, IA ou novas dependências. Não há download automático/agendado, serviço contínuo em segundo plano, backup automático, nuvem/sincronização ou armazenamento externo configurável nesta etapa.

Testes JVM usam fontes falsas, arquivos temporários e HTTP local para verificar limites, fila, pausa/retomada, integridade, cache separado e ausência de acesso à fonte no leitor offline. Room, restauração interrompida, seletor Android e navegação real precisam do aparelho; testes instrumentados compilados não equivalem à execução. Consulte VALIDACAO.md.
