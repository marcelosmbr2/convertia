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
| Motor de download | yt-dlp via youtubedl-android 0.18.1 (Python 3.12, QuickJS, ffmpeg) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 37 / 37 |

As versões ficam em [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Rodando

Precisa de JDK 17 ou mais novo e do Android SDK com a API 37 instalada.
Abra a pasta do projeto no Android Studio, ou pela linha de comando:

```bash
./gradlew :app:installDebug
```

O build gera um APK por arquitetura (`arm64-v8a`, `armeabi-v7a`, `x86_64`), cada um com
50–70 MB por causa do Python e do ffmpeg empacotados. A maioria dos celulares usa
`arm64-v8a`.

## Estado atual

Todas as telas do mockup (`mockup/Convertia v3.dc.html`) estão implementadas.

- **Download: real**, com o yt-dlp. Lê os formatos do link, baixa no formato e contêiner
  escolhidos (juntando vídeo e áudio com o ffmpeg) e salva em `Download/Convertia`.
  Continua com o app fechado, com notificação de progresso, e respeita
  "Somente em Wi-Fi". O yt-dlp pode ser atualizado em Ajustes › Motor › Verificar.
- **Conversão: ainda simulada**, como no protótipo (progresso falso, nenhum arquivo
  gerado). É a próxima fase.
- Biblioteca, histórico, links recentes e fila ficam salvos no aparelho. Downloads
  interrompidos voltam pausados.

## Estrutura

```
app/src/main/java/com/convertia/app/
├── AppContainer.kt          # dependências (DI manual)
├── MainActivity.kt          # onboarding ou app
├── domain/                  # regras puras: catálogo de formatos, estimativas, modelos
├── data/                    # filas, biblioteca, ajustes, estado salvo
│   ├── engine/              # interfaces de motor, yt-dlp e motores simulados
│   ├── download/            # serviço em primeiro plano, notificações, rede
│   └── storage/             # gravação em Download/Convertia
├── share/                   # ShareActivity (menu Compartilhar)
└── ui/
    ├── AppViewModel.kt      # navegação (aba, sheet, voltar) e ações dos toasts
    ├── ConvertiaApp.kt      # barra inferior, sheets e toasts
    ├── components/          # design system do mockup
    ├── theme/               # cores, fontes (Inter, JetBrains Mono) e ícones
    └── download/ convert/ queue/ library/ settings/ onboarding/
```

Os motores ficam atrás das interfaces de `data/engine` (`MediaInspector`,
`DownloadEngine`, `ConversionEngine`): o de download é o yt-dlp (`data/engine/ytdlp`); o
de conversão ainda é o simulado.

## Publicar no GitHub

O workflow `.github/workflows/release.yml` gera os APKs assinados e cria um release a cada
tag `v*`. A versão do app vem da tag (`v0.2.0` → 0.2.0).

1. Crie a chave de assinatura (uma vez só, e **guarde-a bem**: sem ela não dá para
   publicar atualizações que instalem por cima):

   ```bash
   keytool -genkeypair -keystore convertia-release.jks -alias convertia -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Em **Settings › Secrets and variables › Actions** do repositório, crie as secrets:
   - `KEYSTORE_BASE64`: o conteúdo de `base64 -w0 convertia-release.jks`
   - `KEYSTORE_PASSWORD`: a senha do keystore
   - `KEY_ALIAS`: `convertia` (ou o alias que você usou)
   - `KEY_PASSWORD`: a senha da chave

3. Publique uma versão:

   ```bash
   git tag v0.2.0
   ```

   ```bash
   git push origin v0.2.0
   ```

Para assinar um build local, exporte `CONVERTIA_KEYSTORE_FILE`,
`CONVERTIA_KEYSTORE_PASSWORD`, `CONVERTIA_KEY_ALIAS` e `CONVERTIA_KEY_PASSWORD` antes de
`./gradlew :app:assembleRelease`. O workflow `ci.yml` roda testes, lint e build a cada push.

## Testes

```bash
./gradlew :app:testDebugUnitTest
```

Os valores esperados do domínio (tamanhos, formato sugerido, estimativas) foram obtidos
rodando a lógica JavaScript do próprio mockup. O leitor de formatos do yt-dlp é testado
com a saída real de um vídeo do YouTube com licença Creative Commons
(`app/src/test/resources/ytdlp/`).
