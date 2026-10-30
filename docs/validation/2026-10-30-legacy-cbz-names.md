# CBZ O Máskara — codificação dos nomes internos

Investigação do arquivo fornecido pelo usuário, sem modificar ou versionar conteúdo/imagens.

- Tamanho155.692.710bytes (148,5MiB), SHA2562e38ac1b2007a83615cc6ad660a9e573e0d9314ad6540da79782375f4f6ff7ee.
- 376entradas,155.617.460bytes expandidos; testeCRC de todas as entradas passou.
- Nome local inicial bytes4f204da0736b6172615f30302e6a7067, flagUTF8desligada: CP437→O Máskara_00.jpg. ByteA0 isolado inválido emUTF8;CP1252representariaNBSP,nãocorreto.
- Java17ZipFilepadrão reproduziu invalidCENheader(badentryname);CP437abriu376entradas. AndroidrelatoMALFORMED[1]compatívelcomdecodificaçãodenome, não148MB/rede/memória. Não comparar porhashcomarquivoBox: usuário forneceu apenas cópialocal.
- RotaDownloads→importReceived→CollectionArchive.inspect tentaabrirZIPantesdeComicArchive. Precisacorrigir inspeção,detecção,índice eextração, não sórotuladoCBZ.

Implementação: openComicZip compartilhado, UTF8primeiro preservandoreferências existentes; fallbackCP437somente emfalhadecodificação de nomes. VarreduralimitadaforçadecodificaçãoemAndroidlazy,fechaZIPemfalha echecainterrupção. FlagsUTF8explícitascontinuamUTF8. Limites/traversal/duplicatas/senha/APKcontinuampreservados; semrecompactar/trocaroriginais. Aplicado tambémaodetectorBlob parapróximodownloadlegado.

Testes: fixturesgeradassemHQoriginal; RED3falhasantesdacorreção. Resultadofinal: BUILD SUCCESSFUL2m11 (build/legacy-zip-green.log),33testescoreArchive+4BrowserBlobFile,0falhas;AndroidTestKotlin compilado. Probe com o ARQUIVO REAL usando classes Kotlin compiladas: CollectionArchive.inspect retornou HQ única, ComicArchive.index376páginas; extraçõesíndices0/188/375 conferidas porCRC com bytesoriginais (848936/391008/325959bytes). SHA256originalinalteradoapóschecar. TestesfísicosAndroid/Boxnãoexecutados. SemnovoAPK/publicaçãoautorizadosnestasolicitação; versão1.4.3permanece.
