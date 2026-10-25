# Importação automática de coleção compactada

## Objetivo e escolha do usuário
Importar todas as HQs de um ZIP ou RAR automaticamente, cada arquivo interno como edição independente na biblioteca. Integrar ao seletor local e envios pela rede local/FTP existentes. Usuário escolheu importar todas, dispensando lista de seleção.

## Alternativas
Lista prévia permite seleção, mas adiciona passos no controle remoto. Importação automática escolhida reduz o trabalho para coleções completas. Apenas descompactar no computador mantém a limitação atual e não atende ao pedido.

## Comportamento proposto
- Detectar pacote contendo CBZ, CBR, ZIP, RAR, PDF ou EPUB; percorrer subpastas e importar arquivos suportados em ordem natural.
- Manter importação comum de HQ com imagens diretamente dentro do arquivo. Pastas de imagens não serão separadas automaticamente em edições; cada HQ da coleção deve ser um arquivo próprio.
- Um nível de pacote: importar cada arquivo interno como HQ, sem abrir coleções dentro de outras coleções recursivamente.
- Processar uma HQ por vez, mostrar contagem/progresso e apresentar resumo final de importadas, repetidas e falhas. Não abrir automaticamente os detalhes de uma edição ao terminar um lote.
- Repetições de conteúdo idêntico entre importados devem ser reconhecidas sem alterar progresso da entrada existente. Reutilizar restauração pendente por impressão digital quando aplicável.
- Arquivo interno inválido gera falha individual e não impede outras HQs válidas. Interrupção preserva as edições já concluídas e limpa temporários; repetir envio não deve duplicá-las.

## Limites e armazenamento (proposta técnica)
Preservar limite atual de 1 GiB para arquivo recebido; coleção com no máximo 100 HQs e 2 GiB de conteúdo extraído total, inclusive entradas ignoradas. Reutilizar limites dos leitores para cada HQ. Sem senha, volumes divididos, links ou caminhos que escapem do diretório temporário. Usar nomes temporários gerados, extração em disco e verificação de espaço disponível, sem manter coleção inteira na RAM. Remover somente temporários próprios, preservar arquivo de origem/USB e não alterar cache ou downloads offline de outras edições.

## Verificação
Testes de ZIP/RAR, subpastas, ordem, formatos mistos, repetição/reenvio, falha individual, cancelamento, limites de bytes/quantidade, nomes maliciosos e fluxo simples existente. Compilar debug/release e testes Android; assinar atualização com certificado atual. Desempenho, seletor e foco na Box exigem teste físico e não serão declarados verificados por compilação.
