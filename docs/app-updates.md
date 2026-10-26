# Atualizações do Comic TV

## Na TV Box

Abra **Configurações → Atualizações → Verificar atualizações**. Se houver uma versão nova, leia as novidades e escolha **Baixar atualização**. A tela mostra a porcentagem e permite cancelar. Depois da conferência do arquivo, escolha **Instalar atualização** e confirme no instalador do Android.

Na primeira vez, o Android pode pedir autorização para o Comic TV instalar aplicativos. Depois de autorizar, volte ao app para continuar. Cancelar a confirmação não significa instalar: a versão realmente instalada é consultada no retorno.

A consulta automática acontece uma vez por sessão, com aviso passivo nas telas principais. Não inicia downloads nem toma o foco do controle remoto. O botão manual continua disponível. Ao colocar o app em segundo plano, consulta e download são cancelados; o APK já oferecido ao Android é preservado.

**A primeira instalação da versão 1.1.18 usa o método manual atual.** As versões anteriores não possuem esse atualizador. Atualize por cima, sem desinstalar, limpar dados ou remover o USB. É necessário espaço interno para baixar e para o Android preparar a instalação; armazenamento expandido não elimina essa necessidade.

## Publicação pelo desenvolvedor

1. Faça as alterações no repositório privado `wlmb091/comic-tv`.
2. Incremente `versionCode` e `versionName` em `app/build.gradle.kts` e crie `docs/releases/<versão>.md`.
3. Envie as alterações revisadas à branch `main`. O workflow **Build and publish Comic TV** testa, compila e confere a ABI antes da assinatura em outro runner.
4. A assinatura existente é conservada. Somente APK e `update.json` seguem para uma branch temporária do repositório público `wlmb091/comic-tv-updates`.
5. O workflow público verifica hash, tamanho, pacote, versão, assinatura e suporte ARM; cria uma release em rascunho, envia os dois arquivos e somente então publica como latest.

Enviar código sem incrementar a versão não publica outro APK sobre uma release existente. O aplicativo não cria novas versões sozinho: ele consulta o canal publicado. A automação do GitHub gera e distribui as versões a partir do código enviado.

O código do app permanece privado. Os arquivos ZIP de código gerados automaticamente no canal público contêm apenas a infraestrutura desse canal. A chave de publicação é exclusiva desse repositório; as credenciais de assinatura ficam nos secrets do repositório privado, sem tokens no APK.

## Limites e recuperação

- Manifesto até 64 KiB; APK até 64 MiB; HTTPS e destinos GitHub explícitos. SHA-256, pacote, versão, Android mínimo, processador e certificado instalado são conferidos antes do instalador, que também faz sua própria verificação.
- Arquivos ficam em `cache/updates`, separados dos originais, downloads de leitura e cache de páginas. Não entram no backup. Reserva de 64 MiB além do tamanho do APK antes de baixar.
- Se faltar espaço, libere itens por Armazenamento e tente novamente. Não é necessário apagar a biblioteca.
- Se a rede falhar, use Verificar novamente ou Baixar novamente. Falha de publicação mantém a release em rascunho; uma execução posterior pode completá-la. Releases publicadas não são sobrescritas.
- CI compila os testes Android; execução no aparelho e confirmação do sistema continuam sendo conferências físicas separadas.
