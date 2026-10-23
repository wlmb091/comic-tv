# Validação — Comic TV 0.7.0

23/10/2026 · versionCode9 · minSdk23 · targetSdk36.

## Continuidade online

OK no leitor online → Edições da saga / série. O leitor encerra e aguarda carregamentos/prévias antes de salvar e navegar; o botão de tentar novamente não reinicia operações depois desse encerramento. A fonte é consultada para uma lista atualizada fora do leitor ativo.

A tela destaca a edição atual, oferece anterior/próxima, seleção de qualquer edição e localização da atual. A sequência da fonte pode ser invertida explicitamente, com preferência salva por título. Duplicatas conservam a primeira entrada; capítulo ausente não recebe vizinhos inventados. Limite de 10.000 itens antes da deduplicação. Navegar pela lista não grava progresso; abrir edição já lida conserva sua página.

Back cancela uma abertura pendente para impedir navegação tardia durante a transição. Recriar a Activity não desativa permanentemente o ViewModel retido. Consulta do contexto mantém compatibilidade com registros de leitura anteriores, confere confiança/certificado e traduz falhas de API ausente.

## Verificação

112 testes JVM: 75 core, 7 mihon e 30 app, sem falhas ou erros. Incluem sequência, inversão, duplicatas, lacunas, limites, falha/repetição de abertura, seleção desconhecida, navegação após cancelamento e encerramento de carregamento antes de consultar outra fonte. Os testes de sequência e ViewModel foram escritos antes das respectivas implementações. A regressão de carregamento sobrevivendo ao fechamento falhou antes da correção.

Build debug, compilação de instrumentação do app e lint aprovados (0 erros e 34 avisos). A assinatura final v1/v2 e os SDKs/atividade Leanback são conferidos na entrega. A instrumentação não foi executada: sem TV/emulador conectado. Revisões independentes corrigiram transição com trabalho ainda ativo, cancelamento ao voltar e retenção do ViewModel durante recriação.

SHA-256 do APK: F5033675801CCA2167FB206EE749C9A516C16282127AA12B139441F47A21819B.

## Repositórios

O cadastro já existe desde 0.5.0: Fontes → Adicionar Keiyoushi ou informar URL → Ver extensões → Instalar → confirmar no Android → Atualizar instaladas → escolher fonte. Não instala todas as extensões de um repositório automaticamente. A versão atual inclui esse fluxo.

Keiyoushi e Suwayomi foram lidos pelo cliente real na verificação 0.5.0; HQ Now foi identificado no índice. Execução real de extensões, listas de sagas, D-pad, QR e passagem entre edições na TV continuam pendentes. APIs1.2–1.6 hospedadas não garantem compatibilidade universal; QuickJS indisponível.

## Teste na TV

Atualizar sem desinstalar. Instalar/abrir uma fonte; buscar saga/série e abrir uma edição. Avançar páginas, OK → Edições da saga / série, localizar atual e abrir a próxima. Reabrir a anterior e conferir página salva. Inverter sequência e conferir que a escolha persiste por título. Testar falha de rede, Back durante consulta e retomada pela biblioteca.

Portal web foi adiado pela prioridade de leitura online. Sua preparação local está separada do repositório ativo; não houve publicação, contratação ou alteração da hospedagem do Conchat. Rede local e FTP continuam disponíveis para importações ocasionais.
