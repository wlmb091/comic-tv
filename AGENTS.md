# Comic TV — decisões persistentes do usuário

- Design final aprovado: docs/design/approved-home.png. Ler docs/design/DESIGN.md antes de alterar interfaces.
- A referência é para implementação nativa Android TV com fidelidade visual, não apenas recoloração do layout antigo.
- A logo oficial é a arte fornecida pelo usuário em app/src/main/res/drawable-nodpi/brand_symbol.jpg; a assinatura com texto é brand_wordmark.jpg. Estas artes substituem a logo provisória da imagem de referência.
- Cores da interface continuam customizáveis por presets; preservar alto contraste, tamanho de texto e navegação por D-pad.
- Não considerar títulos/capas fictícios da imagem como dados reais da biblioteca.
- Refinamento aprovado em 24/10/2026: tela principal usa somente identidade Comic TV desfocada e escura; capa desfocada fica restrita aos detalhes da edição.

- Temas aprovados: Comic TV (padrão), Tema Flix, Tema Prime, Tema Max e Tema Cinema; alterar superfícies completas e preservar logo/capas originais. Presets anteriores continuam disponíveis.
- Cabeçalho da biblioteca somente COMIC TV em Montserrat ExtraBold: COMIC branco e TV na cor do tema; sem símbolo nem retângulo de fundo.
- Barra lateral expandida usa o mesmo ComicWordmark textual da biblioteca: COMIC branco e TV na cor do tema.
- Símbolo da barra lateral recolhida acompanha a cor do tema ativo.
- Relacionados ocultos e sem consultas por decisão do usuário; preservar implementação para futura retomada. A função atual compara metadados sem IA.
- COMIC TV centralizado no topo da barra lateral expandida.
- Leitor: etapa aprovada prioriza zoom por regiões do original, filtros opcionais para texto/HQ antiga e cache das próximas duas páginas/miniaturas. Painéis individuais ficam para depois; não exigir Android 13 nem modificar arquivos originais.
- Pacote de organização aprovado: favoritos e coleções manuais, busca local na biblioteca e Armazenamento nas Configurações, integrados às telas atuais. Limpeza somente de páginas/miniaturas temporárias, preservando originais/progresso.
- Backup/restauração e downloads offline aprovados e implementados em 0.12.0: backup de dados sem originais/páginas/credenciais/confiança, restauração por mesclagem e pendências por SHA; fila sequencial pausa ao sair/segundo plano. Downloads permanentes independem da limpeza de cache, e a leitura de pacote completo não depende da extensão/rede. Nuvem/backup automático/serviço contínuo ficam para revisão futura.
- Box do usuário usa USB como armazenamento expandido; preservar conexão/configuração funcional e não exigir remoção ou alternância de depuração para testar. Validação manual em docs/validation/2026-10-24-mi-box-manual.md; resultado da restauração precisa ser explicitamente visível (0.12.1).
