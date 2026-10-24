# Backup e restauração — desenho para aprovação

Base: Comic TV 0.11.0. Data: 24/10/2026.

## Objetivo e autorização

O usuário autorizou prosseguir com backup/restauração e downloads offline após escolher o pacote anterior. Foi proposta uma primeira versão integrada ao Comic TV, sem aplicativo externo, conta, servidor próprio ou serviço pago. O backup inclui os dados de leitura, com os arquivos dos quadrinhos separados para manter o arquivo pequeno. Este documento detalha o comportamento dessa primeira entrega; a segunda está em 2026-10-24-offline-downloads-design.md.

## Abordagens

1. **Arquivo local de dados — recomendada na conversa e aceita como direção.** Exportação/restauração manual pelo Comic TV e transferência opcional pela rede local. Sem infraestrutura externa.
2. Arquivo completo com todos os originais e downloads: exigiria muito mais espaço/tempo. Fora desta versão, conforme a proposta de separar arquivos e dados.
3. Conta/nuvem/sincronização: exige serviço e resolução contínua de conflitos. Fora desta primeira versão.

## Interface e fluxo propostos

- Integrar **Backup e restauração** às Configurações, mantendo temas, D-pad, texto ampliado e lateral atual. Usar faixa de seções rolável se necessário.
- **Criar backup** prepara um arquivo identificado por data e versão, com progresso de preparação e erro recuperável. **Salvar arquivo** usa o seletor Android quando disponível. Não solicitar acesso amplo ao armazenamento.
- Oferecer **Transferir pela rede local** para baixar o backup no navegador de celular/computador e enviar outro backup à TV. Sessão explícita, temporária, URL/token/QR; encerra ao sair, parar, mudar rede ou ir ao segundo plano. Reutilizar padrões HTTP do projeto sem tratar backup como CBZ ou disparar importação de quadrinhos.
- **Restaurar backup** permite escolher arquivo pelo seletor ou recebê-lo na sessão local. Primeiro validar e mostrar prévia: data/versão, quantidade de edições, favoritos, coleções e itens locais que precisarão ser reimportados.
- Nenhuma gravação da biblioteca ao apenas escolher/enviar arquivo. Confirmação com Cancelar inicialmente focado; texto informa que a biblioteca atual será preservada e os dados serão mesclados.
- Após restaurar, mostrar contagem de adicionados/atualizados/pendentes e erros reais. Bloquear comandos repetidos durante preparação/restauração. Operações de arquivo e banco fora da UI.

## Conteúdo do arquivo

- Formato próprio versionado de dados, identificador Comic TV e versão de esquema; JSON UTF-8 validado e limitado a 32 MiB, sem ZIP nem recursos executáveis.
- Edições, página/progresso, datas de leitura, histórico oculto, favorito, coleções/vínculos, ajuste/RTL por edição, filtros de imagem e aparência.
- Metadados mínimos das edições online: identidade da fonte/extensão, título, capítulo e certificado de referência. Não salvar URLs transitórias de páginas, cookies, senhas, tokens de sessão, APKs ou autorizações de confiança.
- Arquivos locais recebem impressão digital SHA-256 calculada por streaming. É usada para reconhecer o original reimportado, sem depender apenas de título/nome.
- Não incluir originais, páginas baixadas, caches ou capas. As telas devem dizer isso antes de exportar/restaurar. Guardar cópia fora da TV para proteger contra perda do aparelho.
- Repositórios/fontes continuam sujeitos à configuração e confiança locais; um backup nunca passa a confiar automaticamente numa extensão ou repositório.
- Não declarar compatibilidade com backups de Mihon ou outros aplicativos.

## Restauração e conflitos

- Usar transação Room para edições/coleções/vínculos; preparar/validar antes, sem restaurar um banco SQLite bruto sobre o banco aberto.
- Nunca remover edições, arquivos ou vínculos existentes por ausência no backup. IDs online são verificados contra seus metadados; IDs locais e referências são validados, sem usá-los como caminhos arbitrários.
- Para edição já existente: preservar o progresso/ajuste/estado do histórico com data de leitura mais recente; página sempre limitada ao número de páginas disponível. Empate conserva o estado atual. Favoritos e vínculos são unidos.
- Para coleções: reutilizar ID existente ou nome normalizado equivalente; não duplicar nomes nem apagar/renomear coleções locais por efeito do backup. Manter mapa de IDs para vínculos.
- Aparência é apresentada como opção restaurável na prévia, ativada por padrão. Preferências têm validação de valores conhecidos e escrita serializada. Preparar um registro de operação recuperável para que falha entre arquivos/preferências e transação não deixe dados silenciosamente inconsistentes.
- Edição online restaurada mantém descritor suficiente para atualizar páginas ao voltar a usar sua fonte, após confiança/instalação locais. Nunca executar extensão durante leitura/validação do backup.
- Se um original local já estiver presente, manter o arquivo. Caso falte, conservar metadados em índice de restauração pendente, sem oferecer leitura de uma entrada quebrada. Ao reimportar o mesmo arquivo SHA-256, recuperar sua identidade/progresso/favorito/coleções e remover a pendência após confirmação do banco. Importar arquivo diferente não herda esse progresso.
- Mostrar quantidade de pendências e instrução para reimportar em Backup e restauração. Criar novo backup inclui também essas pendências, evitando perdê-las antes da reimportação.
- Restauração idempotente: importar o mesmo backup novamente não duplica edições, coleções nem pendências.

## Arquitetura e limites

- Kotlin/Compose, Room, Gson, coroutines, SharedPreferences/AtomicFile e HTTP já usados pelo projeto; sem dependência de outro aplicativo além do navegador opcional para a sessão local.
- Subsistema de backup separado dos importadores de quadrinhos. Codec/regras de mesclagem testáveis em core; repositório coordena Room, descritores online, preferências e pendências com gravações temporárias/atômicas.
- Limites propostos: 20.000 edições, 2.000 coleções, 100.000 vínculos, 32 MiB de arquivo, título de até 300 caracteres, nomes de coleção de 1–40 caracteres. Validar tipos, versão, inteiros não negativos, duplicatas, referências, identidade e tamanho antes de aplicar.
- Preservar o mínimo Android 8/API26. Falha de espaço/permissão, interrupção e cancelamento não podem promover exportação incompleta como backup válido nem apagar a biblioteca atual.

## Verificação e entrega

- Testes para round-trip, versão futura recusada, arquivo corrompido/acima de limites, caminhos/IDs inválidos, tipos/valores inesperados, idempotência e conflitos por data.
- SQL/Room para rollback e preservação; originais ausentes, hash errado/correto, pendência persistida e restauro de pendência ao reimportar; falha entre armazenamento e commit com recuperação.
- Fluxos TV: cancelar prévia/confirmar, seletor ausente, envio/recebimento local, teclado/QR e foco. Testar o modo rede sem depender de outro aplicativo instalado na TV.
- Compilar APK/testes instrumentados, executar JVM, lint, assinatura/ABI e registrar claramente o que não foi executado fisicamente.

## Fora desta entrega

Backup automático, nuvem, sincronização, inclusão dos próprios arquivos, restauração de credenciais/autorizações e formatos de backup de terceiros. Nenhum dado real do usuário será restaurado/apagado durante o desenvolvimento.
