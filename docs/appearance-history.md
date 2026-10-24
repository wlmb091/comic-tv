# Resumo, histórico e aparência — 0.8.0

Detalhes: metadados e ações juntos à esquerda, capa ampliada ao centro, resumo à direita na mesma altura da capa. Cada região longa tem rolagem independente; não é necessário navegar pelo resumo para iniciar a leitura.

Histórico: o campo hiddenFromHistory oculta da lista de histórico e de continuar lendo, sem apagar currentPage ou lastRead. A biblioteca completa mantém a edição. Ler novamente salva a posição e reativa a presença no histórico. A ação pode ser revertida no próprio menu ou nos detalhes.

Dados: banco Room v2, migração aditiva 1→2 inclui apenas coluna INTEGER NOT NULL DEFAULT 0. Nenhum fallback destrutivo. O script tools/check-history-migration.mjs executa as consultas reais em SQLite para conferir preservação, restauração e retomada.

Controle: biblioteca usa o mesmo controlador de limiar de 1 segundo do leitor; menu consome a liberação da tecla que o abriu. Possui leitura direta, detalhes, remoção/restauração e fechar. Fechar retorna o foco ao cabeçalho da biblioteca.

Aparência: presets Amber, Green, Blue e Violet por nome estável, escalas de fonte limitadas a 100/115/130% e alto contraste. SharedPreferences com confirmação de gravação antes de publicar o novo estado. Presets alteram somente interface. Alto contraste usa preto, texto secundário branco e foco reforçado nos botões. Cor padrão clara também fornecida pelo LocalContentColor.
