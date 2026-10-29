# Capa da obra no HQ Now — verificação pontual

Em29/10/2026, consultados site público e fonte oficial pública da extensão:

- https://www.hq-now.com/hq/1079/saga-do-infinito — HTML inicial exige JavaScript.
- https://github.com/keiyoushi/extensions-source/blob/main/src/pt/hqnow/src/eu/kanade/tachiyomi/extension/pt/hqnow/HQNow.kt
- https://github.com/keiyoushi/extensions-source/blob/main/src/pt/hqnow/src/eu/kanade/tachiyomi/extension/pt/hqnow/HQNowDto.kt

A extensão consulta `https://admin.hq-now.com/graphql`. O DTO mapeia `hqCover` para `cover`, e a extensão copia essa imagem para `thumbnail_url`. A consulta por ID pede capa da obra; leitura de páginas é outro caminho. Não foi identificado uso de IMDb/TMDB/ComicVine para fornecer essa capa. Isso descreve a API observável, não a implementação privada do banco de dados do HQ Now.

Requisições públicas pequenas, sem credenciais e sem buscar páginas de capítulos:

1. `getHqsByName(name: "Saga do Infinito") { id name hqCover }` retornou ID1079 e `hqCover:null`.
2. `getHqsById(id:1079) { id name hqCover }` retornou a capa `http://static.hq-now.com/hqs/hqs/uploads/picture/image/398994/00Front_Cover_06.jpg`.
3. HEAD da mesma URL com HTTPS respondeu200; o endereço HTTP também respondeu200.

Diagnóstico comprovado no nosso código1.4.0: SagaStore descartava `coverUrl` sem prefixoHTTPS, portanto podia ocultar justamente a capa fornecida por essa fonte. A nova política valida HTTP(S) e converte endereços antigos HTTP para HTTPS, sem ignorar TLS ou fazer downgrade. Quando metadados salvos/busca não contêm capa, consulta a obra pela extensão e salva somente a imagem da obra no cache automático.

A chamada metadata-only do hospedeiro não solicita páginas. Algumas extensões (incluindo o fonte HQ Now inspecionado) retornam também metadados de capítulos em seu método de atualização, independentemente do flag; isso não é download de páginas da HQ. Não foi incorporado código da extensão: somente estudado o contrato e usado o adaptador existente do aplicativo.
