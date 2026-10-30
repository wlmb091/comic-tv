# Buscar → Navegador: foco preso na Home

Vídeo busca-navegação-erro.mp4 analisado sem áudio; capturas temporárias em build/browser-search-focus-contact.png. Busca possui atalhos funcionais; ao abrir navegador vazio, foco percorre barras superior/inferior, não grade; Voltar fecha rota e revela Busca anterior.

Causas em BrowserScreen/Chrome: Home é Compose sobre WebView vazio; efeito só distingue chrome/address de WebView/requestFocus, sem destinoHome. ▶Voltaràpágina desabilitado emaba vazia e ação page mantinha chrome=true. Back comchrome+URLvazia chamaonBack/rotasai. D-pad sem vizinhos explícitos entre bandas e grade.

Correção: browserFocusOwner Controls/Home/Page/None; WebViewrecebeinput apenasPage. Homegridtemrequester+focusRestorer; ↓daaba/↑dorodapé entramHome; focargrade recolhechrome; ▶funcionamesmoaba vazia. Voltarfecha controles, semsair imediatamente paraBusca; saída explícitaSairdonavegadorpermanece. Diálogos/downloads/fullscreen continuamownfocusecancelamrequestpendente; reentradaRESUMEDsegueproprietáriocorreto.

Verificações: RED testeJVM antesfaltavapolítica;GREEN build/browser-home-focus-green.log BUILD SUCCESSFUL1m35,34BrowserJVM0falhas eAndroidTestcompile. Revisão independente semP1/P2. Dois testesCompose deD-pad↑/↓ criados; compilaçãofinal BUILD SUCCESSFUL31s (build/browser-home-focus-android-compile.log); testesCompose NÃOexecutados. SemBox/ADB/emulador, nãoafirmarexecuçãoD-padfísica.

Mantém correçãoCBZlegado do mesmo lote; semnovoAPK/versão/push/publicação nestaetapa.
