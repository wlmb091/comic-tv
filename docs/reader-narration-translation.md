# Ouvir livros e traduzir na rede local — 1.1.0

Importe um EPUB de texto ou PDF com texto selecionável. No leitor, abra OK → Ouvir livro e traduzir. Em PDF, a opção também aparece em Imagem e mais opções. A voz só começa ao escolher Ouvir / continuar. O conteúdo de imagem não é enviado ao tradutor.

## Voz no Android

O Comic TV usa vozes offline em português disponibilizadas pelo Android. Não exige assinatura nem serviço de voz externo. A disponibilidade e instalação dos dados dependem do mecanismo instalado na Box; alguns aparelhos não trazem voz offline. Use Vozes nas configurações do Android e depois Atualizar vozes. Não há imitação de narradores reais.

Velocidades: 0,75×, 1×, 1,25× e 1,5×. O trecho falado fica destacado; algumas vozes só informam o trecho inteiro, outras informam palavras. O texto acompanha a fala. Fonte ajustável de 18 a 40. ↑ ↓ rolam e pausam; ← → trocam página/seção; OK pausa e abre controles. Voltar retorna à imagem. Sair, trocar opções, ir ao segundo plano ou perder foco de áudio pausa; não retoma automaticamente.

O ponto salvo retoma no início do trecho, somente se página e conteúdo coincidirem. Original e tradução têm pontos separados. Preferências de voz e pontos de narração são locais e ainda não integram o backup da biblioteca.

## Tradução no computador

A Box manda texto para um LibreTranslate no seu computador pela rede local. O computador precisa permanecer ligado, com o serviço em execução. Depois do download inicial dos modelos, a tradução funciona sem nuvem. A voz continua sendo gerada na Box. Não é necessário Docker nem tradutor instalado na Box.

O computador precisa de **Docker Desktop com suporte a contêineres Linux**, instalado separadamente. Esta entrega inclui configuração e instruções; não instala Docker, não altera firewall e não inicia serviço automaticamente. Consulte os requisitos/licenciamento do [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/).

1. Instale/abra o Docker Desktop no computador. Deixe PC e Box na mesma rede privada.
2. Abra `tools/translation-server` do código-fonte. Copie `.env.example` para `.env` e coloque o IPv4 privado do **computador** (veja `ipconfig`), por exemplo `192.168.0.10`. Não use o IP da Box.
3. No PowerShell nessa pasta, rode `docker compose up -d`. A primeira inicialização baixa imagem/modelos inglês e português; pode demorar. Veja o andamento com `docker compose logs -f`. Ctrl+C sai dos logs sem encerrar o serviço.
4. No computador, teste `http://SEU-IP:5000/languages` no navegador. Precisa retornar inglês e português. Se o Windows pedir firewall, autorize apenas sua rede privada; não abra portas no roteador nem publique o serviço na internet.
5. Na Box: Ouvir livro e traduzir → Configurar computador. Informe `http://SEU-IP:5000`, deixe a chave vazia para essa configuração e escolha Testar conexão e consultar idiomas. Salve e escolha Traduzir para português.
6. Para encerrar: `docker compose stop`. Para ligar depois: `docker compose start`. Os modelos permanecem no volume. Não use `down -v` se quiser conservar os modelos.

Esta configuração é para uma rede doméstica confiável: usa HTTP local e não exige chave. Texto trafega pela rede até o computador escolhido. Para um servidor já protegido por chave, o aplicativo aceita a chave opcional; ela e o endereço ficam privados no app e fora do backup. Não cadastre serviço público. O cliente aceita apenas IPv4 privado/local e não segue redirecionamentos nem proxy.

Para outro idioma, edite `LT_LOAD_ONLY` no Compose (por exemplo `en,pt,es`) e rode `docker compose up -d` novamente com internet para obter os modelos. Consulte os idiomas disponíveis no app antes de traduzir. Modelos dependem da disponibilidade do LibreTranslate/Argos; a lista do app vem do servidor.

## Cache e limites

Mantém a página atual e até duas seguintes, com caches limitados. Tradução é feita em blocos de até 1.800 caracteres, sem enviar o livro inteiro de uma vez. Fonte de texto/tradução é limitada a 120.000 caracteres por página/seção; PDF de até 64 MiB e 5.000 páginas. Extração PDF usa arquivos temporários com limite de 128 MiB, preservando reserva de 64 MiB livres; limpeza ao fechar.

PDF escaneado, HQ de imagens e EPUB de imagens precisam de OCR, que não está nesta versão. Ordem de leitura de colunas PDF depende da extração. Tradução é automática e pode errar. Sem computador/rede ou em caso de erro, escolha Usar texto original; o arquivo original é preservado. O cache de tradução desta sessão é temporário, sem download offline permanente de traduções.

## Base e validação

Servidor separado baseado no [LibreTranslate 1.9.6](https://github.com/LibreTranslate/LibreTranslate/releases/tag/v1.9.6), imagem oficial fixada por digest, configuração de modelos/volume baseada no [Compose oficial](https://github.com/LibreTranslate/LibreTranslate/blob/v1.9.6/docker-compose.yml) e [opções oficiais](https://github.com/LibreTranslate/LibreTranslate/blob/v1.9.6/libretranslate/default_values.py). O servidor é AGPL-3.0 e não está embutido no APK. O app usa a API HTTP. Nenhuma integração ML Kit de tradução para TV foi adicionada.

Testes automatizados exercitam cliente local com servidor simulado, limites/cancelamento, controles por eventos e persistência. Testes nativos de PDF são compilados; a voz real, o Docker/modelo e o desempenho ainda precisam ser verificados no computador/Box. Não há promessa de desempenho ou disponibilidade de vozes antes desse teste.
