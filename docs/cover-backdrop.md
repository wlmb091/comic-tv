# Fundo com capa — 0.8.3

Configurações → Acessibilidade e aparência → Fundo dos detalhes alterna preto / capa desfocada. A escolha é persistida com as demais opções. Padrão continua preto; alto contraste mantém preto mesmo se a preferência de capa estiver ligada.

A tela de detalhes coloca uma cópia da capa em todo o fundo, incluindo margens. Ela é esticada para preencher a tela, desfocada e coberta por preto com opacidade 76%. Capa principal, informações e resumo permanecem em primeiro plano.

Compatibilidade Android 8: decode com amostragem até 256 px de dimensão, redução para 96×54 e desfoque separável em software no dispatcher IO, sem RenderEffect ou dependência nova. O bitmap final pequeno é ampliado pelo renderizador. Capa ausente ou inválida mantém o fundo preto, sem bloquear leitura. O desfoque só processa a cópia, preservando o arquivo original.

Atualização 0.9.0: por solicitação posterior do usuário, a arte oficial COMIC TV substitui o fundo preto padrão na biblioteca e nos detalhes; alto contraste permanece preto. A opção agora se chama Fundo da biblioteca e detalhes.

0.9.1: a tela principal usa exclusivamente a identidade Comic TV desfocada, com camada preta de 88%. A capa desfocada opcional aplica-se apenas aos detalhes.

0.9.2: a camada sobre a identidade desfocada usa a cor de fundo do tema com opacidade de 94%, preservando a identidade visual de cada preset.
