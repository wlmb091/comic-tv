# Correções do lote de fotos — 1.2.6

Autorização final do usuário: aplicar todas as observações após envio do lote. Base82addf3, árvore limpa. Uma publicação final; sem aprovações intermediárias, sem emulador/drivers, dados/USB/leitor preservados.

- [x] Flix: esticar capas de Recentes até preencher os retângulos (FillBounds, autorizado mesmo alterando proporção); Buscar sai do topo e entra na lateral acima de Biblioteca.
- [x] Prime: esticar capas de Recentes e coleção nos frames16:9; preservar carrossel superior não marcado.
- [x] Comic: esticar capa principal e miniaturas de Recentes marcadas; lateral recolhida retorna ao modelo anterior fixo; filtros/coleções marcados descem ao rodapé e capa principal cresce com espaço liberado.
- [x] Rodapés de Flix/Prime/Comic/Max: uma única faixa horizontal compacta com Biblioteca/Histórico/filtros/coleções/Gerenciar; manter foco, alvos48dp/14sp/130%. Usar rolagem horizontal quando número de coleções não couber sem cortar texto; não quebrar em duas linhas.
- [x] Max: Home sem seção Sua coleção; manter Recentes/círculos/rodapé/dock. Círculos de coleções abrem página dedicada, não filtro da Home; voltar restaura círculo/posição, detalhes preservam página de coleção. Nenhum dado/exemplar é removido. Busca/Histórico/Todas mantêm acesso à biblioteca completa.
- [x] Verificar localmente somente recorte afetado e compilar nativos; CI faz bateria completa. Publicar1.2.6/code68, conferir APK público e encerrar sem esperas redundantes.

Cinema não recebeu ajuste visual neste lote; rodapé comum pode receber o mesmo alinhamento sem mudar seu layout principal. Novas decisões de esticar substituem Fit apenas nas áreas marcadas; não alterar arquivos originais nem esticar wallpaper/carrossel não marcados.

Complementos implementados: foco somente noanelMax;Read/Detailsdesfocadossomente texto emFlix/Cinema/Max;fundo de capa comcamadapreta15%,semforegroundalpha. Main+JVMrecorte+AndroidTestcompile1m28 aprovados. Faltapublicar/verificarAPK público.
