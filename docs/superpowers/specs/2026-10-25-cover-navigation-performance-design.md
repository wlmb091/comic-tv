# Carregamento de capas e fluidez da navegação

Atualização25/10/2026: usuário aprovou executar a primeira etapa após a auditoria entregue em outputs/AUDITORIA-FLUIDEZ-1.1.11.md. Plano executado em docs/superpowers/plans/2026-10-25-navigation-performance.md. Incluiu cache limitado de catálogos por fonte/modo/busca/página, TTL45s novidades/180s catálogo, seis entradas/200títulos por entrada/2MiB de metadados estimados; invalidação em atualização de fontes/confiança. Incluiu diagnóstico agregado local e correção solicitada durante execução: pasta/Trocar fonte e escolher fonte mantêm foco no conteúdo e barra recolhida. Reutilização do classloader, mudança do bloqueio global de download e organização visual dos botões seguem etapas posteriores.

## Prioridade do usuário
Adiar novos modos de mangá/webtoon. Usuário considera a leitura boa por enquanto e indicará ajustes no uso diário. Priorizar carregamento de capas nas telas atuais e fluidez da navegação; preservar leitor, zoom, filtros e renderização das páginas.

## Diagnóstico inicial (inspeção, sem medição física)
- LibraryScreen recalcula recent/filter/map de modelos de capa a cada recomposição provocada pelo foco. Trabalho depende da quantidade de edições, mesmo sem mudança de dados/filtro.
- TitlePreviewResolver tem cache de resultados e limite de duas consultas, mas não une consultas simultâneas da mesma chave; duas chamadas podem atravessar os slots antes de qualquer uma preencher o cache.
- As consultas por capas ausentes são disparadas por item composto da grade; composição não equivale a visibilidade real. Isso pode gastar os dois slots em itens fora da área visível.
- IdentityBackdrop relê/decodifica/processa a mesma identidade estática quando uma nova instância da tela aparece. O bitmap final é pequeno (96x54), mas o preparo não é reaproveitado entre instâncias.
- Coil já possui caches e a1.1.4 introduziu orçamento de RAM adaptativo e pré-carregamento limitado. Não presumir ausência de cache nem prometer ganho sem medir.

## Abordagem recomendada
Reduzir trabalho repetido e dar prioridade ao que está visível. Aumentar globalmente memória/disco foi considerado, mas não resolve consultas duplicadas/recomposição e não há evidência que justifique elevar os limites na Box. Reescrever a interface também não é necessário para investigar os caminhos identificados.

## Pacote proposto
1. Unir consultas simultâneas de uma mesma capa por fonte/título, preservando cancelamento, escopo de fonte, limite de duas consultas e tentativas posteriores após falha. Testar contagem real de chamadas com concorrência/cancelamento.
2. Resolver metadados das capas ausentes prioritariamente para a janela visível do catálogo, sem disparar consultas para cada item apenas composto. Manter prefetch de imagens próximas limitado e cancelável ao sair da tela. Medir chamadas iniciadas/duplicadas em cenário controlado; não adicionar downloads de capítulos.
3. Memorizar listas/índices/modelos derivados da biblioteca enquanto dados/filtros não mudarem, preservando busca/histórico/foco/restauração. Testar invariantes e compilar testes de UI; desempenho real depende de teste físico.
4. Reutilizar o resultado pequeno da identidade de fundo entre telas, com limite explícito e invalidação apropriada; preservar aparência/tema e compatibilidade Android11. Não guardar bitmaps grandes nem aumentar orçamento global do leitor.

## Verificação e limites
Estabelecer cenário repetível antes/depois: abrir biblioteca, percorrer capas, abrir/voltar de detalhes, catálogo com capa ausente e repetir a consulta. Testes devem comprovar menos operações para entradas equivalentes e ausência de consultas duplicadas, não apenas repetir a implementação. Build/testes/lint/assinatura de atualização; medir no aparelho se houver acesso disponível sem alterar USB/depuração. Não afirmar FPS/latência da Box a partir de testes JVM. Fontes e rede podem continuar limitando a primeira imagem.

## Fora deste pacote
Novos leitores, mudanças visuais, animações adicionais, IA, mudanças nos originais, aumento arbitrário de cache, leitura/páginas/zoom e alterações na configuração da TV Box.
