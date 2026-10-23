# Incremento 0.2.0 — transferência na rede local

Implementa a etapa Transferência A do plano aprovado. A TV abre uma sessão HTTP temporária apenas no endereço IPv4 da rede ativa (Wi-Fi ou Ethernet). A tela mostra URL, QR, status, envios em andamento e progresso. Não requer internet nem abre portas no roteador.

O endereço inclui um token aleatório de 192 bits, válido por 15 minutos. Parar, sair da tela ou colocar o aplicativo em segundo plano encerra a sessão e cancela conexões em andamento. Uma nova sessão recebe outro token. Uma rede com isolamento de clientes pode impedir acesso, mesmo quando ambos os dispositivos têm Wi-Fi.

A página de envio é servida pela própria TV, sem serviços ou scripts externos. Permite escolher vários CBZ/ZIP e enviá-los sequencialmente. Cada POST contém apenas os bytes de um arquivo, com tamanho declarado, limite de 1 GiB e gravação por streaming; multipart e transferências sem tamanho não são aceitos nesta versão. O nome original é metadado e nunca determina um caminho em disco.

Um envio por vez. Arquivos incompletos ou inválidos são removidos; apenas após recebimento completo passam ao importador compartilhado (índice ZIP, limites, capa e Room). Não há listagem, download ou exclusão dos arquivos da biblioteca pela rede. Acesso exige a URL/token, Host correto e origem correspondente nas requisições de envio. A sessão acompanha o ciclo de vida da tela, sem serviço em segundo plano.

Testes JVM devem abrir conexões HTTP reais em loopback e cobrir token inválido, tamanho excessivo, envio completo, validação recusada, interrupção, expiração e encerramento. Build, lint e testes Android compilados são verificados separadamente de leitura de QR e envio em TV real.

FTP, portal remoto por token e motor Mihon continuam em incrementos próprios.
