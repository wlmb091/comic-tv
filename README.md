# Comic TV

Leitor nativo para Android TV e Google TV, em português e operado pelo controle remoto.

## Versão 0.9.5

- Design final registrado em docs/design/DESIGN.md e nas instruções AGENTS.md, com imagem aprovada preservada no projeto.
- Identidade oficial: assinatura fornecida pelo usuário, símbolo vetorial para ícone e lateral e banner com a arte COMIC TV.
- Biblioteca nativa com destaque da edição focada/última leitura, ações de continuar/detalhes, leituras recentes compactas e coleção em capas verticais.
- Fundo oficial Comic TV quando a capa desfocada está desligada; opção de capa segue disponível na biblioteca e nos detalhes, com alto contraste preto.
- Biblioteca local com capas e lista de leituras recentes.
- Detalhes com título, informações e ações juntos à esquerda, capa ampliada no centro e resumo independente à direita, com a mesma altura da capa. O bloco de título não é selecionável.
- Segure OK por 1 segundo sobre capas ou leituras recentes para abrir continuar/leitura, detalhes e remoção/restauração do histórico.
- Remover do histórico abre confirmação com Cancelar e Remover. O aviso explica que edição, arquivos e página salva são mantidos; não fica mais fixo nos detalhes. A edição volta ao histórico quando você retoma a leitura.
- Configurações com aba Acessibilidade e aparência: presets preto com amarelo, verde, azul ou violeta, texto padrão/115%/130% e alto contraste. Preferências persistidas no aparelho.
- Texto claro padrão definido em toda a interface; cores dos quadrinhos não são alteradas.
- Resumo expandido rola com ↑/↓ mesmo com o botão de expandir/recolher focado.
- Ao abrir Novidades ou Catálogo pela lateral, foco e seleção acompanham a aba acionada e não o resultado atrasado da fonte.

- Detalhes com título legível, progresso e resumo real salvo pela fonte; painel de informações rolável pelo controle e ações de leitura sempre acessíveis.
- Um único ícone de visualização alterna Normal → Galeria → Livro. Galeria mostra a página atual maior e vizinhas menores. Livro mostra a atual e a seguinte juntas, com passagem de duas em duas e ordem visual RTL quando ativada. Toque curto no OK mantém mover/zoom.
- Ferramentas do leitor em ícones na lateral direita, com nome ao focar: páginas, capítulos anterior/próximo, lista de capítulos, galeria, ajuste e opções.
- Lista de capítulos à esquerda com prévia estática da página atual; seleção de páginas com miniaturas em faixa compacta.
- Importação de CBZ/ZIP, CBR/RAR, PDF e EPUB pelo seletor do Android ou rede local, incluindo USB quando oferecido pelo dispositivo.
- Formato reconhecido pelo conteúdo, mesmo com extensão incorreta. RAR 4 e 5 com extração limitada; arquivos divididos ou com senha são recusados.
- PDF renderizado uma página por vez; EPUB de imagens e texto na sequência do livro. Texto tem ajuste de fonte e rolagem pelo D-pad.
- Páginas JPEG, PNG e WebP, ordenadas naturalmente.
- Leitor com ajuste à página, largura ou altura, zoom de 1x a 3x e deslocamento explícito.
- Progresso e preferências por edição em Room.
- Tema escuro com foco âmbar, seguindo o conceito aprovado.
- Ao iniciar, o foco fica no conteúdo da biblioteca e a lateral permanece recolhida.
- Fundo opcional na biblioteca e nos detalhes: cópia da capa preenchendo a tela, desfocada e escurecida. Ative em Configurações → Acessibilidade e aparência → Fundo da biblioteca e detalhes. Em alto contraste, permanece preto.
- Barra lateral flutuante: recolhida em ícones e expandida com nomes somente quando um ícone recebe foco; o catálogo mantém sua largura.
- Biblioteca com cabeçalhos roláveis e histórico completo de todas as leituras registradas, sem limite de dois itens.
- Catálogo e Novidades têm acessos próprios na lateral e usam a mesma fonte selecionada. Novidades vem da fonte (adicionados/atualizados), separado do histórico pessoal.
- O ícone de pasta no catálogo troca a fonte. Extensões tem grade de instaladas, atualizações e disponíveis, com busca nos repositórios cadastrados e ícones pequenos.
- A seleção de fonte permanece salva; repositórios e detalhes técnicos ficam separados do catálogo.
- Capas ausentes na busca são consultadas nos detalhes dos itens visíveis, com cache limitado e até duas consultas simultâneas.
- Barra para escolher página/seção pelo D-pad, com confirmação antes de carregar e salvar a nova posição.
- Seletor de páginas com fundo preto, destaque amarelo, miniaturas ampliadas (194 dp na selecionada, 170 dp nas vizinhas) e barra de progresso fina.
- Miniaturas da posição selecionada e até duas vizinhas de cada lado, geradas sob demanda em cache separado de 32 MiB.
- Leitura automática: slides de imagens ou rolagem de texto EPUB, com 10 segundos por página/tela inicialmente e intervalos de 5,10,20,30 ou 60 segundos. Pausa com comandos manuais, ferramentas, erro ou segundo plano.
- Transferência pela rede local (Wi-Fi/Ethernet), com URL temporária, QR code, upload pelo navegador e acompanhamento na TV.
- Servidor FTP temporário, com usuário/senha por sessão, QR code e recebimento em modo passivo.
- Fontes Mihon: múltiplos repositórios, índices atuais e legados, download verificado de extensões e instalação confirmada pelo Android.
- Verificação do Android mínimo exigido pelo APK antes da instalação, com mensagem de incompatibilidade específica.
- Catálogo online com populares, recentes, busca, detalhes e capítulos/edições na sequência da fonte; leitura no mesmo leitor, com progresso na biblioteca.
- Nas ferramentas do leitor online, Edições da saga / série abre a lista atualizada, destaca a edição atual e oferece anterior/próxima e ordem invertível por título.

O projeto está em desenvolvimento. Consulte `VALIDACAO.md` para resultados reais de compilação e testes; a existência do código não significa validação em uma TV.

Especificação, plano, referências funcionais e conceito visual aprovado estão em `docs/`.

## Compilar

Requisitos: JDK 17, SDK Android Platform 36, Build Tools 35.0.0 e conexão para resolver dependências. Gradle 8.13 é fornecido pelo wrapper; AGP 8.11.1, Kotlin 2.2.0 e demais versões estão fixados nos arquivos Gradle. `minSdk` é 26 (Android 8).

Abra a pasta no Android Studio ou configure `JAVA_HOME` e `ANDROID_HOME`. Alternativamente, crie um `local.properties` com `sdk.dir=C\:/caminho/do/Android/Sdk`. Essa configuração local não deve entrar no Git.

```powershell
.\gradlew.bat :core:test :mihon:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
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
| Leitor de imagens | OK rápido (<1s) alterna zoom/mover; segurar OK por 1s abre ferramentas |
| Ferramentas | Ajuste alterna página inteira, largura e altura |
| Mover página | As quatro setas deslocam a página; Voltar retorna às ferramentas |
| Ferramentas | Voltar retorna à leitura |
| Leitura normal | Voltar salva e retorna à biblioteca |
| EPUB de texto | Cima/baixo rolam; esquerda/direita trocam seções; OK abre ajuste de fonte |
| Ferramentas | Escolher página/seção abre a barra na posição atual |
| Barra | Esquerda/direita escolhem uma posição; cima/baixo saltam −10/+10 |
| Barra | OK abre a posição; Voltar cancela e retorna às ferramentas |
| Ferramentas | Iniciar leitura automática; Intervalo alterna a velocidade |
| Automático | Setas e OK pausam; ao final do livro ou em erro a leitura automática termina |

Ao mudar de página, zoom e deslocamento voltam ao estado inicial. O zoom desta versão amplia uma imagem decodificada para a tela; alta resolução por tiles faz parte de um incremento futuro.

A contagem do slide começa após a imagem ser exibida com sucesso, sem consumir o intervalo durante carregamento. No texto, o intervalo indica aproximadamente o tempo para rolar uma altura visível; ao fim da seção, segue para a próxima. A automação não reinicia sozinha quando o aplicativo volta ao primeiro plano. A barra carrega apenas até cinco miniaturas após 180 ms de estabilização do cursor; essas prévias não alteram a página lida nem o progresso. Texto EPUB recebe um cartão com trecho, não uma reprodução da paginação impressa.

## Importação e armazenamento

O arquivo original é copiado para o armazenamento privado, sem depender permanentemente da URI externa. Arquivos temporários são validados antes de entrar na biblioteca. Originais são conservados; o cache de páginas é descartável.

Limites: 1 GiB por arquivo, 5.000 entradas/páginas, 32 MiB por página extraída, 2 GiB de conteúdo declarado, 100 megapixels por imagem e 256 MiB de cache de páginas. RAR tem dicionário limitado a 32 MiB e cabeçalhos limitados antes da leitura pelo decoder. Apenas a primeira página é decodificada durante a importação; erros em páginas posteriores são apresentados no leitor.

PDF usa o PdfRenderer do Android, sem senha, com lado maior de até 2048 pixels por página renderizada. EPUB segue container, manifesto e spine, extrai texto básico e imagens JPEG/PNG/WebP, sem executar scripts nem acessar recursos externos. Texto usa seções de até 12.000 caracteres, com fonte de 18 a 40 sp e posição relativa de rolagem salva por seção. A diagramação CSS, fontes embutidas, áudio, vídeo, SVG independente e DRM não são reproduzidos nesta versão; arquivos com encryption.xml são recusados. Markup é limitado a 2 MiB por recurso e texto a 16 milhões de caracteres no livro.

A seleção de arquivos locais depende da presença de um seletor compatível na TV. Como alternativa, abra **Importar → Enviar pela rede local → Iniciar transferência**. Escaneie o QR com o celular ou digite o endereço completo em um navegador na mesma rede e selecione CBZ/ZIP, CBR/RAR, PDF ou EPUB. Não é necessário instalar aplicativo no celular.

A sessão de rede dura até 15 minutos, aceita um envio por vez e termina ao parar, sair da tela, mudar de rede ou colocar o aplicativo em segundo plano. A TV precisa de espaço para o upload temporário e sua cópia validada. Redes de convidados com isolamento de clientes podem impedir o acesso. O endereço possui um token temporário e não permite consultar ou baixar a biblioteca. Detalhes em `docs/REDE-LOCAL.md`.

No celular, selecione o CBZ original diretamente, sem compactá-lo novamente. Um ZIP de quadrinho deve conter as imagens das páginas. Um ZIP com outros CBZ/ZIP recebe uma mensagem explicativa; coleções dentro de outro ZIP ainda não são importadas automaticamente. Desde 0.2.1 o seletor web não aplica um filtro de tipo do sistema, para evitar bloquear CBZs no iPhone; arquivos continuam sendo validados no envio e na TV.

## Fontes online

Abra **Fontes → Adicionar Keiyoushi** ou informe a URL HTTPS de outro repositório. Abra as extensões, filtre por nome/idioma e escolha **Instalar**. O Android poderá solicitar autorização para o Comic TV instalar pacotes; confirme a instalação na tela do sistema. Ao retornar, atualize as instaladas e abra a fonte desejada. Os APKs são conferidos por pacote, versão e certificado SHA-256 do repositório antes de serem oferecidos ao instalador.

No catálogo, escolha uma capa para abrir detalhes e capítulos. Sagas como as do HQ Now são apresentadas conforme o título e a sequência de edições que a extensão retornar. Ao sair da leitura online, você volta à lista anterior. O capítulo aberto aparece na biblioteca para continuar depois; as páginas são carregadas sob demanda e compartilham o cache de 256 MiB, com prévias em 32 MiB separados. A retomada consulta a fonte novamente; esta versão não é um gerenciador de downloads completos para leitura offline.

Para continuar outra edição durante a leitura, segure OK por 1 segundo → **Edições da saga / série**. A posição é salva antes de consultar a fonte. A lista identifica a edição atual, permite abrir anterior/próxima ou qualquer item e pode inverter a sequência; a escolha de ordem fica salva por título. A ordem da fonte não é presumida como cronológica. Edições já abertas retomam sua própria página. A leitura automática continua limitada às páginas da edição atual.

Compatibilidade é por extensão: APIs 1.2–1.6 são hospedadas, mas bibliotecas adicionais, preferências específicas e fontes que exigem QuickJS podem precisar de adaptação. QuickJS não está incluído. Desafios de sites dependem do WebView do Android. A leitura do índice Keiyoushi e a identificação do HQ Now foram verificadas no computador; execução do APK, catálogo real e leitura na TV ainda exigem teste no dispositivo. Detalhes em `docs/FONTES-ONLINE.md`.

Desde 0.7.4, o Comic TV aceita Android 8/API 26 ou superior para preservar métodos padrão de interfaces usados pelas extensões. Android 6/7 deixam de ser suportados. Isso não reduz o requisito de cada extensão. O APK HQ Now 1.6.0 verificado exige Android 8/API 26. Desde 0.7.1 o requisito é lido diretamente do manifesto do APK antes de oferecê-lo ao instalador, preservando as verificações de pacote, versão e certificado. “API compatível” na lista de instaladas descreve a versão da interface, sem certificar funcionamento de cada site.

## Transferência FTP

Abra **Importar → Receber por FTP → Iniciar servidor FTP**. Em um cliente FTP na mesma rede, informe o endereço/porta e usuário/senha mostrados na TV; selecione FTP convencional e modo passivo. Envie CBZ/ZIP, CBR/RAR, PDF ou EPUB diretamente para a raiz. O QR contém os dados de acesso e exige um leitor/cliente que aceite URLs FTP.

Cada sessão dura até 15 minutos e termina ao parar, sair da tela, mudar de rede ou ir ao segundo plano. A senha muda ao iniciar outra sessão. O FTP recebe um arquivo por vez, até 1 GiB, e só adiciona à biblioteca depois de validar. Não expõe arquivos da biblioteca. Use em sua rede local: FTP convencional não criptografa credenciais nem conteúdo. Detalhes e limites em `docs/FTP.md`.

## Próximos incrementos acordados

- **Web:** portal próprio com token temporário e QR de pareamento; adiado em favor da leitura online por extensões, conforme a prioridade esclarecida pelo usuário. Fontes online não dependem desse portal nem da hospedagem do Conchat.
- Ferramentas adicionais, marcadores, galeria, outros formatos de arquivo e zoom por tiles.

O portal remoto ainda não está ativo. Os servidores da rede local e FTP funcionam somente durante uma sessão iniciada pelo usuário.

As licenças de NanoHTTPD, ZXing, Junrar, SLF4J e Jsoup estão em `docs/licenses/` e são incluídas nos assets do APK. PdfRenderer é API do Android; desugaring de APIs Java permanece habilitado; o aplicativo exige minSdk 26.

## Estrutura

`core/` contém arquivos, cache, estado do leitor e índices de repositórios, com testes JVM. `mihon/` hospeda a API Android de extensões, preservando licenças Apache-2.0 e atribuições em seus assets. `app/` contém leitores, Room, instalação nativa, fontes e interface Compose para TV.

Temas em Configurações → Acessibilidade e aparência: Comic TV, Tema Flix, Tema Prime, Tema Max e Tema Cinema. Mudam superfícies, barras, botões, progresso e foco; a logo e as capas conservam as cores originais. Presets antigos continuam disponíveis.

Cabeçalho da biblioteca em Montserrat ExtraBold: COMIC branco e TV na cor do tema. Ações do destaque inicial ficam neutras sem foco.

A barra lateral expandida também usa o cabeçalho textual COMIC TV, com TV na cor do tema.

Relacionados abaixo da tela de detalhes: sugestões da biblioteca e de uma amostra limitada do catálogo da fonte, com motivo verificável (mesma série ou créditos em comum). Abrir uma sugestão do catálogo permite escolher uma edição; nada é adicionado à biblioteca até essa escolha. Metadados da fonte limitam as relações disponíveis; arquivos locais sem créditos não recebem sugestões artificiais.

Na 0.9.5, a seção e o atalho Relacionados estão ocultos; nenhuma consulta de recomendações é iniciada. Código preservado para futura revisão. COMIC TV centralizado no menu lateral expandido.

0.9.5: catálogo com capas inteiras e títulos abaixo, tipografia e cabeçalho compactos; grades adaptadas ao tamanho do texto. Fontes, extensões e extensões por repositório usam cartões compactos; abas com contadores e estados vazios explicativos. Instalação continua confirmada pelo Android.
