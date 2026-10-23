# Receber por FTP

Disponível na versão 0.6.0, em Importar → Receber por FTP. O servidor roda temporariamente na TV e recebe arquivos de um cliente na mesma rede Wi-Fi/Ethernet. Não é cliente de NAS nem portal na internet.

## Conectar

Inicie a sessão na TV. No cliente, escolha protocolo FTP convencional, endereço/porta mostrados, usuário comictv e a senha temporária da tela. Use modo passivo. O QR inclui usuário e senha numa URL FTP; câmeras/navegadores sem suporte a FTP podem não abrir essa URL, então os mesmos dados ficam visíveis para digitar no cliente.

Envie arquivos diretamente para a raiz. CBZ/ZIP, CBR/RAR, PDF e EPUB passam pelo mesmo importador usado por arquivo local e rede HTTP. Upload concluído só recebe confirmação de sucesso após validação/importação. A listagem do FTP é vazia, pois o servidor recebe arquivos e não compartilha a biblioteca. Não use opções do cliente de retomar envio, renomear arquivo temporário ou criar subpastas.

## Sessão e limites

Sessão de até 15 minutos; encerra ao parar, sair da tela, colocar o app em segundo plano ou mudar de rede. Nova sessão tem nova senha e porta atribuída pelo sistema. Até quatro conexões de controle e um envio simultâneo, com tamanho máximo de 1 GiB por arquivo. Como FTP não informa necessariamente o tamanho antes do envio, a tela mostra bytes recebidos em vez de porcentagem estimada.

Comandos, nomes, tempos de espera, tamanho e espaço livre são limitados. A conexão de dados precisa vir do mesmo IP da conexão de controle. Apenas recebimento passivo; sem download, exclusão, renomeação, append, retomada ou caminhos de diretórios. Temporários usam identificadores internos e são removidos após erro, interrupção ou importação. A biblioteca conserva as cópias já importadas.

FTP convencional não possui criptografia. Use somente na sua rede local. Redes de convidados e isolamento de clientes podem impedir conexão; o modo não requer encaminhamento de portas no roteador.

## Implementação

Servidor específico de recebimento em Kotlin/JVM, usando Socket/ServerSocket disponíveis no Android23, sem adicionar um servidor FTP genérico com acesso a diretórios. Essa decisão substitui a previsão inicial de selecionar uma biblioteca FTP de terceiros: reduz operações expostas e dependências, ao custo de suportar somente o conjunto de comandos documentado. Referências do protocolo: [FTP/RFC959](https://www.rfc-editor.org/info/rfc959/) e [EPSV/RFC2428](https://www.rfc-editor.org/info/rfc2428/).

Testes automatizados usam sockets reais em loopback; a entrega inclui também validação com cliente FTP independente. Ciclo de vida, QR, D-pad e comunicação cliente→TV precisam de teste físico. Consulte VALIDACAO.md para a evidência final da versão.
