# Livros EPUB, DOCX e MOBI/AZW — pacote 1.4.6

Pesquisa consultada em 30/10/2026. Implementação própria Kotlin/JVM usando apenas Jsoup já presente. Não foi transplantado código do Calibre nem acrescentada uma biblioteca de conversão ou execução de documentos.

## Fontes e escolha

- [Microsoft — estrutura WordprocessingML](https://learn.microsoft.com/en-us/office/open-xml/word/how-to-open-and-add-text-to-a-word-processing-document): documento, corpo, parágrafos e runs. DOCX é aberto como pacote ZIP e o corpo WordprocessingML é convertido em seções de texto nativo; relações de imagens internas preservam a posição na sequência.
- [W3C — EPUB 3.3](https://www.w3.org/TR/epub-33/): ofuscação de fontes não equivale a DRM. O leitor aceita apenas algoritmos IDPF/Adobe identificados em recursos declarados como fontes. Usa sua própria fonte nativa; não extrai nem instala fontes ofuscadas. Criptografia de conteúdo continua recusada.
- [Calibre — documentação do formato MOBI](https://github.com/kovidgoyal/calibre/blob/master/format_docs/pdb/mobi.txt) e [cabeçalhos do leitor mantido pelo projeto](https://github.com/kovidgoyal/calibre/blob/master/src/calibre/ebooks/mobi/reader/headers.py): estrutura Palm Database, marcador BOOKMOBI, registros de texto, sinalização DRM, encoding, PalmDOC e dados adicionais. Consultados para fatos do formato; nenhum código GPL foi incorporado. Decodificador independente suporta registros descomprimidos/PalmDOC, Windows-1252/UTF-8 e remove trailers antes da expansão. HUFF/CDIC e KF8 permanecem explicitamente não suportados.
- [Project Gutenberg — Alice, edição 11](https://www.gutenberg.org/ebooks/11): EPUB3 e MOBI para Kindle antigo usados como verificação real de leitura offline; originais preservados fora do repositório.

Não houve análise ou reconstrução de aplicativo empacotado nesta etapa. O escopo foi especificação/documentação e artefatos de livros; nenhuma dependência de renderizador externo foi adicionada.

## Comportamento e limites

DOCX: texto, quebras, tabelas linearizadas com separadores e imagens internas JPEG/PNG/WebP intercaladas. Não reproduz paginação/estilos/fontes do Word, objetos OLE, macros, alterações excluídas ou código de campos. Imagens de formatos não suportados recebem indicação textual. Imagens externas e caminhos fora do pacote são recusados; hyperlinks não são acessados. DOCX protegido por senha de Office não é decodificado.

MOBI/AZW: bytes selecionam o parser, independentemente da extensão. Suporta formato legado MOBI até versão 6, texto e imagens raster JPEG/PNG/GIF/WebP referenciadas por recindex. AZW só quando contém esse formato sem DRM. Recusa DRM antes de decodificar; não remove proteção nem pede chave. KF8/AZW3, KFX, HUFF/CDIC e dicionários complexos não são prometidos. Imagens remotas não são buscadas; atributos e scripts nunca são executados.

EPUB: mantém spine e imagens intercaladas, agora preserva separação de células de tabela e admite GIF. Aceita fontes ofuscadas identificadas sem confundir com DRM; fonte nativa substitui a embutida. CSS/layout fixo, SVG vetorial complexo, áudio, vídeo, formulários e JavaScript não são renderizados como experiência interativa. Um SVG que contém raster referenciado continua seguindo o caminho de imagem existente. Não é um renderizador universal EPUB.

Todos os novos textos usam PageRef `epub:text:` para reaproveitar leitor/narração/controle móvel existentes e seções de no máximo 12.000 caracteres, sem dividir surrogate pair. Limite de texto total 16 Mi caracteres; XML DOCX 8 MiB, relações 2 MiB; entradas/bytes individuais/total continuam limitados por ArchiveLimits. MOBI limita diretório, offsets, cada registro, expansão e conteúdo acumulado. XML é analisado por Jsoup com profundidade 128, sem resolver entidades externas. Originais não são alterados.

## Evidência local

Compilação isolada Kotlin 2.2.0/JDK17, sem executar Gradle global. `BookFormatsTest` + `EpubImportTest`: **14 testes, 0 falhas**. Fixtures cobrem sequência texto/tabela/imagens, Unicode e seções, chaves de página adulteradas, relações externas/traversal, PalmDOC/referências sobrepostas/trailers, registros e limites inválidos, recusa de DRM/KF8/HUFF, fontes IDPF/Adobe e recusa de criptografia de conteúdo.

Verificação real offline com indexação e extração de **todas** as seções:

| Artefato | Bytes | SHA-256 | Resultado |
| --- | ---: | --- | --- |
| `https://www.gutenberg.org/ebooks/11.epub3.images` | 189249 | `4f81245d31380ac1d6eafb9a321e3691369bc70985736ae4bccf849a4f784036` | 22 seções: 21 texto, 1 imagem |
| `https://www.gutenberg.org/ebooks/11.kindle.images` | 241000 | `1b4222ffdf1862a39d0fae6d42c588f28a34c12ad73cc21c2849fe2646cac3e1` | 14 seções de texto |

Os downloads reais não foram adicionados ao repositório. DOCX foi validado por fixtures estruturais, não por amostra de Word de usuário. Não houve execução física em Box ou Safari; compilação/extração JVM não demonstram aceitação visual nem suporte universal.
