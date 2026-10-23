# Compatibilidade de instalação Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for bounded manifest reader/tests, task review and integration review. Root coordinates Gradle.

**Goal:** Explicar incompatibilidade de Android antes de oferecer um APK ao instalador, usando requisito real do arquivo.

**Evidence:** HQNow1.6.0/version106000 baixado do índice real Keiyoushi, certificado9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2 validado comapksigner. aapt2 revela minSdk26. ComicTVminSdk23. Endpointpúblico retornouSaga1079 com17edições. SemdeviceparaexecuçãoAPK.

**Architecture:** LeitorJVMlimitado de AndroidManifest.xml binário dentro doAPK verificauses-sdk/minSdkVersion antes dePackageManager; este pode recusar pacotes incompatíveis. Metadados não executam código. Manterverificações pacote/versão/feature/certificado existentes. ErroexplicaAPIexigidaeatual. Rótulodeextensãoinstalada diferenciacompatibilidadedaAPIdevalidaçãofuncional.

- [x] Leitor mínimo AXML com limites deZIP/strings/chunks/atributos, números positivos/codenames recusados com mensagem clara; testes escritos primeiro e validados contra manifest real HQNow (sem bundlarAPK).
- [x] Instalador checa requisito antes de oferecerAPK, mantém políticas de confiança; mensagemPTbr e rótuloAPIpreciso. Versão0.7.1/code10.
- [x] Revisão independente, testes/build/lint/instrumentaçãocompilada, documentação de requisitos e resultados do ensaioAPI/assinatura; entregarAPK/código e atualizarGitHub. NãoafirmarexecuçãodefontevalidadaTV.
