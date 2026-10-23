# Validação — Comic TV 0.6.0

23/10/2026 · versionCode 8 · minSdk23 · targetSdk36.

## Resultado

101 testes JVM passaram, sem falhas ou erros: 69 core, 7 mihon e 25 app. Build debug, APK de instrumentação do app e lint passaram. Lint: 0 erros e 33 avisos, incluindo recomendações Android/KTX e versões de dependências. A instrumentação foi compilada, sem execução: adb não lista dispositivos e não há emulador configurado.

APK assinado com a mesma chave debug, verificado v1/v2. aapt2 confirma versão, SDKs e atividade Leanback.

SHA-256: `0C54E8D54CE1A45FA1B10980CCFB3584E3A0C656877F58FA1E9AFF2191FB2EB5`.

## FTP

12 testes com sockets reais cobrem autenticação, três falhas de senha, PASV/EPSV, bytes/nome preservados, raiz vazia, limite de tamanho, comandos/caminhos negados, um envio simultâneo, limite de quatro clientes, comando excessivo, bloqueio de conexão de dados de outro IP, interrupção, expiração durante envio, limpeza de temporários abandonados e erro sem exposição de dados internos.

O primeiro ciclo falhou pela ausência do servidor. O primeiro servidor revelou respostas de conclusão antes da limpeza; a ordem foi corrigida e os testes passaram. open/close agora compartilham a mesma trava para impedir reabertura acidental durante parada. Revisão da integração corrigiu a detecção de mudança de rede por identidade Android Network e endereço, em vez de somente IP.

Cliente independente curl.exe enviou um CBZ por PASV em loopback. O callback conferiu SHA-256 idêntico e o ComicArchive reconheceu uma página. Isso verifica protocolo e importador JVM; não substitui comunicação com a TV ou importação Room/decodificação Android em dispositivo.

O app cancela o job de importação e invalida a geração da sessão antes de fechar o servidor. Esse guard protege callbacks já admitidos pelo servidor no instante da parada. Arquivos incompletos não são publicados pelo fluxo de importação cancelado. A revisão independente final não encontrou problemas Critical/Important pendentes na integração.

## Teste físico pendente

Na TV: atualizar APK sem desinstalar; Importar → Receber por FTP → Iniciar servidor FTP. Em cliente FTP na mesma rede, usar endereço, porta, usuário e senha mostrados, modo passivo e envio direto para a raiz. Testar CBZ/RAR/PDF/EPUB, retorno à biblioteca, leitura, parada durante envio, segundo plano e troca de rede. Conferir legibilidade/foco/QR no dispositivo.

FTP convencional não criptografa dados; a tela orienta uso na rede local. Não oferece download da biblioteca, subpastas, exclusão, renomeação, append ou retomada de upload. A porta é atribuída pelo sistema e a senha muda por sessão. Portal remoto permanece pendente.

Os recursos da versão 0.5.0 continuam incluídos. A execução real de fontes Mihon/HQ Now ainda exige validação na TV; os relatórios anteriores estão no histórico Git e nas entregas de cada versão.
