# Capa principal automática ao abrir a obra

Pedido: ao abrir quadrinho/saga pela primeira vez, buscar e salvar automaticamente a capa principal ausente. Ao atualizar a coleção, imagem já local, inclusive para fonte instalada não selecionada. Não trocar a fonte ativa, não usar primeira página do capítulo como capa de saga.

- [x] Carregar identidade e autorização da fonte do registro da edição, não a seleção atual do catálogo.
- [x] Cache automático separado das escolhas manuais. Não sobrescrever imagem própria nem capa explicitamente escolhida; ausência de fonte não apaga cópia válida.
- [x] Obter capa da obra usando cliente/headers/cookies da própria extensão; metadados somente se faltarem/expirarem dados, sem abrir/baixar capítulos para decoração.
- [x] Download limitado, sequencial/coalescido por obra, cancelável, atomicamente validado; cooldown após falha. Não bloquear entrada no leitor, foco ou progresso.
- [x] Disparar ao abrir detalhes/leitor, inclusive edição já importada online e entrada direta no leitor. Pausar/cancelar fora da tela/segundo plano.
- [x] Invalidar seção via sagaRevision ao concluir; a Home prefere cache automático, respeitando escolha manual.
- [x] Testes focados e compilação; registrar limites reais. Nenhuma validação física presumida. Sem geração/publicação de APK neste recorte até fechar o lote seguinte.

Arquivos locais sem fonte ou URL de capa de saga não permitem descoberta automática confiável; manter escolha explícita. Fonte precisa continuar instalada e autorizada. Falha de rede não impede leitura nem remove capa previamente salva.
