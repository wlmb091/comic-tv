# Pesquisa: explorador local, APK e nuvem

## Solicitação e estado

Usuário solicitou explorador de pastas de arquivos internos/armazenamento TV/USB, importação de PDF/CBR/CBZ/EPUB e demais compatíveis; instalação de APK selecionado; acesso a GoogleDrive/OneDrive/TeraBox para baixar/importar. Pediu usar sempre a skillreverse-engineer-anything em pesquisas semelhantes. Correção7z anterior concluída e preservada; pacote ainda aberto,sempublicação.

## Evidência REA estática

APK Amaze3.11.4 obtido da releaseoficial https://github.com/TeamAmaze/AmazeFileManager/releases/tag/v3.11.4 .11.996.072bytes/SHA2562462dc19c75db5eae0629ac40a5f11baecb84afde1565602de5712f6260577e5. REA6.7CLIinspect-artifact Evidenceev_3849a6396a60c5be4829f6cd228bef35503e808a091a43e69949cecedb24d974; inventárioAPK disponível embuild/explorer-research/amaze-rea.json. Aapt2confirma com.amaze.filemanager/code126/name3.11.4/min23/target36. ManifestoREQUEST_INSTALL_PACKAGES,MANAGE_EXTERNAL_STORAGE,FileProvider.

NãoháREA MCP conectado; doctorobservouregistrationconfigdrift; nenhumaconfiguraçãoalterada. JADXheadlesscontinuaausenteconformeinvestigações anteriores: nãoalegardecompilaçãoAPK nemruntime. Inspeçãoemsourceoficialtag3.11.4 complementainventário,normalanálise de repositório.

CloudUtil.java83–102 chamaCloudStorage.getChildren para listar itens(pasta/nome/tamanho/data),prefixosporprovedor;getUserLoginverificaestado/token. FileUtils.java350–401preparainstalaçãoAPKporIntent/FileProvider/teladoAndroid;OTGUtil usaDocumentsContract. Referênciasfonte https://github.com/TeamAmaze/AmazeFileManager/tree/v3.11.4 . Padrõescomo referência,semcopiarcódigoGPL/identidade/credenciais.

Primeira etapa: X-plore inicialmente consultado apenas pelo manual; o APK foi fornecido e inspecionado na etapa adicional abaixo. https://www.lonelycatgames.com/apps/xplore documentaárvoredoispainéis/arquivos/nuvemGoogleDrive+OneDrive+WebDAV. https://www.lonelycatgames.com/docs/xplore/google-drive/full explicaOAuthcomtokeneregistropróprio; NÃOreusarclientid/chavesdoX-plore. Consulta técnica não observa navegaçãofísicaTV.

## Opções de nuvem

1. ProvedoresAndroid/SAF e navegador existente: reaproveitaapps que publiquemDocumentsProvider;arquivoselecionadopode serbaixadopeloprovedor e copiado/importadonoComicTV. Dependedoappinstaladoeintegraçãodele; nãoprometerDrive/OneDrive/TeraBoxautomaticamentedisponíveisnaBox. Fonte https://developer.android.com/guide/topics/providers/document-provider .
2. ContasdiretasnoComicTV: adaptadoresGoogleDrive/MicrosoftGraph/TeraBoxviaOAuthoficial,registro/credenciaisdoComicTV,permissõesmínimas,tokensprotegidos/foraBackup. Fontes https://developers.google.com/workspace/drive/api/guides/api-specific-auth e https://learn.microsoft.com/en-us/graph/api/driveitem-list-children .TeraBoxendpointoficial/integrations/docsidentificado,conteúdoEnglishnãoacessívelnesta sessão;suporteespecíficopendente,nenhumalogin/APIprivadaaproveitada.

Decisão posterior do usuário: nuvem adiada para pacote futuro com comunidade. Implementar apenas explorador local agora; nenhuma nova autenticação ou conector de nuvem nesta etapa.


## X-plore fornecido — análise estática adicional

Arquivo X-plore 4.45.03, pacote com.lonelycatgames.Xplore, code44503, min26/target36. 11.494.882 bytes; SHA256 `4b2f9872117824673214b20ea9bb2c52e8453a4d96acc1798502dc0aec3faac4`. REA inspect-artifact `ev_2691366887fd7142e4072bed530d5c2d768a5fbfea179703d7832340f7d4e3f5`, inventário local ignorado `build/explorer-research/xplore-rea.json`; aapt2 badging/manifesto e leitura limitada da tabela de strings DEX.

Manifesto declara REQUEST_INSTALL_PACKAGES, MANAGE_EXTERNAL_STORAGE e permissões legadas de armazenamento. DEX contém referências a OPEN_DOCUMENT_TREE, buildChildDocumentsUriUsingTree, takePersistableUriPermission e application/vnd.android.package-archive. Isso sustenta os mecanismos disponíveis no pacote, não prova o fluxo executado nem uma decompilação. Não houve execução do APK/JADX nem cópia de código/assets. O Comic TV implementa fluxo próprio com SAF de leitura e instalador Android; não solicita acesso irrestrito a todos os arquivos nem acessa dados privados de outros apps. TV/USB dependem das pastas autorizáveis pelo seletor Android.

A experiência adotada é lista de pastas, retorno à pasta anterior, filtros e ações por arquivo; dois painéis, nuvem, exclusão/movimentação de arquivos e extração genérica não fazem parte desta entrega.
