# Pacote completo1.3.0 — ajustes, leitor, busca e navegador

Autorização:29/10/2026 “pegar tudo…começar…entregar tudo no mesmo pacote”. App gratuito/pessoal, doação externa opcional; usar somente fontes/APIs permitidas,semocultarvínculo/contornarrestrições. Mesmafeaturebranch,árvorelimpainicial69c43b8.

Ordem obrigatória: terminar tarefa em andamento antes da nova, fila única/publicação final.

- [x] 1.Acabamentovisual:Comicposterverticalanterior;FlixSearchfundomenu/bordavermelha;CinemaMenu fundoescuro/textobranco/sem borda;Maxrecuoblocotítulo/headingesquerdo;Settingsalinhamento;Comicgapseparandolateral/capas;Cinemarecuofileiras;sombrasóletrasdotítuloprincipal;rodapécompactocentralizado;TodasrestauraHomecomtodasasseções.
- [x] 2.Leitor:rotação90graussomenteleitor,surfacecomdimensõestrocadas/ajusteinteiro,controlessamesrelativepositions,menusbibliotecainalterados,semoriginalmutation.
- [x] 3.Busca global: tela comabasTodos/Quadrinhos/Mangás,tagsexplícitas,local+fontesinstaladasnãoapenasativa,catálogonovidades;repositorysemíndicedetítulosmostraextensõesdisponíveissemalegarobraencontrada. Cancelamento,debo400ms,limites2concorrentes,timeout/budgetsreuse,fonte+resultado+naBibliotecabasedonidentity.
- [x] 4.Metadados/notas: fontesoficiais permitidas,atribuição/cache/seleçãodeobraISBN/autores,semassociaçãomudaqueconfundavolume/filme;ausenteexplicitosemnotainventada.
- [x] 5.NavegadorTV:WebViewcontrolávelDpad,campotecladoOK,navegaçãohttp(s),TLSsemignorarerro,semJSbridge/fileaccess,downloadexplícitocomlimite1GiB/progresso/cancelamento/redirect/cookiesdomínio,importarcompatívelviafluxoexistentevalidandoarquivo,nãoautoinstalarAPK.
- [ ] 6.Revisãocompletarecorte+segurança,testeslocaisfocados,CIcompleto/buildassinado/publicaçãounificada1.3.0(code73).

Preflight: tarefa1ownUIAppLibrary;2ownReader;3ownSearchSources+Appintegrationapóstarefa1;4Metadataownnovosarquivos;5Browserownnovosarquivos+Import/Appapós3. Rootcoordenaumatarefaporvez,leituradeinvestigaçãopermitidaenquantoimplementadortrabalha. Semnovasdependênciaspesadas,API26/HC/text85–130%/foco/dados/USBpreservados;Boxmanualsememuladordriver/restartWindows.

Limites explícitos:repositoriesextensõesnãocontêmlistadetodosostítulos;extensãonãoinstaladanãoexecuta. Semdescobertaexternafictícia. Leia Fontes/licençasantesintegração. NãoaplicarIMDbnotadefilmeàHQ.

Etapa1:build/package130-visual.log1m58/52tarefasaprovado,testesThemeSpec+AndroidTestcompile. Etapa2emexecução,nenhumafunção3/4/5implementadaantesdeleitorfinalizado. Fontesmetadadospesquisadas:OpenLibraryhumanlookup/cache/1reqseg,GoogleBooksrequerchave,AniListtermosreceitaespecífica,ComicVinenãocomercial. Preferirprovidersemchavecomatribuição,mínimosdados ecache;semnotadeadaptaçãoIMDbnaobraoriginal.

Etapas2–5 concluídas e revisadas. Builds focados reader/search/metadata/browser aprovados; compilação AndroidTest, sem execução física. Revisão final independente sem P1/P2. Preparação1.3.0/code73; CI completo uma vez e publicação única a seguir.
