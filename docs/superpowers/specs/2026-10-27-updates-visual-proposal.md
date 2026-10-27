# Proposta: organização visual de Atualizações

Status: escopo aprovado pelo usuário em 27/10/2026; implementação 1.1.20.

## Objetivo

Oferecer uma mudança visível e útil na próxima atualização para conferir na Box, começando pela tela usada no teste do atualizador. O usuário escolheu retomar organização visual e receber uma proposta após conferência das telas.

## Situação atual

AppUpdateSettings já mostra versão instalada/disponível, porcentagem, mensagens de estado e novidades. As informações são empilhadas e o botão Verificar novamente permanece ao lado da ação principal em todos os estados, embora a consulta não execute durante operações ocupadas. Preservar o alvo principal estável já usado para manter foco durante transições.

## Alternativas

1. Recomendada: organizar apenas Atualizações. Mudança fácil de reconhecer, escopo pequeno e diretamente ligada à instalação de teste.
2. Reorganizar Configurações inteira. Mais abrangente, mas envolve abas e mais rotas de foco; deixar para pacote separado.

## Desenho proposto

- Resumo compacto no topo: versão instalada e, quando houver, nova versão disponível, com hierarquia clara e cores do tema.
- Estado atual legível junto ao resumo: consultando, disponível, baixando, conferindo, pronta para instalar, aguardando Android ou falha. Refletir os estados reais existentes; não inventar porcentagem durante conferência nem afirmar instalação concluída antes da confirmação real.
- Barra de download com porcentagem e volume em MiB legíveis.
- Ação principal em posição estável, mantendo foco ao mudar entre baixar, cancelar e instalar. Verificar novamente aparece somente nos estados em que a consulta pode executar, sem remover um controle que ainda detenha foco.
- Novidades em área de leitura rolável pelo D-pad abaixo das ações; instrução curta sobre confirmação Android.
- Layout que acomode texto de 85–130%, alto contraste e os temas aprovados, sem exigir duas colunas nem alturas fixas para texto.

## Limites e conferência

Alterar apresentação Compose nativa; manter protocolo, assinatura, segurança, consulta por sessão e escolhas de download/instalação. Preservar home, leitor, dados e USB. Versão prevista 1.1.20, pois a manutenção 1.1.19 já foi enviada ao pipeline. Conferir a publicação existente antes de preparar a próxima. Testes de foco e transição conforme a alteração, compilação/lint e publicação assinada; resultado físico depende do usuário na Box. Não alegar ganho de desempenho.
