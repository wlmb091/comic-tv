# Descompactador/conversor e painel no celular — 30/10/2026

## Escopo autorizado e entrega

Usuário aprovou descompactador/conversor e painel completo no celular; optou por painel próprio, visualização da TV, leitura independente e transporte de arquivos. Confirmou que usa iPhone e aprovou implementação web no Safari, sem aplicativo Android separado. Referência visual aprovada em docs/design/companion/approved-layout.png. Nuvem/comunidade, editor de capas, montador CBZ e bibliotecas externas permanecem futuros; compatibilidade de vídeo do navegador também adiada.

### Arquivos

No explorador, selecionar ZIP/CBZ/RAR/CBR/7z/CB7 → Conteúdo · Extrair · Converter CBZ. Índice geral validado, extração em pasta nova e conversão só das páginas de imagem em ordem natural. Bytes JPEG/PNG/WebP copiados sem re-encode. PDF/EPUB não são convertidos para imagens. CRC de cada entrada ZIP/RAR/7z preservado/verificado, caminhos confinados, originais intactos. Resultado em Explorar arquivos → Arquivos processados; CBZ também pode ser salvo em destino SAF. Operações interrompíveis, limites existentes e reserva64MiB por bloco durante staging. Não executa arquivos nem instala APK descompactado automaticamente.

### Celular

Entrada em Importar → Conectar celular ou Navegador → Opções → Conectar celular. Sessão QR com endereço HTTP local, token aleatório192bits, validade30min, encerramento manual/segundo plano/mudança da identidade Network ou endereçoIP. Sem credenciais fixas e sem servidor na internet. QR dá acesso à biblioteca/tela durante sessão: aviso na TV orienta rede confiável e não compartilhar endereço.

Painel responsivo com Biblioteca/Leitor/Controle/Arquivos. Biblioteca mostra edições e capas reais, busca e Continuar lendo. Leitor independente com páginas/imagens/EPUBtexto e progresso de navegação salvo apenas na sessão do navegador do celular; NÃO chama saveProgress da TV. Abrir na TV é ação explícita. Fontes online usam os repositórios/fontes autorizados existentes e podem exigir rede; não há nova fonte/API. Originais importados podem ser baixados no celular com formato detectado pelos bytes; originais online não são apresentados como arquivos locais.

Controle: D-pad,OK,Voltar,Menu,Enter,texto no campo focado, toque/rolagem na prévia. Eventos limitados à Activity ativa do ComicTV, sem volume/energia/comandosAndroid externos. Espelho via PixelCopy640px/JPEG65 com cache1,2s e atualização opcional1,8s, desligada inicialmente; não é transmissão de vídeo e conteúdo protegido pode ficar preto. Sem alteração das políticas de mídia/cookies do navegador.

Uploads por XHR com progresso real do envio, importação pelo fluxo existente,1transferência por vez/máximo1GiB/64MiBreserva; nomes/formatos limitados, APK explícito/disfarçado recusado. Cancelar envio interrompe a conexão; após envio concluído, importação pode já ter terminado (interface orienta conferir biblioteca antes de reenviar). Não remove arquivo do celular. Servidor validaHost/Origin/token/corpos, fecha conexão para não deixar corpo rejeitado contaminar próxima requisição, limita comandos30/s e8handlers/2tarefas de recursos.

## Revisões e correções

Revisão independente archive_tools_review encontrou P2 de CRC ao converter ZIP e staging sem reserva por bloco. Corrigidos; testes adicionados. Revisão companion_review encontrou P2 de listener ativo após cancelamento da inicialização, troca de rede com mesmoIP e toque deslocado em object-fitcontain. Corrigidos com ownership/finally, servidor não reabrível após close, comparaçãoNetwork e mapeamento do conteúdo efetivo da imagem. TimeoutPixelCopy também não recicla bitmap antes do callback pendente.

Testes reaisHTTP identificaram corpo rejeitado de APK contaminando requisição seguinte no keep-alive; corrigido Connection:close e regressão aprovada. Lint encontrou RestrictedApi ao despachar KeyEvent pela ComponentActivity; alterado para API pública de decorView, mantendo Back pelo dispatcher.

## Verificação

- TDD: build/archive-tools-red.log, archive-tools-review-red.log, companion-red.log. Registro inicial companion-green2/probe/green3 contém falhas de integração diagnosticadas e corrigidas; não são evidência final.
- build/archive-companion-final.log:43JVMcore +7JVMapp =50 testes,zero falhas/erros. Inclui arquivos,collections,7z,CP437,explorer,HTTPuploads e CompanionServer (token/host/origin/expiry/commands/body/APKdisfarçado/cleanup/close).
- Esse build inicialmente falhou só no lintRestrictedApi. Verificação final fresh em build/companion-android-final.log:BUILD SUCCESSFUL3m11,ReleaseKotlin/AndroidTestKotlin/lint aprovados;0erros/86avisos. Nenhum testeAndroid executado fisicamente.
- Arquivo real RqurVrdBslt (66.038.065bytes,7z com extensãoCBR) convertido no JVM:36páginas/67.755.755bytes de imagens,CRCsaída conferido eSHA256original inalterado. Resultado temporário removido. Log build/archive-tools-real.log. Nenhum quadrinho original/imagem real versionado.
- Interface: Edgeheadless em390x844 e844x390 com biblioteca/screen/upload simulados em loopback. Verificados largura sem overflow,24cards+Vermais,leitorpróxima/posiçãoindependente,comandos/texto,uploadXHR,32downloads locais listados e toque central0,5/0,5 ignorando barras em prévia ampliada. JS passou node--check. Relatório e capturas docs/result/companion/. NÃO são capturas do Android/Safari/Box nem prova de compatibilidade física.
- git diff --check aprovado. Sem nova dependência neste recorte, semAPKassinado/push/CI/publicação.

## Pendências físicas e estado

Versão continua1.4.4; pacote aberto, inclui7z e explorador anteriores. Antes de afirmar validação física: usuário deve conferir QR/Safari iPhone, leitura local e online, download/importação, controle do campoCompose/WebView, teclas ePixelCopy naBox. Workflows certificados/programas externos continuam sob confirmação Android. Nenhuma garantia de100%de compatibilidade ou ganho medido naBox. Aguardar novas instruções do usuário, sem publicar o pacote.
