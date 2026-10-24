# Validação — Comic TV 0.7.10

23/10/2026 · versionCode 19 · Android 8/API 26 ou superior.

## Alterações

- Seletor de páginas prioriza as miniaturas: selecionada com 194 dp de altura (antes 88), vizinhas com 170 dp (antes 72), números de 20 sp.
- Painel de 320 dp com espaço reservado no leitor para não sobrepor a página exibida.
- Fundo preto, textos claros, destaque amarelo e borda discreta. Barra reduzida a 8 dp de área e traço de 2 dp, com marcador menor.
- Seleção, confirmação, cancelamento, RTL e prévias sob demanda preservados; nenhum progresso é salvo apenas por explorar miniaturas.

## Verificação

- 153 testes JVM sem erros/falhas: 87 core, 10 mihon, 56 app.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Ajuste visual reversível: não foram adicionados testes que apenas reproduzem dimensões/cores.
- Sem aparelho ou emulador conectado: conferir legibilidade a distância e espaço das miniaturas na TV.

- Assinatura apksigner, metadados aapt2 (0.7.10/code19/minSdk26) e ABI DEX verificados.
- SHA256: DB01948301D497B29348A15E27E15F78DE2764FE2BF4EDE12C1D3FDE8F3B0511.
