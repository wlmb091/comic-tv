# Foco, resumo e confirmação — 0.8.2

Evidência: vídeo do usuário mostra o conteúdo de Novidades/Catálogo enquanto o foco cheio aparece em Biblioteca. A lateral era recriada sem destino explícito de foco; a marcação ativa também usava latest da consulta remota, atualizada apenas ao terminar a busca.

Correção: rota da navegação determina o item ativo. Um pedido de foco é criado somente ao acionar a lateral e consumido uma vez no destino. Atualizações da fonte não movem o foco. Retorno do leitor/detalhes não gera novo pedido, preservando restauração do conteúdo. Os botões expõem semântica de seleção.

Resumo: o controle de scroll usava isFocused no painel e ignorava foco de seus botões. O modificador compartilhado textPanelScroll usa hasFocus e intercepta ↑/↓ enquanto houver texto para rolar, inclusive a partir do botão. Aplicado aos detalhes da biblioteca e da fonte.

Histórico: remover abre confirmação com aviso sobre manter quadrinho e página salva, Cancelar inicialmente focado e Remover do histórico. Só confirma a gravação após acionar Remover. Restaurar continua direto. Mesma confirmação nos detalhes e no menu das capas; aviso fixo removido.
