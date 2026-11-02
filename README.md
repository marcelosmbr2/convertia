# Convertia

App Android para baixar e converter mídia, feito em Kotlin com Jetpack Compose.
Os protótipos de interface ficam em [`mockup/`](mockup/).

## Stack

| | Versão |
|---|---|
| Android Gradle Plugin | 9.5.0 (Kotlin embutido) |
| Gradle | 9.6.1 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 (Material 3) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 37 / 37 |

As versões ficam em [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Rodando

Precisa de JDK 17 ou mais novo e do Android SDK com a API 37 instalada.
Abra a pasta do projeto no Android Studio, ou pela linha de comando:

```bash
./gradlew :app:installDebug
```

## Estado atual

Todas as telas do mockup (`mockup/Convertia v3.dc.html`) estão implementadas: Onboarding,
Baixar, Converter (com os sheets Adicionar, Inspetor e Seletor de formato), Fila,
Biblioteca, Ajustes, Predefinições e o alvo do menu Compartilhar.

**Download e conversão ainda são simulados**, como no protótipo: o progresso é falso e
nenhum arquivo é baixado ou gerado. Já funcionam de verdade os ajustes (salvos no
DataStore), as permissões, a área de transferência, o seletor de arquivos/galeria e o
recebimento de links pelo menu Compartilhar. No build de debug o app abre com os dados de
exemplo do mockup; no release, vazio.

## Estrutura

```
app/src/main/java/com/convertia/app/
├── AppContainer.kt          # dependências (DI manual)
├── MainActivity.kt          # onboarding ou app
├── domain/                  # regras puras: catálogo de formatos, estimativas, modelos
├── data/                    # filas, biblioteca, ajustes e motores simulados
│   └── engine/              # interfaces de motor + implementações simuladas
├── share/                   # ShareActivity (menu Compartilhar)
└── ui/
    ├── AppViewModel.kt      # navegação (aba, sheet, voltar) e ações dos toasts
    ├── ConvertiaApp.kt      # barra inferior, sheets e toasts
    ├── components/          # design system do mockup
    ├── theme/               # cores, fontes (Inter, JetBrains Mono) e ícones
    └── download/ convert/ queue/ library/ settings/ onboarding/
```

Os motores reais entram trocando as implementações de `data/engine` (`MediaInspector`,
`DownloadEngine`, `ConversionEngine`); as filas e a interface não mudam.

## Testes

```bash
./gradlew :app:testDebugUnitTest
```

Os valores esperados do domínio (tamanhos, formato sugerido, estimativas) foram obtidos
rodando a lógica JavaScript do próprio mockup.
