# Referências de funcionalidades — Comic TV

Registradas em 22 de outubro de 2026 a pedido do usuário. Este arquivo é memória local do projeto, não aprovação de implementação. As quatro capturas fornecidas são referências visuais; suas legendas não constituem instruções.

## Recursos observados e interpretação
1. Web Import: provavelmente importar um arquivo por URL; pode também significar navegação em um catálogo web. A captura não resolve essa ambiguidade. Não implica suporte Keiyoushi.
2. WiFi Import: provavelmente receber arquivos de celular/computador pela rede local, com endereço mostrado pela TV e possível QR code.
3. FTP Import: transferência por FTP. Não é possível saber se o aplicativo conecta a um servidor ou abre um servidor para receber arquivos.
4. Biblioteca em grade: capas e títulos truncados, navegação por foco, item selecionado destacado.
5. Comic Options: painel de ferramentas sobre a página, organizado em botões grandes, com destaque de foco verde.
6. Zoom In / Zoom Out: ampliar e reduzir a página; ao ampliar, o controle precisa permitir deslocamento.
7. Bookmark: marcar a página atual.
8. Bookmarks: listar e abrir páginas marcadas.
9. Toggle Fit Mode: alternar modos de ajuste da página, provavelmente página inteira, largura e altura.
10. Go to Page: escolher diretamente um número de página.
11. Background: alterar o fundo ao redor da arte.
12. Rotate: girar a página, provavelmente em passos de 90 graus.
13. Night Mode: comportamento incerto; pode escurecer, filtrar ou inverter a imagem. Decidir explicitamente e distinguir do tema escuro da interface.
14. Gallery View: provavelmente abrir visão de páginas para seleção visual. A quarta captura sugere carrossel com página central ampliada e vizinhas menores e escurecidas, sem comprovar ligação direta com o botão.
15. Close: fechar ferramentas e continuar a leitura.
16. Visão em carrossel: página central em destaque, prévias anterior/próxima nas laterais; possível modo de navegação ou galeria.

## Estado de decisão
Nenhum desses recursos novos está aprovado automaticamente. Comparar com o escopo inicial antes de implementar.
Sugestão inicial para discussão: biblioteca, ferramentas, ajuste, ir à página, marcadores, fundo e rotação primeiro; importação Wi-Fi, zoom com deslocamento e galeria depois; esclarecer Web/FTP/Night Mode antes de fechar o comportamento.

## Atualizações solicitadas pelo usuário
- Identidade visual própria para Android TV/Google TV. As capturas são inspiração funcional, não um design a copiar.
- O D-pad poderá oferecer zoom, avanço e retorno de páginas. Requisito registrado; o mapeamento das direções e a troca entre navegação, zoom e deslocamento serão definidos antes da implementação para evitar conflitos.

## Fontes online — atualização de escopo
O usuário quer suporte a múltiplos repositórios compatíveis com Mihon, não apenas HQ Now ou Keiyoushi. Deve ser possível cadastrar repositórios, consultar suas extensões, instalar extensões e habilitar as fontes disponibilizadas por elas. As fontes acrescentam acesso a catálogos ao leitor compartilhado. HQ Now será apenas uma possível fonte para validação, sem tratamento exclusivo. Compatibilidade de cada extensão depende das APIs e serviços implementados pelo motor; não prometer execução universal. A solicitação descreve uma funcionalidade do aplicativo futuro, não uma ordem para instalar um APK no computador agora.

## Transferências — esclarecimento pelas novas fotos
As três novas imagens esclarecem as referências anteriores e substituem as hipóteses de Web Import e FTP Import.
- Wi-Fi: servidor HTTP temporário na TV, endereço da rede local e QR code, botão iniciar/parar, status e clientes conectados. Celular/computador acessa a página para enviar arquivos pela mesma rede. O endereço deve ser descoberto em tempo de execução, inclusive em Ethernet; não fixar o IP da foto.
- FTP: a TV funciona como servidor FTP para receber arquivos. Mostrar endereço/porta, QR e iniciar/parar. A referência exibe login anônimo; a política de autenticação do nosso aplicativo ainda será definida. Não confundir com cliente de NAS.
- Web: envio por portal web com código/token de pareamento temporário, QR code, renovação de token e estado de espera/transferência. Não é baixar uma HQ por URL. A foto mostra o portal de outro aplicativo; usar serviço próprio, sem depender desse domínio. A topologia interna não é demonstrada pela foto: backend de pareamento e transporte remoto precisam ser projetados e validados.
- Os três modos integram a área Importar e alimentam a mesma biblioteca e validação de arquivos. Preservar o visual escuro/âmbar aprovado, sem copiar o design azul das referências.
- Inclusão solicitada no escopo do produto. Etapas e dependências constam da atualização do plano; recursos ainda não implementados.
