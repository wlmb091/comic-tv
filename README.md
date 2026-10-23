# Comic TV

Leitor nativo para Android TV e Google TV, em português e operado pelo controle remoto.

## Versão 0.2.1

- Biblioteca local com capas e lista de leituras recentes.
- Importação de CBZ/ZIP pelo seletor de arquivos do Android, incluindo USB quando oferecido pelo dispositivo.
- Páginas JPEG, PNG e WebP, ordenadas naturalmente.
- Leitor com ajuste à página, largura ou altura, zoom de 1x a 3x e deslocamento explícito.
- Progresso e preferências por edição em Room.
- Tema escuro com foco âmbar, seguindo o conceito aprovado.
- Transferência pela rede local (Wi-Fi/Ethernet), com URL temporária, QR code, upload pelo navegador e acompanhamento na TV.

O projeto está em desenvolvimento. Consulte `VALIDACAO.md` para resultados reais de compilação e testes; a existência do código não significa validação em uma TV.

Especificação, plano, referências funcionais e conceito visual aprovado estão em `docs/`.

## Compilar

Requisitos: JDK 17, SDK Android Platform 36, Build Tools 35.0.0 e conexão para resolver dependências. Gradle 8.13 é fornecido pelo wrapper; AGP 8.11.1, Kotlin 2.2.0 e demais versões estão fixados nos arquivos Gradle. `minSdk` é 23.

Abra a pasta no Android Studio ou configure `JAVA_HOME` e `ANDROID_HOME`. Alternativamente, crie um `local.properties` com `sdk.dir=C\:/caminho/do/Android/Sdk`. Essa configuração local não deve entrar no Git.

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug
```

O APK debug é produzido em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar em um dispositivo já conectado:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Testes que exigem emulador ou dispositivo Android:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Controle remoto

| Contexto | Comando |
|---|---|
| Biblioteca e menus | Setas movem o foco; OK seleciona |
| Leitor | Esquerda/direita trocam páginas, respeitando a direção configurada |
| Leitor | Cima/baixo aumentam/reduzem zoom em 25%, de 100% a 300% |
| Leitor | OK abre ferramentas |
| Ferramentas | Ajuste alterna página inteira, largura e altura |
| Mover página | As quatro setas deslocam a página; Voltar retorna às ferramentas |
| Ferramentas | Voltar retorna à leitura |
| Leitura normal | Voltar salva e retorna à biblioteca |

Ao mudar de página, zoom e deslocamento voltam ao estado inicial. O zoom desta versão amplia uma imagem decodificada para a tela; alta resolução por tiles faz parte de um incremento futuro.

## Importação e armazenamento

O arquivo original é copiado para o armazenamento privado, sem depender permanentemente da URI externa. Arquivos temporários são validados antes de entrar na biblioteca. Originais são conservados; o cache de páginas é descartável.

Limites: 1 GiB por arquivo, 5.000 entradas ZIP, 32 MiB por página extraída, 2 GiB de tamanho declarado de imagens, 100 megapixels por imagem e 256 MiB de cache de páginas. ZIP criptografado, corrompido ou sem imagens suportadas produz erro. Apenas a primeira página é decodificada durante a importação; erros em páginas posteriores são apresentados no leitor.

A seleção de arquivos locais depende da presença de um seletor compatível na TV. Como alternativa, abra **Importar → Enviar pela rede local → Iniciar transferência**. Escaneie o QR com o celular ou digite o endereço completo em um navegador na mesma rede e selecione arquivos CBZ/ZIP. Não é necessário instalar aplicativo no celular.

A sessão de rede dura até 15 minutos, aceita um envio por vez e termina ao parar, sair da tela, mudar de rede ou colocar o aplicativo em segundo plano. A TV precisa de espaço para o upload temporário e sua cópia validada. Redes de convidados com isolamento de clientes podem impedir o acesso. O endereço possui um token temporário e não permite consultar ou baixar a biblioteca. Detalhes em `docs/REDE-LOCAL.md`.

No celular, selecione o CBZ original diretamente, sem compactá-lo novamente. Um ZIP de quadrinho deve conter as imagens das páginas. Um ZIP com outros CBZ/ZIP recebe uma mensagem explicativa; coleções dentro de outro ZIP ainda não são importadas automaticamente. Desde 0.2.1 o seletor web não aplica um filtro de tipo do sistema, para evitar bloquear CBZs no iPhone; arquivos continuam sendo validados no envio e na TV.

## Próximos incrementos acordados

- **FTP:** servidor temporário na TV para receber arquivos; diretório restrito e autenticação a definir.
- **Web:** portal próprio com token temporário e QR de pareamento; depende de backend e hospedagem próprios.
- **Mihon:** vários repositórios, instalação e atualização de extensões e seleção de fontes. HQ Now não terá exclusividade. O motor requer revisão de dependências/licenças e testes por extensão.
- Ferramentas adicionais, marcadores, galeria, outros formatos de arquivo e zoom por tiles.

As telas de fontes e dos futuros transportes explicam o estado atual. FTP, portal remoto e extensões Mihon ainda não estão ativos. O servidor da rede local funciona somente durante uma sessão iniciada pelo usuário.

As licenças de NanoHTTPD e ZXing estão em `docs/licenses/` e são incluídas nos assets do APK.

## Estrutura

`core/` contém o índice ZIP, importação para disco, cache e estado do leitor, com testes JVM sem dependência do Android. `app/` contém Room, ViewModels, seletor de arquivos e interface Compose para TV. A separação permite testar os limites dos arquivos e os comandos do controle de forma independente da TV.
