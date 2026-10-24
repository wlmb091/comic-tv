# Ouvir livro — EPUB e PDF com texto

## Intenção e decisões do usuário

Adicionar narração para acompanhar a leitura na Box, sem contratar serviço externo. O usuário escolheu explicitamente as vozes disponíveis no Android. A primeira etapa proposta e solicitada para implementação cobre EPUB de texto e PDF com camada de texto; OCR para páginas digitalizadas fica para uma etapa posterior.

Base atual: Comic TV1.0.0 nativo Kotlin/Compose, Android mínimo26; aparelho relatado MiTV-AFKR0/Android TV11. Preservar a versão estável, assinatura, dados, USB de armazenamento expandido e design aprovado. Nenhuma publicação ou integração comercial nova faz parte desta função.

## Abordagens consideradas

1. **TextToSpeech do Android + extração local de texto — escolhida.** Aproveita mecanismos instalados, sem conta/API paga ou envio intencional do livro a um serviço contratado. A qualidade/disponibilidade das vozes depende do aparelho.
2. **Serviço online de narração — não escolhido.** Poderia oferecer vozes diferentes, mas acrescentaria custo, conexão, tratamento de dados e credenciais.
3. **OCR e narração de qualquer PDF/imagem — adiado.** É uma função separada: reconhecimento, ordem de leitura e qualidade de digitalização exigem validação própria.

O usuário escolheu a primeira abordagem na conversa. As decisões técnicas e limites a seguir são propostas do implementador para revisão, não escolhas atribuídas ao usuário.

## Experiência proposta

- Ação **Ouvir livro** nas opções de leitura de EPUB com texto e **Texto e narração** nas opções de PDF local. Quadrinhos/imagens sem texto não oferecem narração fictícia.
- EPUB mantém o leitor de texto atual. No PDF, abrir um modo de texto extraído com a página correspondente e ação para voltar à visualização original. Isso permite destacar/rolar texto sem prometer alinhamento palavra a palavra sobre a imagem PDF.
- Controles acessíveis pelo D-pad: Reproduzir/Retomar, Pausar, Parar, trecho anterior/próximo, voz e velocidade. Velocidades iniciais0,75×/1×/1,25×/1,5×, padrão1×. Seleção de voz identifica as variantes em português disponíveis no mecanismo instalado.
- A narração só começa por ação explícita; restaurar estado, reabrir livro ou receber callback atrasado nunca inicia áudio sozinho.
- Enquanto narra, destacar o trecho e rolar para mantê-lo visível. Quando o mecanismo fornecer onRangeStart, usar os intervalos da fala; quando não fornecer, destacar o segmento/parágrafo inteiro. Não prometer precisão universal por palavra.
- Ao concluir um segmento, seguir para o seguinte. Ao concluir a página/seção, avançar pela sequência já indexada; não usar o temporizador de leitura automática em paralelo.
- Abrir opções, trocar página/seção manualmente, rolar manualmente, trocar edição, sair, perder foco de áudio ou ir ao segundo plano pausa. Retomada exige ação explícita. Controles de narração podem reabrir/fechar o painel sem alterar o ponto salvo.
- Ao pausar, retomar do início do último segmento em andamento, evitando prometer um ponto exato que todo mecanismo TTS não fornece.
- PDF sem texto extraível pausa e informa que a página pode ser digitalizada; oferecer próxima página ou retorno à imagem. Não pular páginas silenciosamente nem classificar toda falha de extração como ausência de texto.

## Voz e compatibilidade

Usar TextToSpeech instalado e enumerar vozes em português que não exigem conexão e não sinalizam dados ausentes. Priorizar pt-BR, depois pt; nenhuma voz online será selecionada automaticamente. Se não houver voz compatível ou mecanismo inicializado, explicar a indisponibilidade e oferecer tentar novamente/abrir configurações de voz quando o aparelho suportar. Não instalar APKs de voz diretamente.

Adicionar apenas a visibilidade de serviço TTS necessária no manifesto. Sem microfone, gravação de voz, clonagem de pessoas, API externa, serviço contínuo, reprodução em segundo plano ou exportação de áudio nesta etapa. Não garantir que a Box já possui a voz offline; isso precisa ser conferido no aparelho.

## EPUB e PDF

O EPUB já produz PageImage.text a partir de seções de até12.000 caracteres. Reaproveitar esse texto e sua sequência, sem alterar arquivo original ou confundir seção interna com capítulo editorial.

O PDF atual usa PdfRenderer apenas para imagem. PdfRenderer.Page.getTextContents exige API35, acima da BoxAPI30; por isso não serve como única implementação. Proposta: PDFBox-Android2.0.27.0 (Apache2.0), inicializado uma vez e usado apenas para extração de texto em worker. Empregar ordenação por posição e normalização conservadora; PDFs com colunas/fontes sem mapeamento podem produzir ordem ou texto imperfeitos.

Limites iniciais para narração PDF: original até64MiB, até5.000 páginas já aceitas pelo leitor, saída até120.000 caracteres por página e scratch temporário até128MiB, com reserva de64MiB de espaço livre. Arquivos maiores continuam disponíveis no leitor visual, com mensagem de limite específico para narração. O parser usa scratch em disco para reduzir uso de heap; nenhum documento é carregado em bitmap para extrair texto.

## Arquitetura e estados

- Separar planejamento/segmentação e estado de narração (testável em JVM) do adaptador AndroidTTS e da extração PDF.
- Estados explícitos: indisponível, preparando texto/voz, pronto/pausado, falando, concluído e erro recuperável. Erro de fala/extração não deve apagar o progresso nem derrubar a leitura visual.
- Fala em segmentos limitados ao máximo aceito pelo TTS, respeitando limites Unicode e preferindo quebras de parágrafo/frase. IDs incluem geração/documento/página/segmento; callbacks de geração antiga não alteram UI, progresso ou navegação.
- Um extrator PDF por sessão, com acesso serializado e fechamento em finally após cancelar/join de trabalhos pendentes. Extração, inicialização do parser, hash e escrita de posição fora da thread principal.
- Cache em memória limitado à página/seção atual e às próximas2, com teto16MiB. Antecipar texto, sem sintetizar o livro inteiro. Caches inválidos não devem produzir narração de outra página/edição.
- Scratch apenas em diretório temporário próprio, com limpeza ao fechar e recuperação de sobras no próximo início da extração. Nenhuma operação de limpeza toca originais, capas, dados de biblioteca ou downloads completos.
- AudioAttributes de fala e AudioFocus: pausar com perda de foco, não retomar automaticamente ao recuperá-lo. Parar/fechar libera TTS e foco. A leitura automática existente é suspensa quando a narração começa e não liga sozinha ao terminar.

## Persistência

Salvar voz/velocidade localmente. Posição de narração por edição inclui página/seção, hash do texto e início do segmento. Salvar em mudança de segmento/pausa/saída com ordenação que impede escrita atrasada sobre o ponto mais recente.

Ao reabrir, restaurar ponto sem iniciar áudio. Se a página ou o hash do texto não corresponder, usar o início da página atual, preservando o progresso normal da biblioteca. A posição fina e preferências de voz não entram no formato de backup nesta primeira etapa; o backup existente continua preservando a página/seção e demais dados já suportados. Não migrar o banco para isso.

## Testes e entrega

- JVM: segmentos longos, espaços/quebras, Unicode/surrogates, limites, callbacks atrasados, pausa/retomada por segmento, avanço no fim de página, erro/ausência de voz, alternância manual e exclusão mútua com leitura automática.
- Android compilados: PDF gerado com texto versus página só imagem, limites/fechamento/cancelamento, falha TTS simulada e foco dos controles. Testes não usam vozes pagas ou rede.
- Build/lint/assinatura/ABI e migrações existentes mantidos. Acrescentar licença/NOTICE da nova dependência e registrar aumento real do APK.
- Aceitação manual na Box: disponibilidade de português offline, reprodução audível, velocidade, destaque/rolagem, pausa/retomada, troca de página, fim de livro, Voltar e segundo plano. Sem ADB não afirmar execução instrumentada.

## Não objetivos

OCR, audiolivros gravados/MP3, imitação de narrador real, catálogo OPDS, nuvem, sincronização, histórico de áudio, destaque preciso na imagem PDF e reprodução com app fechado. Não redesenhar biblioteca/telas de capítulos neste pacote.

## Referências técnicas

- https://developer.android.com/reference/android/speech/tts/TextToSpeech
- https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener
- https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page
- https://github.com/TomRoush/PdfBox-Android

Estado: proposta escrita após escolha das vozes Android. Builder check independente respondeu “nothing important left”. Aguardando aprovação do documento antes de código/dependências.
