# Modo Cinema manual — Implementation Plan

## Escopo ampliado aprovado em27/10/2026

Implementar os cinco layouts próprios de docs/design/themes/README.md e suas imagens, por pedido explícito posterior. Comic TV/estante, Flix/lateral, Prime/menu superior, Max/dock inferior e Cinema/imersivo. O plano inicial abaixo registra a base de artes manuais; não limita mais o pacote à composição anterior. Remover Lima/Rosa/Laranja das escolhas com migração legada para Comic TV. Vidro80%, alto contraste100%. Navegação por tema deve preservar contratos de primeira opção, restauração de foco, dados tardios e todas as seis seções.

- [x] Registrar aprovação persistente e copiar cinco imagens para docs/design/themes.
- [x] Navegação própria por tema e handoff D-pad nas orientações superior/inferior/lateral.
- [x] Biblioteca/hero próprios: estante editorial, destaques panorâmicos e Cinema imersivo.
- [x] Integrar arte manual, preferências e legado, executar verificações e revisar conjunto;365casos/0falhas, lint0erros, build e AndroidTest compile passaram. Físico ainda manual.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans; armazenamento pode ser delegado como domínio independente com contrato explícito.

**Goal:** Implementar cinco layouts aprovados, arte horizontal manual separada da capa e vidro80% (alto contraste100%).
**Architecture:** Política nativa de navegação por preset; biblioteca compartilha dados/ações com composição de destaque própria. Cinema imersivo sempre admite sua arte manual; preferência cinemaMode opcional permite arte nos destaquesFlix/Prime/Max, false por padrão e opcional no backupV1. Sem servidor/API/IA. Arte por edição escolhida em Detalhes/Mais opções viaOpenDocument, cópia própria preservando bytes; associação coletiva por série/fonte será posterior. Store em filesDir/cinema-artwork, fora dos caches de leitura; commits/exclusão sob mutex da biblioteca e revalidação DAO. Arte não entra no backup de dados; preferência entra. Renderização local só da seleção atual, espera180ms e cancelamento ao trocar/sair; decode conforme viewport/RAM, sem memória/discoCoil adicional para artes.
**Tech Stack:** Kotlin/JVM core, AndroidBitmapFactory, SAF, ComposeTV, Coil existentes/API26.
**Spec:** Direção apresentada e aprovada pelo usuário: Modo Cinema opcional, manual primeiro, arquivo horizontal distinto de capa, degrade, cache limitado. Escolhas técnicas abaixo são decisões do implementador, não alegações de requisitos do usuário.

## Constraints / choices

- jpg/png/webp estático validado por conteúdo, até20MiB, landscape>=1280x720, máximo40MP/16384por eixo. Recomendar1920x1080/16:9. Não esticar imagem pequena nem alterar arquivos de origem; arquivo copiado sem recompressão.
- Até128MiB de artes persistentes, reserva64MiB; limite é falha orientada, sem apagar artes silenciosamente. Preparação em disco, troca atômica, original anterior mantido se falhar/cancelar. Paths/IDs limitados e sem links externos.
- Vidro80% pela última decisão (substitui proposta70% e base96.9% da1.1.22). #0F0F0F/#404040/botões neutros e demais cores mantidos; alto contraste opaco.
- Sem mudança no leitor/capítulos/trust/cache256+32MiB, capas, originais, dados ou USB. Sem afirmar ganho/FPS/qualidade física/instalação na Box por build.
- Arte de destaque só naBiblioteca; título/foco/offset existentes preservados, sem autofocus por arte tardia. Navegação temática cobre as seis seções; leitor e detalhes preservam sua composição funcional. Busca externa não integra este pacote.

## Tasks

- [x] Store core e testes: staging limitado/cancelável, validação injetada, troca/ref atômicos/versionados, quota/reserva, remoção/segurança de caminhos.
- [x] Android validator e integraçãoLibraryRepository/DAO/deleção/storageReport; seletor de arte em Mais opções com escopo local, feedback/cancelamento/remoção confirmada.
- [x] Preferência opcional/persistência/backup retrocompatível e controlesSettings; renderCinema naBiblioteca/hero com seleçãoatual e fallback seguro.
- [x] Ajustar vidro80% e testes. Testes unitários significativos/AndroidTest compilados/build/lint/SQL/ABI; revisãoindependente final sem bloqueantes.
- [ ] Documentar, versão1.2.0/code62, publicação automática autorizada e verificação de manifesto/APK público. Conferência física ainda manual.
