# Investigação — erro ao instalar extensões

Pedido de 31/10/2026: investigar foto com Android mostrando “Ocorreu um problema ao analisar o pacote” ao instalar DB New Hope. Download aparece 100%, uma extensão pronta. Investigação apenas; sem alteração funcional, geração ou publicação de APK.

## Resultado

Defeito confirmado no manifesto da versão publicada 1.4.7: dois providers declaram a mesma classe `androidx.core.content.FileProvider`, com authorities `.explorer` e `.extensions` e XMLs de pastas diferentes. Introduzido junto ao explorador em 1.4.5 (commit 21e9cdf); antes havia somente o provider de extensões e a subclasse própria de atualização.

O Android identifica providers locais pelo ComponentName (pacote + classe), não apenas pela authority. A segunda declaração pode reutilizar o registro da primeira, sem registrar corretamente a outra authority. Isso compromete a entrega do URI de extensões ao instalador, embora o Comic TV consiga analisar o arquivo diretamente por seu caminho privado antes de abrir o Android. É a hipótese principal, de alta confiança, para o erro da foto. A foto isolada não confirma a exceção interna da Box.

Referência primária: [AOSP Android 9, ActivityThread.installProvider](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/app/ActivityThread.java#L5962), especialmente mLocalProvidersByName e installProviderAuthoritiesLocked. Consulta de fonte estática, sem alegar observação de execução.

Correção indicada: providers com subclasses próprias distintas para explorador e extensões, conservando authorities, pastas restritas, grantUriPermissions e confirmação do Android. A atualização do app já usa sua própria AppUpdateFileProvider. Validar abertura e leitura integral dos URIs de ambos os providers e tentativa real pelo instalador, inclusive após sair e retornar. Não ampliar acesso às pastas.

## Evidências de artefatos

- APK publicado [Comic TV 1.4.7](https://github.com/wlmb091/comic-tv-updates/releases/tag/v1.4.7), baixado especificamente para investigar: 25.302.015 bytes, SHA256 `d63de4f40c95a9a041be03f06e10ad143c8f43f55e1e8b1c932f92d21e97964e`, igual ao registro da publicação. Aapt2 confirmou as duas declarações no manifesto binário, não apenas no fonte. REA inspect-artifact: `ev_bdca7be7b4c65d20d8f0a2e2dd9be1efe4e0c516e5882ad1c8b5b64b27bdc0b6`.
- Índice protobuf atual de [Keiyoushi](https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json) identifica DB New Hope, pacote `eu.kanade.tachiyomi.extension.all.dbnewhope`, versão 1.6.0/code106000/lib1.6. APK obtido da URL fornecida pelo índice: `https://github.com/keiyoushi/extensions/releases/download/0d83f6b-0/tachiyomi-all.dbnewhope-v1.6.0.apk`.
- APK DB New Hope: 67.051 bytes, SHA256 `22fb9714aac565e55f262bb88e9a88cea5bd4cc0c2c782834ab6580c592e8f6b`. CRC de todas as entradas ZIP aprovado. Aapt2: minSdk26, targetSdk37, feature tachiyomi.extension. Apksigner: assinatura v2 válida, um certificado SHA256 `9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2`, igual ao fingerprint do repositório. Lib1.6 está dentro da política atual do host. REA inspect-artifact: `ev_de471cddaec91c3fe386a3ca828eb45715a8640bf94eb8ee69d4337910f594ce`.
- O pacote remoto atual é válido; não prova que seus bytes sejam os mesmos baixados na Box no momento da foto. Android abaixo de26 já não executaria a versão atual do Comic TV. TargetSdk37 não é minSdk37.
- ExtensionInstaller.prepare e revalidate analisam o arquivo local e verificam pacote, versão, feature e certificado antes da abertura do instalador. A fila marca Ready após a verificação; a foto é compatível com falha posterior de acesso ao URI.

Artefatos locais e saída completa: `build/extension-install-investigation/`. REA MCP não está conectado (doctor indicou registro ausente); usado CLI6.7.0 para inventário, sem alterar configuração. Sem execução/decompilação das extensões. Aapt2/apksigner/CRC são evidências das ferramentas Android/Python, separadas do inventário REA.

## Limites e pendências

ADB não encontrou dispositivos; sem logcat da Box e sem instalação física reproduzida. Não concluir que todas as extensões têm a mesma causa. Não houve correção de código nem testes de correção. Próxima etapa técnica: corrigir a identidade dos providers e testar o fluxo no Android. Nenhuma limpeza de dados, desinstalação de fontes ou mudança de confiança necessária para investigar este defeito.
