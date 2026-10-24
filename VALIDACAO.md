# Validação — Comic TV 0.9.2

24/10/2026 · versionCode 26 · minSdk26.

- Temas Comic TV, Tema Flix, Tema Prime, Tema Max e Tema Cinema com fundos, painéis, botões, foco e progresso próprios. Presets anteriores e nomes de preferências preservados.
- Amostras das paletas nas configurações. Contraste alto e ampliação de texto preservados.
- Cabeçalho da biblioteca usa texto Montserrat ExtraBold incorporado: COMIC branco, TV na cor do tema, sem símbolo/fundo retangular. Fonte e licença SIL OFL incluídas no APK.
- Ações do destaque inicial neutras sem foco; preenchimento colorido acompanha foco do controle.
- Fundo principal continua sendo identidade desfocada, agora com camada da cor de fundo do tema a 94%; capa desfocada apenas nos detalhes.
- 170 testes JVM passaram (90 core, 10 mihon, 70 app). Novos testes verificam contraste de texto/ações e restauração de presets antigos/novos.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram após as últimas alterações. Instrumentação compilada, não executada.
- Assinatura apksigner e metadados aapt2 verificados: 0.9.2/code26/minSdk26.
- SHA256: 029640684D829B696C1FF41A1629D525B1AA0E6035C823DAC0A0B21558EE8246.
- Sem aparelho conectado: validar fonte, foco, cores, texto ampliado e persistência dos temas na TV. Imagens geradas são propostas visuais, não capturas do APK.
