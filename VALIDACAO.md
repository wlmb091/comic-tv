# Validação — Comic TV 0.9.5

24/10/2026 · versionCode 29 · minSdk26.

## Alterações

- Catálogo: títulos abaixo das capas, preservadas com ContentScale.Fit; legenda de duas linhas e estados de carregamento/falha. Cabeçalho com Montserrat ExtraBold, contador integrado e busca compacta. Três chaves de cabeçalho/offset de restauração preservados.
- Grade de catálogo adapta largura mínima ao tamanho de texto. Cabeçalho e busca continuam rolando com as capas.
- Fontes e extensões: ícones de 32 dp, cartões com altura flexível, nomes e estados em duas linhas. Fonte ativa indicada por texto e semântica de seleção.
- Abas de instaladas/atualizações/disponíveis com contadores e distinção entre seleção e foco.
- Extensões por repositório também em grade. Card instalado pode abrir suas fontes; instalação e atualização continuam com os fluxos existentes e confirmação do Android.
- Campos de busca arredondados, foco consistente com tema e borda reforçada por contraste alto. Estados vazios/carregamento explicam a próxima ação.
- Temas e Relacionados ocultos preservados; nenhuma IA adicionada.

## Verificação

- 178 testes JVM sem falhas/erros: 94 core, 10 mihon, 74 app.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram. Testes instrumentados somente compilados, sem execução em dispositivo.
- Assinatura apksigner verificada. aapt2: versão 0.9.5/code29/minSdk26.
- SHA256: FA32C0E6ACC62E0ACA3528E679770DB1ACE11CC56B42E7D8A76F54C2A4377770.

## Conferência na TV

Sem dispositivo conectado. Conferir fontes com nomes longos, todos os temas/tamanhos de texto, rolagem do catálogo, retorno de foco depois dos detalhes, busca sem resultado, imagem indisponível e as abas/grades de extensões. Aparência e fluxo real de instalação ainda dependem de validação na TV.
