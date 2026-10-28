# Max — relatório de implementação

THEME: Max

STATUS: partial

MATCHED: estrutura implementada comThemeSpec/scaffold,dock inferior7itens incluindoBuscar,artefullbleed,marcaMontserrat,títuloBebasNeue45sp,botão principal delineado,miniaturas inteiras eRecentes203dp,corpo14sp,faixa rolável,área segura48/27dp ealto contraste. Estes itens são conferência estática/compilação, não confirmação visual porcaptura.

DIFFERENCES: capturasHome/fococard/menu/faixa rolada ainda não obtidas; emuladorAndroidTV29x86 instalado eAVD1080p isolado criado, aceleração indisponível (`-accel-check`: driver não instalado); tentativa porsoftware permaneceoffline, semdadosruntime utilizáveis. Não afirmar960×540dp medido naBox. Desfoque de vidro embitmap ainda não implementado neste recorte, portanto não confundir superfícies translúcidas comblur real. Corpo permanece fonteAndroid;Inter ainda não incorporada. Scrim ainda usa os gradientes compartilhados anteriores, não paradas exatas do documento. Posição do título éresponsiva e não coordenada fixa.

DEVIATIONS (intentional):7itens porpedido posterior ao mockup6; textos violeta mais claros preservamcontraste, bordas/progresso usam#9B6DFF; tamanhos mínimos14sp/alvos48dp ealtura de dock medida prevalecem sobre altura fixa do desenho;130%pode rolar verticalmente. Grade/Histórico/coleções permanecem abaixo da primeira faixa para não remover funções. Max usa reserva196dp/mínimo180dp nohero epadding vertical0 na grade paraevitar corte inicial; outros temas preservam geometria anterior.

OPEN QUESTIONS: validação porcapturas requer emulador operacional ou aparelho deteste; habilitar aceleração/instalar driverWindows não foi feito. Nenhum resultado matches ou desempenho físico pode ser afirmado. Hub adiado porpedido explícito; monetização futura registrada.

FILES CHANGED: ThemeSpec.kt,ComicTvTheme.kt,ThemeNavigationPolicy.kt,ComicTvApp.kt,AppearancePreferences.kt,LibraryScreen.kt,LibraryHero.kt,PrimeLibraryHero.kt,CinemaBackdrop.kt,MainActivity.kt; testesThemeSpecTest,ThemeNavigationTest,MaxThemeCaptureTest;fonte/licençaBebasNeue. MudançasPrime/Flix/Comic/Cinema limitadas àrefatoração semnovo design. Fases visuais seguintes aguardam conferênciaMax conforme roteiro recebido.

FonteBebasNeue obtida do [repositório Google Fonts](https://github.com/google/fonts/tree/main/ofl/bebasneue), licençaSILOFL copiada emdocs/licenses eassets/licenses. SHA25608e4623805102d819f58601e46e345648846075e363b2ceb23313c2d1c83ec73. MarcaMontserrat mantida. Teste de captura gera figuras sintéticas somente no processo de teste; nenhuma arte fictícia é recurso da aplicação.

Verificação local: build/theme-spec-max-final.log aprovado em3m03/199tarefas;375casos JVM/373aprovados/2ignorados/0falhas/0erros;lint0erros/75avisos. Testes nativos preparados100%/130%/HC e4estados, ainda semexecução. Recompilação específica dos testes de captura após espera porimagem éregistrada embuild/max-capture-compile.log. Revisão estática inicial corrigiu risco de corteRecentes; revisão final semnova regressão de produção. Espera de fundo porseleção foi corrigida posteriormente; carregamento das capas ainda precisa ser observado ao executar.

Usuário escolheu explicitamente“Conferir o visual naBox”, recusando implicitamente a necessidade de configurar aceleraçãoWindows nesta etapa. Preparar1.2.4 assinada para atualização por cima,semclaim deaceitação física. Não instalar driver nemreiniciarWindows; fases visuais seguintes após relato doMax.

1.2.4/code66 publicada eAPK público verificado. STATUSpartial preservado;aguardando conferênciaMax naBox antes da próximaetapavisualPrime. Evidências emdocs/validation/1.2.4.md.
