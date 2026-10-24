# Tradução local — adendo à narração

O usuário pediu tradução no mesmo pacote mesmo demorando mais. A proposta inicial de ML Kit foi descartada antes de adicionar o SDK: os termos exigem autorização prévia para TVs. O usuário então escolheu tradutor no computador pela rede local e confirmou “pode ser assim” após a explicação do funcionamento.

## Arquitetura final aprovada

- Cliente Android compatível com a API do LibreTranslate, sem serviço externo pago/nuvem. O programa de tradução roda no computador ligado na mesma rede; modelos são preparados no computador.
- Usuário cadastra IPv4 local/porta, idioma de origem e chave opcional. Não aceitar hosts públicos, credenciais embutidas ou query na URL; não seguir redirecionamentos e não fazer fallback para provedor remoto.
- Texto original nunca é sobrescrito. Modo de texto/narração oferece Traduzir para português e Voltar ao original. Mostrar claramente Tradução automática. A voz do Android lê o texto atualmente exibido.
- Apenas EPUB/PDF com texto; OCR segue adiado. Idioma de origem manual, listado pelo servidor. Destino português; qualidade/variante dependem dos modelos do servidor.
- Transmitir página/seção atual e até2 seguintes conforme o cache de leitura. Segmentos até1.800 caracteres, sequência de chamadas limitada, resposta de rede até64KiB por chamada e texto final até120.000 caracteres por página. Caches limitados a3 páginas e16MiB; não traduzir o livro inteiro.
- Mudar original/tradução, servidor, idioma ou página pausa fala e invalida trabalhos anteriores. Retomar apenas por ação explícita. Ponto fino usa identidade de idioma/servidor e hash do texto para não aplicar offset do original à tradução.
- Falha no computador/rede preserva o original e oferece retorno a ele. Conectar/testar servidor não inicia narração nem altera o arquivo. A tradução inicia desligada quando reabrir.
- Endereço/chave ficam privados no aparelho e fora do backup. Avisar no diálogo que os trechos serão enviados ao computador cadastrado; sem envio de livro inteiro ou upload de arquivo original.

## Programa do computador

Fornecer guia e exemplo Docker Compose usando imagem oficial do LibreTranslate, com modelos en/pt inicialmente e armazenamento persistente. Não instalar Docker, baixar todos os idiomas ou abrir serviço público automaticamente no computador do usuário. A instalação do servidor é uma etapa própria documentada, como foi explicado e aceito. Modelo/outros idiomas podem ser preparados conforme necessidade no servidor.

## Validação

JVM/fakes e servidor HTTP de teste local: endereço público/malformado proibido, sem redirecionamento, protocolo JSON, limite de resposta, original intacto, cache por idioma/modo, cancelamento/callbacks atrasados e ponto de narração separado. UI nativa compilada; testes reais exigem Box e servidor configurados. Não afirmar execução real de tradução/voz até teste no aparelho.

Referências: https://docs.libretranslate.com/guides/installation/ e https://docs.libretranslate.com/api/operations/translate/. Restrição que descartou ML Kit: https://developers.google.com/ml-kit/language/translation/translation-terms.
