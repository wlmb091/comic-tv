# Validação — Comic TV 0.2.1

Data: 23 de outubro de 2026. Correção da seleção de arquivos e diagnóstico de importação.

## Relato e evidências

As fotos do usuário mostram o seletor de arquivos do iPhone com CBZs desabilitados, enquanto ZIP é selecionável. O upload de Thanos.zip chega a 100% e recebe a mensagem genérica do importador. O usuário repetiu o envio compactando apenas um arquivo e observou a mesma recusa.

O HTML 0.2.0 aplicava `accept=".cbz,.zip"` ao seletor nativo. A atualização remove essa restrição do seletor; as verificações de extensão/tamanho no JavaScript e no servidor, assim como a validação do conteúdo ZIP, continuam ativas. Isso contorna a dependência do seletor em registrar/reconhecer o tipo CBZ.

Referências técnicas consultadas: [documentação do seletor file](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/input/file) e [relato WebKit de arquivos desabilitados por accept no iOS](https://bugs.webkit.org/show_bug.cgi?id=229456). Esses relatos não provam o comportamento de toda versão do iOS nem substituem o novo teste no aparelho do usuário.

O ZIP original e o CBZ original não foram fornecidos, portanto a causa exata da recusa desse arquivo permanece sem confirmação. Um ZIP contendo CBZs em vez de imagens é uma hipótese compatível com o relato; esse caso foi reproduzido com fixture e agora recebe orientação específica. Não foi acrescentada importação recursiva de coleções ZIP.

## Alterações

- Seletor da página de envio sem filtro nativo, mantendo seleção múltipla e validação após a escolha.
- Orientação para enviar o CBZ original, sem compactá-lo novamente.
- ZIP sem imagens que contém outros CBZ/ZIP agora explica que os quadrinhos devem ser enviados diretamente.
- Erros controlados de arquivo/imagem são apresentados no navegador e no estado da TV. Falhas inesperadas continuam mascaradas e são registradas no log Android para diagnóstico, sem colocar detalhes internos na resposta HTTP.
- versionName 0.2.1, versionCode 3; configuração de assinatura debug preservada para atualização da versão anterior.

## Verificação executada

`core:test`, `app:testDebugUnitTest`, `app:assembleDebug`, `app:assembleDebugAndroidTest` e `app:lintDebug`: BUILD SUCCESSFUL.

35 testes JVM, zero falhas. Três regressões foram observadas falhando antes da correção: filtro do seletor, diagnóstico de ZIP com CBZs e propagação do erro de validação. Um teste adicional confirma que exceções inesperadas não expõem caminhos internos ao navegador/TV. Foram mantidos os testes anteriores de limite, streaming, cancelamento e persistência.

Lint: zero erros e 21 avisos. JavaScript: `node --check` aprovado. Assinatura do APK: v1/v2 verificadas. Manifesto: minSdk 23 e targetSdk 36. Revisão independente do diff sem problemas relevantes identificados.

Não houve execução em iPhone/TV durante esta correção. Os testes de interface/Room compilaram, sem execução em dispositivo. A correção do seletor e o arquivo real do usuário ainda exigem o novo teste físico.

## Como retestar

1. Instalar Comic-TV-0.2.1-debug.apk sobre a versão anterior.
2. Iniciar uma nova sessão de rede na TV e abrir o novo QR/endereço no celular.
3. Escolher o CBZ original diretamente, sem criar um ZIP em volta dele.
4. Se houver recusa, conservar a mensagem específica e fornecer o CBZ/ZIP original para análise do conteúdo.

SHA-256 do APK: `E28DF8B69BE377E5CC8A89582554585B72D35EA22F079A03C6319DC5DD281B93`.
