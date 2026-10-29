# Navegador TV · 1.3.0

Abra por Importar → Navegar e baixar arquivo ou Busca → Navegador. Digite HTTP/HTTPS (teclado apenas com OK), abra e use Entrar na página. MENU ou Escape devolve o foco ao endereço; Voltar percorre o histórico antes de fechar. Sair do navegador fecha diretamente.

O provedor Android System WebView do aparelho renderiza os sites. Não há bridge JavaScript, acesso file/content, janelas externas ou instalação de APK. Certificados inválidos e conteúdo misto são bloqueados. Websites podem não oferecer navegação adequada ao D-pad; o botão MENU sempre devolve a barra. Firmware sem WebView recebe alternativa de importação local/rede.

O download precisa de confirmação e aceita somente CBZ/ZIP/CBR/RAR/PDF/EPUB por HTTP(S). Blob/data/file/intent não são aceitos. Limite de 1 GiB, reserva de 64 MiB, cinco redirecionamentos, sem downgrade HTTPS. Cookies são consultados novamente para cada destino; não há cabeçalho Authorization nem Referer encaminhado. Algumas páginas que exigem Referer, DRM, popups ou protocolos próprios podem não funcionar; não se contorna essa exigência.

Uma operação por vez. Progresso usa bytes recebidos e o total quando o servidor informa; Cancelar e segundo plano interrompem rede e removem o parcial próprio. Downloads concluídos ficam no cache da sessão, disponíveis para Importar explicitamente; erro de validação conserva o arquivo para nova tentativa. Fechar a sessão limpa o download temporário. Importação usa a validação de arquivos e a deduplicação existentes; resumo de coleção informa sucesso/repetições/erros. Edição única oferece Ler agora/Ver detalhes; coleções aparecem na biblioteca. Originais externos, dados existentes e USB não são removidos.

Testes BrowserPolicyTest cobrem esquemas/credenciais, nomes, formatos, redirects, limites e servidor HTTP local com resposta truncada/limpeza. Verificação física WebView/D-pad/download depende da TV; nenhum resultado de build equivale a essa conferência.
