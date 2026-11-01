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

## Estrutura

```
app/src/main/java/com/convertia/app/
├── MainActivity.kt        # ponto de entrada, edge-to-edge
└── ui/
    ├── ConvertiaApp.kt    # Scaffold + barra de navegação inferior
    ├── Destination.kt     # abas: Baixar, Converter, Fila, Biblioteca, Ajustes
    └── theme/             # cores e tipografia tiradas do mockup
```
