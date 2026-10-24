# Validação — Comic TV 0.7.8

23/10/2026 · versionCode 17 · Android 8/API 26 ou superior.

## Alterações

- Detalhes da biblioteca com título e capítulo separados, progresso, metadados e resumo real armazenado pela fonte. Ausência de resumo é informada sem inventar texto. Carregamento local, sem abrir novamente a extensão.
- Ações de continuar e voltar ficam sempre compostas; painel de informações recebe foco e rola pelo D-pad. Detalhes da fonte separam informações e lista de capítulos em duas colunas.
- Ferramentas do leitor em ícones na lateral direita, com rótulo ao focar: continuar, páginas, capítulo anterior/próximo, lista de capítulos, mover/zoom, ajuste, opções e sair. Falhas oferecem nova tentativa.
- Miniaturas em faixa compacta de 220 dp, reservando espaço para a página acima. Indicador de página/zoom temporário substitui a ajuda inferior permanente.
- Capítulos abrem em painel esquerdo e mantêm uma prévia estática da página atual; capa serve como alternativa quando a prévia não está disponível. Voltar retoma o progresso salvo.
- Atalhos anterior/próximo respeitam a sequência da fonte e a preferência de inversão. Limites e capítulo ausente mostram uma mensagem sem escolher outro arbitrariamente.
- Fechamento do leitor cancela e aguarda suas operações antes de consultar outra edição. Gestos de OK e leitor EPUB de texto preservados.

## Verificação

- 151 testes JVM passaram, sem falhas ou erros: 87 core, 10 mihon, 54 app.
- Seis testes adicionados cobrem metadados/resumo e navegação entre capítulos, incluindo inversão, limites e capítulo ausente.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Assinatura APK verificada por apksigner; aapt2 confirma 0.7.8/code17/minSdk26.
- Verificador de ABI do DEX passou para o método padrão executável de GeneratedSerializer.
- Revisão identificou riscos de foco em botão fora da composição e rolagem do resumo; ambos corrigidos antes da compilação final.
- SHA256: 9A5BD64B9DFB0E637DC805AD7904A7E1E695228B05D6939BEF11957C925E082E.

## Limites e conferência na TV

Sem aparelho ou emulador conectado nesta etapa. Conferir foco dos ícones, leitura de resumo longo, faixa de miniaturas e retomada após selecionar capítulos. A prévia no painel de capítulos é estática. Resumos antigos ausentes não são buscados automaticamente. As referências guiaram a organização; esta versão não adiciona marcadores, compartilhamento nem efeitos FlipPager/CurlPager.
