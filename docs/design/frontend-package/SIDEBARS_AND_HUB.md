# Complemento do usuário — barras e hub

Recebido duranteThemeSpec/Max em28/10/2026. Aplicar as barras na etapa de seu tema, preservando conteúdo/dados/foco; não alterar todos os layouts ao mesmo tempo.

## Barras

Comic começa no trilho64px e expande ícones+nomes(~220px); Flix começa aberto220px e recolhe para trilho64px; Cinema painel direito fechado/alça, aberto327px. Medidas são do canvas1280×720 e devem ser normalizadas, mantendo alvos≥48dp/texto legível. Novo padrãoFlix substitui o anterior apenasrecolhido.

Sobrepor conteúdo sem empurrar/remedir; degradê escuro para contraste. Animar translação200–250ms, nunca largura. Abrir comD-pad na direção da barra somente na borda do conteúdo; direção oposta/Back fecham; confirmar destino recolhe. Recolher sem trocar rota devolve oúltimo foco/offset do conteúdo; escolher outro destino usa a entrada do destino, não requester de rota destruída. PreservarBack de diálogos/páginas/leitor/saída. Preferência sempreaberta/recolhida éopcional e não está implementada.

## Hub proposto

Primeira camada: metadados reais daobra/chips eSobre aobra; Nas telas paraadaptações; Notícias comtítulo/imagem/fonte/data/resumo eQR paraoriginal. Futuro: lançamentos,ordem de leitura,personagens,curiosidades. Navegação sugeridaInício/Notícias/Nastelas/Favoritos/Coleções; Home verticalhero→Continuarlendo→Nastelas→Notícias→Suacoleção. Não remover biblioteca completa nem duplicar rotaNovidades confundindo-a comnotícias sem definição.

Nenhum vínculo como“Baseado na sua leitura” éautomaticamente comprovado por uma capa/título: usarIDs/metadados deobra/edição e correspondência verificável. Não inventar autor/editora/ano/status/nota ou sinopse paraedições sem esses dados. Recurso éadicional ao frontend; usuário foi consultado sobre prioridade e uso pessoal/distribuição/monetização. Atéresposta, seguirThemeSpec/Max e registrar hub, sem credenciais/consultasnovas.

## Fontes verificadas e pendências

Resposta explícita do usuário: espera monetizar futuramente e quer concluir primeiro a fidelidade visual dos cinco temas. Hub adiado, sem implementação ou consultas remotas novas naHome; selecionar futuramente fornecedores compatíveis comuso comercial, não presumir que API gratuita de protótipo cobre monetização.

- [TMDB FAQ](https://developer.themoviedb.org/docs/faq),consultada28/10/2026: uso não comercial gratuito com atribuição; comercial exige tratar licença comTMDB. Definição pública liga uso comercial ao objetivo principal de gerarreceita; distribuição gratuita por si só não equivale automaticamente acomercial. Logoaprovado+aviso de nãoendosso emSobre/Créditos exigidos. Não integrar sem conferir termos completos e escopo final.
- [ComicVine API](https://comicvine.gamespot.com/api/): chave própria, somente não comercial,200requisições porrecurso/hora,cache ecréditos/link; termos adicionais de não redistribuição/concorrência devem seravaliados parao uso escolhido.
- AniList terms: tentativa https://docs.anilist.co/guide/terms-of-use retornou403; não confirmar condições sem documentação acessível. Jikan consultado,mas página semtexto recuperável; licença do software não prova permissão de todosos dados/imagens deMyAnimeList. Fontes ainda não aprovadas para integração.
- RSS: selecionar sites/feeds e verificar condições de cadaum. Resumo/link/QR e atribuição não substituem permissões do fornecedor; não copiar artigos inteiros.

Arquitetura proposta parafase dehub: rede assíncrona cancelável, orçamento de imagens/carregamento no tamanho exibido, cache limitado/último estado offline e expiração, Home independente de rede. Semserviço contínuo pesado/semchaves embutidas, sem conteúdo remoto tardio tomando foco. Nenhum código de integração neste complemento.
