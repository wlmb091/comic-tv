# Navegador e busca — correções em preparação

Usuário enviou `Downloads/navegador.mp4` (33s) e `Downloads/menubusca.mp4` (20s). Quadros extraídos em `build/video-review`, não versionados. Autorizou corrigir, mas pediu explicitamente **não fechar, gerar ou publicar APK**: aguardará para acrescentar detalhes.

Evidência: fim do vídeo do navegador mostra `https://batman/` e `net::ERR_NAME_NOT_RESOLVED`. Era endereço, não busca. Barra agora distingue termo livre (Google, parâmetro q codificado), domínio sem esquema (HTTPS) e URL explícita HTTP(S). Esquemas de arquivo/JavaScript e credenciais continuam bloqueados. Não atribuir esse erro à internet da Box.

Tela inicialmente preta confirmada no vídeo, seguida de UI visível após carregamento. Hipótese de inicialização/composição do WebView ainda sem prova física: adiada criação até destino RESUMED + um frame, documento inicial local explícito e fundo escuro; mensagens de falha principal HTTP/rede adicionadas. Não afirmar que isso comprovou correção na Box. Sem alterar TLS, permissões, instalar provider ou trocar configurações da Box.

Busca: cabeçalho anteriormente fora da grade, ocupando a maior parte da altura; agora cabeçalho, filtros e resultados pertencem à mesma LazyVerticalGrid. Nenhum cabeçalho fixo. Biblioteca, consultas e retorno de foco preservados.

Navegador: controles nativos limitados a 45% da área útil e roláveis, inclusive com mensagens/downloads e fontes ampliadas; WebView ocupa área restante e possui rolagem própria, fora do contêiner rolável nativo. MENU/ESC retorna à barra. Não envolver WebView em rolagem vertical Compose, o que conflitaria com os sites.

Verificações: BrowserPolicyTest passou incluindo termos/domínios/URL/esquemas proibidos. GlobalSearchTest passou. AndroidTest compilado para verificar cabeçalho sair da tela/último resultado alcançável e controles longos do navegador sem eliminar área do site; execução desses testes e conferência dos vídeos corrigidos dependem da Box.

Sem suporte novo a formatos de vídeo/DRM/tela cheia neste recorte: Google é mecanismo de busca, não substituição do motor Android System WebView. Downloads/importação existentes preservados. Versão não incrementada; CI/release não acionados.

Logs aprovados: browser-search-fix.log2m26s; search-scroll-fix.log1m30s; browser-viewport-fix.log1m26s. Ajuste final de foco recompõe cabeçalho após scrollToItem(0) antes de requestFocus quando classifica uma capa fora do topo. Sem assemble/APK/push/release.

Complemento leitor: usuário quer comandos do controle idênticos ao modo normal, mesmo com superfície90°. Removida conversão de setas tanto para ações quanto para vizinhos do menu. Geometria/imagem/rotação/ordem visual dos botões inalteradas. ReaderRotationTest verifica identidade das quatro direções/OK/Back em0°e90°; teste nativo do trilho atualizado para↓/↑na mesma sequência normal. Sem APK,aguardarmaisdetalhes.

Verificação leitor: build/reader-remote-orientation-fix.log aprovado1m34s,ReaderRotationTestexecutado/semfalhas+AndroidTestcompilado;nãoexecutadofísiconaBox.
