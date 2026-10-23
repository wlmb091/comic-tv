# Validação — Comic TV 0.1.0

Data: 22 de outubro de 2026. Primeiro incremento local.

## Resultado confirmado

- Build debug do aplicativo: aprovado.
- Build do APK de testes instrumentados: aprovado; testes não executados em dispositivo.
- Testes JVM: 20 executados, zero falhas e zero erros.
- Android Lint: zero erros e 19 avisos.
- Assinatura do APK: verificada com apksigner, esquemas v1 e v2.
- Manifesto compilado: pacote `com.pelican.comictv`, versão `0.1.0`, minSdk 23, targetSdk 36 e launcher Leanback.
- Revisão estática independente: três problemas relevantes identificados e corrigidos; reavaliação sem problemas relevantes remanescentes nas correções.

## Comando verificado

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

Neste ambiente, o executável Gradle 8.13 foi usado com os mesmos alvos. O SDK, JDK e caches ficaram na pasta de trabalho, fora dos fontes. Um proxy HTTP restrito a loopback encaminhou downloads para os repositórios oficiais devido a problemas de conectividade das ferramentas; essa configuração não foi incorporada aos repositórios Gradle do produto. Uma propriedade local indicou uma chave debug gerada para o projeto, contornando bloqueio do arquivo de lock da chave padrão no Windows. Em uma instalação normal, o projeto usa a chave debug padrão do Android.

## Testes JVM

| Conjunto | Quantidade | Resultado |
|---|---:|---|
| Índice ZIP, caminhos, limites, cópia e cache | 8 | Aprovado |
| Importação transacional e reconciliação | 3 | Aprovado |
| Páginas, RTL, zoom e deslocamento | 4 | Aprovado |
| Carregamento assíncrono, saída e retomada | 5 | Aprovado |

Os testes cobrem, entre outros casos: ordem 1/2/10, nomes numéricos longos, ZIP inválido ou sem imagens, limite de entradas, limite de bytes inclusive sem tamanho informado, remoção de gravação parcial, limpeza de imagem rejeitada, expulsão de cache não protegido, importação interrompida, IDs preservados e descarte de originais órfãos.

As regressões da revisão comprovam que sair antes de terminar a inicialização não grava a página zero sobre o progresso anterior, que repetidos pedidos de saída só navegam uma vez e que uma falha de indexação pode ser tentada novamente. O teste de troca rápida confirma que uma requisição antiga não substitui a página mais recente.

## Avisos de manutenção

Os 19 avisos de lint são de versões de dependências com atualizações disponíveis, orientação fixa de tela, regras modernas de extração/backup e variações de densidade de recursos. Não foram ocultados por baseline nem por desativação das verificações. As versões foram conservadas para manter o conjunto compilado nesta entrega. A verificação de assinatura também emite avisos sobre metadados JAR fora da cobertura individual de v1; a assinatura v2 do APK foi verificada com sucesso.

## Verificações ainda pendentes

Não houve execução do APK em emulador ou TV física. O adb não conseguiu iniciar neste ambiente. Assim, não foram confirmados visualmente o layout em 1080p/4K, foco inicial e restauração, tecla mantida, compatibilidade do seletor de arquivos, recriação real de processo, uso de memória ou desempenho em uma TV de pouca memória.

`LibraryFlowTest` e `LibraryDatabaseTest` compilaram, mas continuam pendentes de execução em dispositivo. A persistência Room e o fluxo visual completo de importação/leitura precisam dessa execução; os testes JVM não a substituem.

## Roteiro para TV

1. Instalar o APK debug e abri-lo pelo launcher da TV.
2. Verificar a biblioteca vazia e navegar até Importar com o controle.
3. Selecionar um CBZ/ZIP real pelo seletor disponível no aparelho.
4. Abrir os detalhes e iniciar a leitura; verificar páginas, zoom, ajuste, RTL e modo Mover página.
5. Retornar à biblioteca, abrir novamente e conferir a página salva.
6. Repetir com várias edições, tecla mantida, arquivo inválido e retorno após fechar o aplicativo.

## Limites da entrega

O APK contém o fluxo local e as telas explicativas dos próximos incrementos. Rede local/QR, servidor FTP, portal web/token e instalação/execução de extensões Mihon ainda não estão implementados. A validação de HQ Now e outras fontes será feita no incremento do motor de extensões.

Arquivo entregue: `Comic-TV-0.1.0-debug.apk`.

SHA-256: `F19E5338503F2844B7D3D1E281CCD6CAC75CC68D29C09EFD04CB32AC46C2A6CD`.
