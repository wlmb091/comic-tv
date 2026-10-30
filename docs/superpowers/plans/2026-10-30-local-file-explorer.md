# Explorador local de arquivos

Escopo autorizado: explorador agora; nuvem e comunidade ficam para outro pacote. Preservar a correção 7z pendente e não gerar APK assinado/publicar.

1. Analisar estaticamente o APK X-plore fornecido e registrar evidências/limites; implementação própria, sem código/assets do alvo.
2. Testar e implementar ExplorerFiles no core: tipos de arquivo, confinamento canônico, ordenação e enumeração limitada sem varredura recursiva.
3. Implementar ExplorerViewModel/ExplorerStorage/ExplorerScreen: origens privadas de arquivos do usuário e pastas SAF autorizadas; caminho, filtros, lista rolável, retorno e foco D-pad. Importação reutiliza LibraryViewModel; arquivos já importados abrem leitura.
4. Instalação local explícita separada: validar APK, cópia limitada e cancelável em cache privado, FileProvider restrito, permissão de fontes desconhecidas e confirmação pelo instalador Android. Não alterar downloads APK do navegador nem confiança em extensões.
5. Integrar entrada em Importar, revisar independentemente e executar testes focados + compilação Android/lint. Documentar limites e validação física pendente. Sem push/publicação.

Execução concluída localmente. Revisão independente corrigida;6 testes focados passaram, Android e AndroidTest compilados, lint0erros/81avisos. Box pendente. Registro docs/validation/2026-10-30-local-file-explorer.md. Sem APK/push/publicação.
