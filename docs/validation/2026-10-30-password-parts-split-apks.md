# Senha, partes e XAPK/APKM — 1.4.6

Implementação própria, sem executar aplicativos de terceiros nem copiar código/assets de outros instaladores.

## Importação explícita

Importar e Explorador oferecem seleção múltipla e senha opcional. A preparação usa apenas os URIs escolhidos, em diretório privado exclusivo. Não procura arquivos vizinhos, rede, USB não autorizado ou outras pastas. Senha não entra em preferências, banco, nomes, logs ou backup; o campo não usa estado salvo e os arrays são zerados ao terminar. Strings transitórias do teclado/Compose e cópias internas das bibliotecas não têm apagamento de memória garantido.

ZIP ZipCrypto/AES usa Zip4j 2.11.5. RAR usa Junrar 8.1.2 com senha e dicionário máximo de 32 MiB; **RAR com cabeçalhos/nomes criptografados e RAR multivolume são recusados**, preservando o preflight de metadados. 7z usa Commons Compress 1.28.0/XZ existentes com senha, limite de memória de 32 MiB e metadados de até 8 MiB. O custo AES 7z acima de 2^20 iterações é recusado antes de decodificar dados; o caso 63 representa derivação direta. Os coders do cabeçalho codificado são verificados antes de abrir o decoder; os coders do conteúdo são verificados antes de extrair, usando os campos da versão fixa da biblioteca e falhando de forma fechada caso o layout mude.

Partes suportadas são a divisão binária de **um arquivo único**, com mesmo prefixo e sequência `.001`, `.002`… completa, até 100 partes/1 GiB no total. Não confundir com volumes RAR ou ZIP `.z01`: esses formatos continuam recusados com mensagem explícita. A ausência da última parte só pode ser detectada pelo decoder/CRC do arquivo resultante. Seleção misturada, duplicada, vazia ou com lacunas é recusada.

A preparação gera uma cópia CBZ sem senha, com páginas em ordem natural, sem recomprimir pixels. A biblioteca importa essa cópia; originais permanecem intactos. São aplicados limites de entradas, 32 MiB por página, 2 GiB descompactados, saída de até 1 GiB, cancelamento e reserva de 64 MiB. Isto não remove DRM e não oferece senha persistida para reabertura do original.

## Instalação Android explícita

Somente Explorador local/SAF trata XAPK/APKM como instalação, junto do APK local existente. Downloads de APK pelo navegador e a confiança de extensões não foram liberados.

O container é ZIP; a preparação extrai no máximo 100 APKs para nomes internos fixos, com CRC/tamanho e limites de 1 GiB, sem materializar caminhos externos. Todos precisam ter o mesmo pacote, versão e conjunto de assinantes, com exatamente uma base e sem nomes de split duplicados. O atributo `split` é lido do manifesto Android binário limitado a 2 MiB; certificados vêm do PackageManager. A sessão Android também valida e verifica o conjunto durante a instalação. A ABI é escolhida pela prioridade `Build.SUPPORTED_ABIS`; splits nativos incompatíveis são excluídos. OBB é recusado integralmente, com mensagem; não há cópia silenciosa para Android/obb.

`PackageInstaller.Session` recebe os APKs em thread de trabalho e exige ação do usuário onde a API disponibiliza esse controle. A confirmação do Android é apresentada pelo callback da sessão. A activity é privada; instalação depende da permissão Android de fontes desconhecidas. O Android pode recusar dependências de splits ausentes, SDK, ABI ou políticas da TV. Nenhuma alegação de suporte a todos os XAPK/APKM.

## Verificação desta etapa

- Compilação Kotlin isolada dos helpers/readers core: aprovada.
- Cinco JUnit isolados: ZIP AES com senha correta/incorreta, original intacto e arrays zerados; partes e lacunas; identidade/base/ABI dos splits; manifesto inválido; fixture 7z AES real e rejeição de custo AES excessivo.
- Manifesto binário do APK Comic TV existente foi interpretado corretamente como base.
- Compilação Kotlin isolada de `ExplorerSplitApk`, `ExplorerSplitInstallActivity` e `ProtectedArchiveImport` contra Android 36: aprovada, com aviso de API Back obsoleta.
- Compilação Android integral/lint ficam a cargo da integração final. Instalação real na Box e teste físico de senha/multipart permanecem pendentes.

Referências oficiais: [Zip4j](https://github.com/srikanth-lingala/zip4j), [SevenZFile.Builder](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/sevenz/SevenZFile.Builder.html), [PackageInstaller.Session](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session).
