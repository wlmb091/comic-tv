# Validação — Comic TV 0.2.0

Data: 23 de outubro de 2026. Incremento de transferência pela rede local.

## Resultados confirmados

- `:core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: BUILD SUCCESSFUL.
- 31 testes JVM, zero falhas e zero erros: 15 testes anteriores do núcleo, dez testes HTTP, cinco do ViewModel do leitor e um de cancelamento da importação.
- Android Lint: zero erros e 21 avisos de manutenção; não foi criada uma baseline para esconder resultados.
- APK debug 0.2.0, versionCode 2, minSdk 23, targetSdk 36.
- Assinaturas v1 e v2 verificadas com `apksigner`.
- Sintaxe do JavaScript embutido na página de envio verificada com `node --check`.
- Revisão independente e reavaliação das correções sem problemas relevantes remanescentes identificados.
- Nova montagem do APK após inclusão das licenças nos assets: BUILD SUCCESSFUL.

O build usou o JDK/SDK e os scripts locais descritos no relatório histórico `VALIDACAO-0.1.0.md`. Os repositórios Gradle do produto continuam sendo Google/Maven Central; o proxy local de desenvolvimento não integra o aplicativo.

## O que os testes HTTP exercitam

São conexões reais HTTP e sockets em loopback no computador, usando o servidor incluído no aplicativo. Cobrem página protegida por token, upload em streaming de 65.537 bytes com conteúdo exato, tamanho excessivo, recusa na validação, interrupção, nova tentativa, expiração, parada, proibição de reabrir sessão encerrada, nomes inseguros, Host/origem incorretos, limpeza de uploads abandonados e preservação de outros arquivos. Duas conexões verificam que o segundo upload recebe 409 enquanto o primeiro conclui. Um teste encerra o servidor com um upload ainda incompleto e confere sua remoção.

O teste adicional do repositório usa arquivos ZIP reais e um adaptador de capa controlado para cancelar a importação durante a geração da capa. Confirma que nenhum registro nem original é promovido após o cancelamento. Foi observado falhando antes da correção. A checagem de cancelamento acontece imediatamente antes do commit não cancelável no Room.

## Funcionamento implementado

Importar → Enviar pela rede local → Iniciar transferência. A TV resolve o IPv4 da conexão ativa Wi-Fi/Ethernet e abre o servidor naquela interface, com porta disponível e token aleatório. Mostra endereço completo, QR, estado e progresso. A página recebida pelo navegador envia vários arquivos sequencialmente. A validação e a persistência são as mesmas da importação local.

A sessão termina ao parar, sair da tela, colocar o aplicativo em segundo plano, mudar de endereço de rede ou expirar após 15 minutos. Novas sessões removem sobras `.upload` de sessões anteriores. Não há serviço de rede permanente nem listagem/download da biblioteca.

## Validação pendente em dispositivo

O adb iniciou nesta sessão, mas não havia dispositivos conectados nem emulador configurado. Os testes Android de UI/Room compilaram, sem execução em dispositivo. Ainda não foram confirmados em TV real: resolução de endereço por ConnectivityManager, leitura física do QR, envio de celular pela LAN, layout a distância, foco/restauração, ciclo de vida real e desempenho com arquivos grandes. Os testes HTTP no computador não substituem essas verificações.

Somente IPv4 em Wi-Fi/Ethernet está contemplado neste incremento. Redes de convidados, isolamento de clientes e restrições do roteador podem impedir acesso. O armazenamento precisa comportar o temporário e a cópia validada; o servidor verifica espaço antes de receber. A contagem de dispositivos deriva de requisições recentes do navegador e é atualizada com o tráfego da sessão.

FTP, portal web remoto e extensões Mihon continuam pendentes. Marcadores, galeria e outros formatos também permanecem nos incrementos planejados.

## Roteiro de teste na TV

1. Instalar Comic-TV-0.2.0-debug.apk sobre a versão anterior e conferir a biblioteca preservada.
2. Abrir Importar → Enviar pela rede local e iniciar a sessão.
3. Escanear o QR no celular conectado à mesma rede e enviar um CBZ/ZIP real.
4. Confirmar o estado de sucesso no navegador/TV e abrir a edição pela biblioteca.
5. Enviar vários arquivos, cancelar um envio, parar e abrir uma nova sessão; o endereço antigo deve parar de funcionar.
6. Repetir por Ethernet, com queda de rede, segundo plano e pouco espaço disponível.

## Arquivo entregue

Comic-TV-0.2.0-debug.apk

SHA-256: `1C885001444D9DE8472179A807AE62B6E16245D2FAAB7864A7CD24532818114B`.
