# Validação — Comic TV 0.12.3

24/10/2026 · versionCode 35 · minSdk 26 · base 0.12.2.

## Escopo

TvChoice compartilhado para abas da biblioteca, filtros e escolhas de seção/tema/tamanho nas Configurações. Seleção em contorno/texto do tema sobre painel neutro; foco com preenchimento Action e texto OnAction. Semântica selected explícita. TvButton para ações, armazenamento e navegação preservados; sem dependência ou migração.

## Verificação

Teste Compose de seleção independente de foco preparado. Compilação anterior à implementação falhou por TvChoice ausente (work/theme-controls-red.log); isso é evidência de compilação, não execução do comportamento. Testes nativos requerem Android conectado.

Revisão independente não encontrou bloqueios: callbacks, modificadores de foco e caminhos de persistência preservados; pares de cores existentes continuam cobertos por ThemePaletteTest. AssembleDebug, assembleDebugAndroidTest, testes JVM e lint passaram em work/theme-controls-0123-final.log. 86 testes JVM do aplicativo aprovados, sem falhas/erros. APK assinado com o mesmo certificado da 0.12.1/0.12.2; versão 0.12.3/code35/minSdk26 confirmada.

## Aceitação física pendente

Conferir biblioteca/filtros/Configurações pelo D-pad, todos os temas, alto contraste e texto ampliado. Conferência das proporções da 0.12.2 também permanece pendente. Instalar como atualização sem desinstalar; manter USB conectado. Relatos anteriores em docs/validation/2026-10-24-mi-box-manual.md.

SHA256 APK: 365CD1409EE9932611A70C8F08FA5F1513C6D79BB05775E271644CD4AC6D853B
