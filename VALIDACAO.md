# Validação — Comic TV 0.7.1

23/10/2026 · versionCode 10 · minSdk 23 · targetSdk 36.

## Correção

O instalador verifica o Android mínimo no AndroidManifest.xml binário do APK antes de chamar PackageManager ou oferecer o arquivo ao instalador nativo. Caso a TV seja incompatível, apresenta o Android/API exigido e a API atual. Preserva conferências de pacote, versão, feature de extensão e certificado SHA-256; nenhum código do APK é executado na inspeção.

A leitura do manifesto é limitada a 1 MiB expandido, com verificação de chunks, pools UTF-8/UTF-16, atributos, namespace Android, resource IDs e duplicações. Metadados inválidos ou versão de desenvolvimento não numérica são recusados. O rótulo da lista de instaladas passou a dizer “API compatível”, sem prometer funcionamento de todos os sites.

## Evidência real de HQ Now

APK consultado: tachiyomi-pt.hqnow-v1.6.0.apk, pacote eu.kanade.tachiyomi.extension.pt.hqnow, versionCode 106000. apksigner conferiu certificado 9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2, igual ao pin Keiyoushi. aapt2 revelou minSdk 26. O novo leitor JVM também retornou26; a política aceitou API 26 e recusou API 23 com mensagem específica. São testes de metadados e política, sem execução do APK no computador ou no Android.

Uma consulta pública à API usada pela fonte retornou Saga do Infinito (id 1079),17 edições. A primeira, Prelúdio- Em Busca de Poder parte 1, retornou 52 páginas. HEAD do primeiro endereço de imagem respondeu 200, image/jpeg. O código da extensão inverte a lista recebida do site; a opção de inverter sequência do leitor permite escolher a apresentação desejada. Isso verifica serviço e referências, sem certificar carregamento da extensão dentro do Comic TV.

Referência do código inspecionado: https://github.com/keiyoushi/extensions-source/blob/main/src/pt/hqnow/src/eu/kanade/tachiyomi/extension/pt/hqnow/HQNow.kt

## Verificação

119 testes JVM passaram: 82 core, 7 mihon, 30 app, sem falhas ou erros. Os testes novos foram observados falhar antes da implementação. Incluem pools UTF-8/UTF-16, valores inteiros/string/default, metadados inválidos, namespace, resource ID, duplicações, truncamento e tamanho expandido. Uma sonda adicional com 5.000 mutações determinísticas do manifesto real não produziu exceções inesperadas.

Build debug, compilação de instrumentação do app e lint aprovados:0 erros e 34 avisos. Revisão independente sem Critical/Important pendentes. Assinatura final v1/v2 verificada; aapt2 confirma versão, SDKs e atividade Leanback. Sem dispositivo/emulador conectado; instrumentação e execução real de extensão continuam pendentes.

SHA-256 do APK: `36DF75F090B60A0A805AE81F49DD969B0E580041483E556ACDF018FA19DEF229`.

## Teste físico pendente

Atualizar APK sem desinstalar. Fontes → adicionar repositório → instalar extensão → confirmar no Android → Atualizar instaladas. Em TV com Android 8/API 26 ou superior, testar HQ Now/Saga do Infinito, abertura de edição, miniaturas e passagem entre edições. Em Android anterior, confirmar mensagem de requisito mínimo. O suporte do aplicativo a Android 6 não reduz o requisito de cada extensão.

Recursos 0.7.0 de continuidade online permanecem incluídos. Portal remoto continua adiado, sem alteração da hospedagem do Conchat.
