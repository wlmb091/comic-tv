# Atalho de desinstalação — pacote aberto

Escolher fonte → segurar OK por1s → Desinstalar fonte. Usa o mesmo controlador de pressão longa da biblioteca, que consome OK e não abre o catálogo junto com o menu. O menu bloqueia o release/repeat herdado da pressão e inicia em Cancelar.

Somente uma fonte ainda listada, pertencente a uma extensão instalada, pode abrir a desinstalação; o próprio app é recusado. Confere novamente a feature `tachiyomi.extension` no pacote. A confirmação real pertence ao Android via ACTION_DELETE/package URI; permissão REQUEST_DELETE_PACKAGES declarada. Nenhum pacote foi desinstalado nos testes.

Ao retornar do Android, refreshInstalled refaz a lista a partir do PackageManager. Não há ocultação otimista: cancelar mantém a extensão. A revisão de fontes limpa snapshots/previews e remove a fonte ativa se indisponível. Fontes removidas deixam de compor catálogo, novidades e busca instalada; somente reinstalação permite reaparecer. Índices em Extensões podem continuar oferecendo download para reinstalar.

Uma extensão pode conter várias fontes/idiomas; Android desinstala o pacote inteiro, e o menu informa a quantidade de fontes afetadas. Biblioteca, progresso e downloads offline não são apagados. Capítulos não baixados podem precisar da extensão novamente para leitura.

Verificação: source-uninstall.log compilação aprovada1m11; source-uninstall-verified.log testes ExtensionUninstallPolicy+regressão SourceSelection e compilação AndroidTest aprovados43s. Fluxo do instalador/foco físico ainda depende da Box. Sem APK, versão, push ou publicação; usuário segue acrescentando alterações.
