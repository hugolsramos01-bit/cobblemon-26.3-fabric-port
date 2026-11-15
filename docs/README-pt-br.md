<div align="center">
  <img src="../common/src/main/resources/assets/cobblemon/icon_cobblemon.png" alt="Ícone do Cobblemon" width="112">

  # Cobblemon · Minecraft 26.3

  **Um port experimental para Fabric.**

  Explore, capture, treine e batalhe com Pokémon no Minecraft 26.3.

  [Instalação](#instalação) · [O que mudou](#o-que-mudou) · [Compilar](#compilar) · [Estado do projeto](#estado-do-projeto) · [English](../README.md)
</div>

---

> **Port da comunidade · Em desenvolvimento**
>
> Esta é uma adaptação não oficial baseada no trabalho da equipe do Cobblemon. Faça backup dos mundos antes de experimentar.

| Minecraft | Mod loader | Java | Estado |
| :--- | :--- | :--- | :--- |
| **26.3** | **Fabric 0.19.5+** | **25** | Experimental |

## Instalação

Crie uma instalação separada no launcher. Em multiplayer, use a mesma versão do port no servidor e nos clientes.

1. Instale **Minecraft Java 26.3** e **Fabric Loader 0.19.5 ou mais recente** para essa versão.
2. Configure **Java 25** e adicione uma versão compatível do **Fabric API**. A versão usada na validação foi `0.161.0+26.3`.
3. Coloque o JAR instalável do Cobblemon na pasta `mods` da instalação. Caso ainda não haja uma release, veja como [compilar](#compilar).
4. Inicie o jogo e crie um mundo de teste. Para servidor dedicado, instale também versões correspondentes de Fabric, Fabric API e Cobblemon no servidor.

O JAR gerado por `releaseJar` inclui as bibliotecas de execução empacotadas, entre elas Fabric Language Kotlin. JEI, Mod Menu e outros addons são opcionais; a compatibilidade depende da versão.

## O que mudou

O port adapta renderização, interfaces, rede, mundo, receitas, máquinas e persistência para 26.3. Também inclui ajustes de montaria e voo, spawn e movimento aéreo, início de batalha selvagem ao lançar seu Pokémon, correções visuais e otimizações de renderização e animação.

Algumas decisões de gameplay vão além da migração e podem diferir do Cobblemon original. O objetivo é permitir que jogadores e mantenedores avaliem o conjunto completo.

## Estado do projeto

A revisão Fabric testada passou nos testes comuns, em **34 GameTests de servidor**, na suíte de cliente, na validação do access widener e no build. O JAR também iniciou um cliente e um servidor separados do Gradle. O usuário confirmou em jogo o fluxo opcional de som ambiente. Esses testes cobrem cenários específicos, não todos os addons e configurações.

Ainda faltam o teste com dois jogadores autenticados, uma verificação mais ampla de addons e a revisão dos avisos de distribuição de bibliotecas. NeoForge e TBCS estão fora do escopo atual. Consulte o [resumo para revisão](26.3-review-brief.md) e a [auditoria de procedência](26.3-provenance-review.md).

## Compilar

Com JDK 25 e Git instalados, execute na raiz do projeto:

```powershell
.\gradlew.bat :fabric:releaseJar
```

O JAR instalável fica em `fabric/build/libs/`, começa com `Cobblemon-fabric-` e não tem `dev-shadow` no nome. Para iniciar um cliente de desenvolvimento, use `.\gradlew.bat :fabric:runClient`. Os comandos dos testes estão no [guia de validação](26.3-validation.md).

## Sons opcionais

O port preserva os mapeamentos de som originais do Cobblemon. O [guia de sons ambientes](custom-pokemon-ambience.md) explica como criar um resource pack com áudios que você tenha permissão para usar. O gerador não inclui nem baixa áudios.

## Créditos e licenças

O [Cobblemon original](https://gitlab.com/cable-mc/cobblemon) é obra da equipe e dos contribuidores do projeto. Este port parte do commit `7511facebce13cc86706d49ae7ebab515e862fc8`.

O código segue a [MPL 2.0](../LICENSE). Modelos e outros recursos podem ter licenças próprias; consulte os arquivos de licença individuais e a [licença do repositório de assets](https://gitlab.com/cable-mc/cobblemon-assets/-/blob/master/LICENSE). Bibliotecas de terceiros mantêm suas próprias licenças.
