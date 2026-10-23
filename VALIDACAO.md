# Validação — Comic TV 0.5.0

23/10/2026 · versionCode 7 · minSdk 23 · targetSdk 36.

## Resultado

89 testes JVM passaram, sem falhas ou erros, nos módulos core, mihon e app. Build debug e APKs dos testes de instrumentação do app e do runtime Mihon passaram. Lint do app: 0 erros e 32 avisos (atualizações de dependências, recomendações Android/KTX e avisos preexistentes). Não há TV/emulador conectado; instrumentação não executada.

O APK final inclui licenças/atribuições, assina com a mesma chave debug e verifica em v1/v2. aapt2 confirma versão, SDKs e atividade Leanback.

SHA-256: 598206C7C0E3C92487B12737088DA1A42A6ED3C283FA4739F6C086FFBA0A99B1.

## Fontes

O cliente JVM real leu Keiyoushi (1426 extensões, índice protobuf/gzip), identificou HQ Now 1.6.0, pacote eu.kanade.tachiyomi.extension.pt.hqnow, versionCode 106000, e leu Suwayomi (1 extensão, índice JSON legado). Yuzono retorna stubs de migração e foi corretamente recusado. Essas leituras verificam o índice, sem executar APKs.

Testes cobrem normalização/HTTPS, índices legados/v2/externos, pin, limites e gzip, política de certificados/classes/API, ponte Rx e cancelamento, identidade estável, persistência online, tamanho/registro adulterado e erros de API ausente preservando cancelamento. Teste Room de contagem online/progresso foi compilado para execução no Android.

Revisão independente verificou instalação nativa, pins, caches e integração. Corrigidos: limites de páginas divergentes, retorno online à lista de capítulos, LinkageError recuperável no leitor e retenção do contexto de páginas até o limite de um capítulo. Revisão final sem problemas Critical/Important pendentes.

## Limites e teste na TV

A execução de extensões, catálogo real do HQ Now/Saga do Infinito, confirmação do instalador, WebView, foco e leitura online não foram validados em dispositivo. APIs 1.2–1.6 hospedadas; compatibilidade depende de cada extensão e suas dependências. QuickJS indisponível. O capítulo aberto é registrado na biblioteca; a retomada renova URLs na fonte e requer internet. Não há download completo de sagas nesta versão.

Teste sugerido: Fontes → Adicionar Keiyoushi → Ver extensões → buscar HQ Now → Instalar e confirmar no Android → Atualizar instaladas → explorar fonte → buscar Saga do Infinito → abrir edição → usar barra/miniaturas/automático → voltar aos capítulos → reiniciar e continuar pela biblioteca. Repetir com outra extensão.

Os leitores locais e a transferência da rede continuam incluídos. Os resultados anteriores de 0.4.0 estão no histórico Git e no relatório de entrega daquela versão.
