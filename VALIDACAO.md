# Validação — Comic TV 0.3.0

Data: 23/10/2026. versionCode 4. Mantida a assinatura debug usada nas entregas anteriores.

APK `Comic-TV-0.3.0-debug.apk`: assinatura v1/v2 verificada; minSdk 23, targetSdk 36. SHA-256: `5B1221645F076A02501969D86F2FBAA70746B86FE27778CCE18F24DA957DEB05`. JavaScript do upload passou em `node --check`.

## Mudanças

- ZIP/CBZ e RAR/CBR identificados pela assinatura, independentemente da extensão. O CBZ 02 do usuário contém RAR4; não precisa mais de conversão.
- RAR4/RAR5 por Junrar 8.1.2, acesso aleatório ao arquivo em disco, sem descobrir volumes vizinhos. Preflight limita número e tamanho dos cabeçalhos antes da alocação do decoder. Limites de entrada, tamanho e dicionário; saída temporária limitada, com cleanup e verificação de CRC pelo decoder. Senha, multipartes e links são recusados.
- PDF pelo PdfRenderer Android, uma página por vez, com lado maior limitado a 2048 pixels. Arquivo original preservado.
- EPUB identificado pelo container, leitura em ordem de spine. Imagens raster no leitor existente; texto básico em tela nativa com fonte 18–40 sp, rolagem pelo D-pad e posição relativa salva por seção. Esquerda/direita mudam seções, OK abre fonte/opções. Scripts não são executados; URLs externas e DRM/encryption são recusados.
- Seletor e upload aceitam CBZ, ZIP, CBR, RAR, PDF e EPUB. Mantida a ausência de filtro nativo accept para não bloquear CBZ no iPhone.
- Java desugaring habilitado para minSdk 23. Licenças Junrar, SLF4J e Jsoup incluídas nos assets e documentação.

## Evidências executadas

Build final: `core:test`, `app:testDebugUnitTest`, `app:assembleDebug`, `app:assembleDebugAndroidTest`, `app:lintDebug`: BUILD SUCCESSFUL.

46 testes JVM, zero falhas. RAR e upload de novos tipos foram observados falhando antes da implementação. EPUB inicialmente falhou na ordem de spine/texto e na rejeição de URLs inseguras; esses casos passaram após implementação. O teste de texto no ViewModel falhou quando tentava decodificar texto como bitmap, e passou com a nova representação.

Cobertura dos testes novos: RAR4 e RAR5 sólido, ordem natural, extração reversa, extensão incorreta, caminhos inseguros, duplicatas, senha, corrupção de dados, limites e cancelamento/cleanup. EPUB com texto e imagens intercalados, ordem não alfabética do spine, texto longo/acentos/emoji, scripts ignorados, URLs externas, saída do pacote, encryption e limite de markup. HTTP aceita os novos sufixos e mantém os testes existentes de sessão, streaming e isolamento.

Os dois arquivos Thanos reais foram indexados e todas as páginas extraídas pelo novo núcleo com heap JVM de 192 MiB: 52 páginas do 01 (ZIP), 52 do 02 (RAR4). Os SHA-256 das 104 páginas conferem com extração independente: .NET ZipFile para 01; WinRAR/UnRAR para 02. Zero divergências. Conteúdo do usuário não foi incluído no Git; fixtures são sintéticas.

Android Lint: zero erros, 27 avisos. Os seis avisos adicionais desta entrega são sugestões de conveniência KTX (Bitmap, Canvas, SharedPreferences); os demais são os avisos anteriores de dependências, configuração e recursos.

Revisão independente identificou e corrigiu cache integral de RAR em memória, limite de cabeçalhos tardio, diferença de KeyDown/KeyUp entre texto e imagens e ocultação de erros de persistência no leitor de texto. Reavaliação das correções sem problemas Important/Critical remanescentes.

## Limites e testes pendentes

Sem TV ou emulador conectado (`adb devices` vazio). Testes instrumentados compilaram, incluindo PDF sintético com duas páginas; NÃO foram executados. PdfRenderer, fonte/rolagem EPUB e foco precisam ser validados na TV. Nenhum PDF/EPUB real do usuário foi fornecido. A execução JVM não comprova comportamento Android em todos os aparelhos.

EPUB é um subconjunto offline: texto básico e JPEG/PNG/WebP, sem fidelidade de CSS, fontes embutidas, áudio/vídeo ou SVG independente. SVG com imagem raster referenciada é interpretado como imagem. A paginação do texto é por seções de até 12.000 caracteres; não corresponde a páginas impressas. Conteúdo protegido não é desbloqueado. PDFs são rasterizados; zoom maior não adiciona detalhes além da resolução de renderização.

Barra de páginas, fontes Mihon, HQ Now e sagas ainda não foram implementados. Estudos de Hikari e requisitos permanecem em ANALISE-HIKARI.md. Esta entrega conclui os formatos solicitados, com as limitações acima.

## Reteste na TV

1. Instalar Comic-TV-0.3.0-debug.apk sobre a versão anterior.
2. Iniciar transferência pela rede local. Selecionar os dois CBZs originais do Thanos, sem renomear ou recompactar, e confirmar 52 páginas em cada edição.
3. Testar também RAR/CBR e um PDF sem senha. Abrir páginas inicial/final, zoom, retorno e retomada.
4. Enviar EPUB ilustrado e EPUB de texto sem DRM. Verificar sequência, fonte via OK, rolagem ↑/↓, seções ←/→, saída e retomada.
5. Registrar mensagem exata caso um documento seja recusado e informar seu formato real.
