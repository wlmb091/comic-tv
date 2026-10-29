# Home: foco, enquadramento e carrossel —1.2.8

Spec: lote de fotos/vídeos após1.2.7 e autorização “pode prosseguir com as tarefas”. Dados reais, ComposeAPI26, USB/originais/leitor preservados.

- [x] Diagnosticar startup/↑: ação dohero é foco abaixo do título não focalizável; bringIntoView pode deslocar cabeçalho e↑ não encontra alvo que revele topo. Pin do cabeçalho apenas quando ele cabe no viewport; entrada explícita/↑ revelam o cabeçalho inteiro. Não resetar rolagem em emissões/carrossel.
- [x] Max Explore imediatamente apóshero/antesContinuarlendo; atualizar índices de restauração. ComicRecentes72dp de altura e capasCrop; títulos streaming moderadamente menores, ajuste85–130% mantido.
- [x] Flix/Max/Cinema: alternância automática5s, título/progresso/ações sincronizados com mesmaedição; layout preservado. Pausar em menu/diálogos/segundo plano/interaçãoativa/fora do topo; não pausar apenas por focoCTA inicial, senão nunca iniciaria. Sem mudar foco/scroll/progresso persistido.
- [ ] Revisão independente, testes JVM do recorte e compilação nativa; CIcompleto uma vez.
- [ ] Publicar1.2.8/code70, conferir manifestação versionada/asset/logassinador e registrar.

Preflight: tarefasfoco ecarrossel compartilham somenteinterface LibraryScreen(featuredIdOverride,onHeroInteractionChanged); focoownsScreen/Hero/ThemeSpec, carrosselownsApp/Backdrop/controller. Sem conflitos de arquivos. Rootintegra/pública. Nenhuma fonte/API nova; Prime/Comic não recebemautoplay. Animação só fade curto160ms pelo token existente, semredimensionamento.

Validação física será naBox; vídeos são evidência do defeito, não validação da correção. Dependência temporária imageio-ffmpeg instalada apenas embuild/video-tools para extração dos3vídeos, fora doAPK.
