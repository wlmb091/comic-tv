# Validação — Comic TV 0.8.3

24/10/2026 · versionCode 23 · Android 8/API 26 ou superior.

## Alterações

- Abertura da biblioteca solicita foco para o conteúdo uma vez, após compor o cabeçalho. Ícones da lateral não recebem foco automático enquanto esse pedido está pendente, evitando expansão inicial.
- Expansão da lateral depende exclusivamente do foco nos ícones; removido hover sobre o painel. Sair dos ícones recolhe a lateral. Pedidos de foco por navegação explícita e restauração ao retornar do leitor continuam independentes.
- Fundo opcional em Configurações → Acessibilidade e aparência → Fundo dos detalhes: preto / capa desfocada. Preferência persistida; alto contraste mantém preto.
- Cópia da capa preenche toda a tela de detalhes, esticada, desfocada e escurecida por camada preta de 76%. Elementos de primeiro plano e arquivo original preservados.
- Desfoque compatível com Android 8: amostragem na decodificação, imagem 96×54 e filtro separável em software no dispatcher IO. Falha/ausência de capa mantém fundo preto sem bloquear leitura.

## Verificação

- 165 testes JVM sem erros/falhas: 90 core, 10 mihon, 65 app.
- Três testes novos cobrem preservação de cor/alfa, suavização de detalhes sem mutar entrada e rejeição de dimensões inválidas/excessivas. Teste de restauração de aparência atualizado para a preferência de fundo.
- Teste Compose adicionado para início recolhido, entrada nos ícones por D-pad e recolhimento ao sair. Compilado no APK de instrumentação, não executado em dispositivo.
- assembleDebug, assembleDebugAndroidTest e lintDebug passaram.
- Assinatura apksigner e metadados aapt2 verificados: 0.8.3/code23/minSdk26.
- Verificador ABI DEX e consultas de migração/histórico em SQLite local passaram.
- SHA256: F4C1088A1970BD663EC368764D8130453E4DE0F3F9119732F720DA85A070F91C.

## Conferência na TV

Sem aparelho conectado nesta etapa. Conferir inicialização com biblioteca vazia/preenchida, foco lateral, retorno do leitor, ativação do fundo, troca entre edições, preferência após reiniciar e alto contraste. O fundo padrão permanece preto até ativar a opção.
