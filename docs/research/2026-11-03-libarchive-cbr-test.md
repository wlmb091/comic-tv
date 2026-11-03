# Teste isolado de libarchive com os CBR do usuário

03/11/2026. Autorização: testar separadamente os dois CBR originais e comparar as 44 páginas com Junrar, sem alterar o aplicativo. Este teste não autoriza a substituição da biblioteca no produto nem mudar a licença do Comic TV.

## Resultado

**Aprovado para estes dois arquivos no computador.** Libarchive 3.8.9, fornecida pelo Windows em `C:/Windows/System32/archiveint.dll`, extraiu todas as entradas sem senha. A API C foi chamada diretamente por Python/ctypes, sem gravar páginas no disco. Cada nome de entrada, tamanho e SHA-256 dos bytes descompactados foi comparado com a extração de referência usando Junrar 8.1.2, a versão declarada no projeto.

| Arquivo | Formato | Páginas | Bytes descompactados | Comparação |
|---|---|---:|---:|---|
| TFormers-Segredos&Mentiras#01(TFComics-SQ).cbr | RAR5 | 22 | 62.721.872 | Todas idênticas |
| TFormers-Segredos&Mentiras#02(TFComics-SQ).cbr | RAR5 | 22 | 69.079.266 | Todas idênticas |

Nenhuma senha fornecida. SHA-256 dos dois arquivos originais conferido antes e depois, sem alteração. Leituras de libarchive levaram aproximadamente 0,564 s e 0,630 s neste computador; esses tempos não estimam desempenho no Android.

Hashes individuais das páginas e dos arquivos originais: `2026-11-03-libarchive-cbr-test.json`.

## Limites e próximo passo

Este resultado confirma compatibilidade dos dois exemplos com a extração RAR5 da libarchive no Windows. Não confirma compatibilidade universal, suporte a arquivos com senha, funcionamento no Android, custo de memória, tamanho adicional no APK ou integração com o leitor.

Antes de substituir Junrar, será necessário preparar uma compilação Android e a integração própria, preservar as validações atuais de caminhos/tamanhos/CRC/cancelamento e testar os arquivos na Box e no projetor. A decisão de retirar suporte a RAR com senha continua dependente de confirmação do usuário. Nenhum código do aplicativo, dependência, versão ou configuração de licença foi alterado por este teste.

Scripts e saída bruta locais: `../../work/cbr-inspect-20261103/` em relação à raiz do projeto. Relatório de licença da candidata: [COPYING do libarchive](https://github.com/libarchive/libarchive/blob/master/COPYING).
