# Lateral recolhida na abertura — 0.8.3

Na abertura, a biblioteca pede foco para seu botão de conteúdo, aguardando o cabeçalho estar composto. O pedido é consumido uma vez, sem repetição a cada emissão do banco.

Enquanto esse pedido está pendente, os ícones da lateral não aceitam foco automático. Isso evita que a escolha inicial do Android expanda a lateral antes do foco no conteúdo.

A expansão depende exclusivamente de foco dentro dos ícones. Hover sobre o painel não expande mais a lateral. Sair dos ícones recolhe a barra. Navegar explicitamente pela lateral mantém o foco no ícone acionado, como na correção de 0.8.2.

O estado do pedido inicial é salvo durante recriação da tela. Retornos dos detalhes/leitor e atualização de dados não iniciam um novo pedido que roube a restauração de foco.
