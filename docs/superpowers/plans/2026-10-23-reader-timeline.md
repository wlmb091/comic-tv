# Barra de páginas Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, then independent review.

**Goal:** Escolher qualquer página/seção pelo D-pad e confirmar sem alterar o progresso enquanto navega na barra.

**Architecture:** Novo modo Timeline no reducer compartilhado com cursor separado da página lida. A confirmação altera a página usando o fluxo existente de carregamento/persistência. Componente Compose compartilhado pelos leitores de imagens e texto.

**Tech Stack:** Kotlin JVM reducer e Compose Canvas/semantics.

**Spec:** Barra de páginas solicitada junto às referências Hikari e aprovada na sequência de incrementos; identidade escura/âmbar preservada.

## Global Constraints

OK → Ferramentas → Escolher página/seção. Esquerda/direita movem um item (orientação visual da barra segue RTL); cima/baixo saltam -10/+10. OK confirma e retorna à leitura. Voltar cancela e retorna às ferramentas sem mudar página, zoom ou progresso. Contagem zero/um não pode gerar índices inválidos. Sem pré-carregamento de todos os itens: desenho limitado a 80 marcas. Em EPUB de texto usar “seção”; o mesmo fluxo funciona com imagens, ZIP/RAR/PDF. Fontes online e sagas continuam em incrementos seguintes.

## Tasks

- [x] Testes do reducer: cursor independente, limites, salto, RTL, confirmação/cancelamento, contagens zero/um.
- [x] Implementar estado Timeline, confirmação e cursor separado. Testar ViewModel: navegar não salva, confirmar carrega/persiste destino.
- [x] Componente visual legível e acessível, integrado a ambos leitores e ao foco do controle.
- [x] Build, testes JVM, testes instrumentados compilados e lint; revisão independente; validar pacote e assinatura.
- [x] Atualizar versão 0.3.1, documentação e GitHub, entregar APK. Registrar teste em TV como pendente.
