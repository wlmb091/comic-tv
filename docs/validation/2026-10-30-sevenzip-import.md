# Importação de CBR que contém 7z

Arquivo fornecido: RqurVrd Bslt #01 (2026) (DarkseidClub).cbr,66.038.065bytes/SHA2560093d407b8239db8c530769bc22133c168f6dbf2b48a5db6c7810fb5021bebbe. Assinatura377abcaf271c;7z0.4, nãoRAR.36JPEGs,67.755.755bytesexpandidos,CRCdetodosconfirmado. Originalinalterado/nãoversionado.

Causa: ComicArchive/CollectionArchive/Blob reconheciam sóZIP/RAR/PDF/EPUB. Extensão.cbr não converte7z emRAR. Diagnóstico diferentedeCP437/MALFORMED anterior.

Implementação: ComicContainers compartilhado porimportador/coleções/Blob;7z/CB7comCommonsCompress1.28.0 eXZ1.10. Disco/acessoaleatório,decoder32MiB,headercomprimidoeEXPANDIDO<=8MiB,5000entradas,1GiBarquivo,32MiB/página,2GiBtotal;nomesnãoviramsaída,cancelamentonocanal,CRCetamanhodepágina,temporárioslimpos. Senha/links/antiitemsrejeitados. Novasextensões7z/CB7aceitasnobrowser/USB/upload/FTP. BlobmantémbloqueioAndroidManifest;fixturesprópriassemconteúdoHQ.

ZIPpreservaUTF8primeiro/CP437fallback;metadadosainda inválidos dão mensagemorientandorecompactar,emvezdeMALFORMEDcru. Arquivovazio,incompleto,HTML/login/executáveltêmmensagensdistintas,semconfiarExtensão/MIME.

Revisãoindependente encontrouP2: limitepackedheadernãolimitaexpansãoemCommonsCompressreadEncodedHeader. CorrigidoSevenZHeaderGuardpreflightlimitandoUnpackSizesantesdeabrirdecoder;testeREDfalhouantes/GREENpassoudepois. Limitaçãointencional:encodedheader1folder/1packedstream/comcadeiaaté8coders1in1out;estruturasincomunsrejeitadasexplicitamente.

Fontesprimárias: https://commons.apache.org/proper/commons-compress/examples.html e https://raw.githubusercontent.com/apache/commons-compress/rel/commons-compress-1.28.0/src/main/java/org/apache/commons/compress/archivers/sevenz/SevenZFile.java . LicençasCommonsCompress/IO/Lang/Codec eXZ emdocs/licenses eassets/licenses.

Evidência: build/sevenz-red.log,header-red/green,archive-errors-red; primeira integraçãoAndroid BUILD SUCCESSFUL5m14,assembleRelease/lint/AndroidTestcompile. ProbeclassesKotlinreaisindexou/extraiu36páginascomCRC67.755.755bytes (build/sevenz-actual-core.log). Verificaçãofinal BUILD SUCCESSFUL3m51 (build/archive-compat-final.log):104JVM(69coreArchive/Transfer+35Browser),0falhas;6NodeBlobpassaram;assembleReleaseAndroidunsigned/AndroidTestcompile/lint0erros88avisos. RepetidoprobenoarquivorealAPÓSguardadeheader,36páginas/CRCtotalOK (build/sevenz-actual-core-final.log). SempublicaçãoouAPKassinadooferecido.semBox/ADB/mediçãodevelocidade. Loteaberto,versão1.4.4,sempublicaçãodenovoAPK.
