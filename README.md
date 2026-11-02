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
| Motor de download | yt-dlp via youtubedl-android 0.18.1 (Python 3.12, QuickJS, ffmpeg 7.1) |
| Documentos | LibreOffice 26.2 (LibreOfficeKit), do APK do LibreOffice Viewer no F-Droid |
| PDF → documento | PdfBox-Android 2.0.27 + reconstrução própria |
| OCR | ML Kit Text Recognition 16.0.1 (modelo latino embutido) |
| HTML | jsoup 1.23.2 (HTML → ODT plano) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 37 / 37 |

As versões ficam em [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Rodando

Precisa de JDK 17 ou mais novo e do Android SDK com a API 37, o NDK 28.2 e o CMake 3.22.1
(o Gradle instala os que faltarem se a licença do SDK estiver aceita).
Abra a pasta do projeto no Android Studio, ou pela linha de comando:

```bash
./gradlew :app:installDebug
```

O build gera um APK por arquitetura (`arm64-v8a`, `armeabi-v7a`, `x86_64`), cada um com
140–170 MB: Python e ffmpeg (~50 MB), OCR e PdfBox (~20 MB) e o LibreOffice (~80 MB). Instalado ocupa mais,
porque as bibliotecas nativas são extraídas. A maioria dos celulares usa `arm64-v8a`.

O LibreOffice vem do APK oficial do LibreOffice Viewer publicado pelo F-Droid: o build
baixa, confere o SHA-256 (`gradle/libreoffice.properties`) e extrai as bibliotecas e os
assets. Sem acesso ao f-droid.org, compile com `-Pconvertia.office=false`; o app então
não oferece conversões de documentos. O workflow `libreoffice-inspect.yml` lista as
versões disponíveis e regenera o `.properties`.

O ffmpeg do youtubedl-android traz a libwebp alinhada a 4 KB, que o Android não carrega em
aparelhos com páginas de 16 KB (e sem ela o ffmpeg não abre). O build compila a libwebp 1.6.0
alinhada a 16 KB (`app/src/main/cpp`, fonte baixada com SHA-256 conferido) para arm64-v8a e
x86_64, e nesses aparelhos o app troca as cópias extraídas por ela (`WebpLibraries.kt`).

## Estado atual

Todas as telas do mockup (`mockup/Convertia v3.dc.html`) estão implementadas.

- **Download: real**, com o yt-dlp. Lê os formatos do link, baixa no formato e contêiner
  escolhidos (juntando vídeo e áudio com o ffmpeg) e salva em `Download/Convertia`.
  Continua com o app fechado, com notificação de progresso, e respeita
  "Somente em Wi-Fi". O yt-dlp se atualiza sozinho uma vez por dia, ao abrir o app (o que vem na biblioteca
  envelhece e o YouTube passa a responder 403), e também em Ajustes › Motor › Verificar.
- **Conversão: real.**
  - Áudio e vídeo com o ffmpeg empacotado (MediaCodec quando a aceleração por hardware
    está ligada, com volta automática para software).
  - Imagens com as APIs do Android (HEIC pelo HeifWriter, SVG pela AndroidSVG) e o ffmpeg
    para AVIF/TIFF/BMP/GIF/ICO; imagem → PDF e PDF → imagens.
  - Compactados com o Commons Compress; RAR pela libarchive.
  - Documentos, planilhas, apresentações e e-books com o LibreOffice embutido, num
    processo à parte (`:office`): DOCX/ODT/RTF/TXT/HTML/MD/PDF/EPUB. O LibreOffice do
    Android não abre HTML (não traz o Writer/Web), então páginas HTML e os capítulos de
    EPUB viram ODT plano por um conversor próprio (jsoup) antes; FB2 sai de um gravador
    próprio sobre o ODT plano exportado.
  - PDF → DOCX/ODT/RTF/HTML/MD/TXT/EPUB/FB2 por reconstrução própria (`data/engine/pdf`):
    lê texto, desenhos e imagens com posição (PdfBox-Android), usa a árvore de estrutura
    quando o PDF é tagueado e, quando não é, remonta parágrafos, títulos, listas, tabelas
    (com e sem grade), colunas, cabeçalho e rodapé; grava um ODT plano que o LibreOffice
    exporta. Bom em PDF digital; aproximado em layouts complexos.
  - OCR com o ML Kit (modelo latino embutido, ~17 MB por arquitetura): imagem → texto/
    documento, imagem → PDF pesquisável e páginas digitalizadas de PDF. O ML Kit é código
    fechado do Google; as imagens não saem do aparelho, mas, pelos termos do Google, a
    biblioteca pode enviar métricas de uso anônimas.
  - MOBI/AZW3 ficam fora (formato fechado da Amazon; o Kindle aceita EPUB).
- Biblioteca, histórico, links recentes e fila ficam salvos no aparelho. Downloads
  interrompidos voltam pausados.

## Estrutura

```
app/src/main/java/com/convertia/app/
├── AppContainer.kt          # dependências (DI manual)
├── MainActivity.kt          # onboarding ou app
├── domain/                  # regras puras: catálogo de formatos, estimativas, modelos
├── data/                    # filas, biblioteca, ajustes, estado salvo
│   ├── convert/             # pasta de trabalho e motor composto (escolhe o conversor)
│   ├── engine/              # yt-dlp, ffmpeg, imagens, compactados, LibreOffice (office/)
│   ├── download/            # serviço em primeiro plano, notificações, rede
│   └── storage/             # gravação em Download/Convertia
app/src/main/java/org/libreoffice/kit/   # ponte LibreOfficeKit (MPL-2.0)
├── share/                   # ShareActivity (menu Compartilhar)
└── ui/
    ├── AppViewModel.kt      # navegação (aba, sheet, voltar) e ações dos toasts
    ├── ConvertiaApp.kt      # barra inferior, sheets e toasts
    ├── components/          # design system do mockup
    ├── theme/               # cores, fontes (Inter, JetBrains Mono) e ícones
    └── download/ convert/ queue/ library/ settings/ onboarding/
```

Os motores ficam atrás das interfaces de `data/engine` (`MediaInspector`,
`DownloadEngine`, `ConversionEngine`). O de download é o yt-dlp (`data/engine/ytdlp`). O
de conversão é o `CompositeConversionEngine` (`data/convert`): copia a origem para uma
pasta de trabalho, escolhe o `FileConverter` pelo par origem → destino e grava a saída
respeitando o ajuste de conflito. Sem conversor para o par, o item falha com aviso.

## Publicar no GitHub

O workflow `.github/workflows/release.yml` gera os APKs assinados e cria um release a cada
tag `v*`. A versão do app vem da tag (`v0.3.0` → 0.3.0).

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
   git tag v0.3.0
   ```

   ```bash
   git push origin v0.3.0
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
(`app/src/test/resources/ytdlp/`). Quando há `ffmpeg` ou `soffice` no PATH, alguns testes
rodam os argumentos gerados neles de verdade (e são pulados quando não há).

Os motores rodam de verdade num emulador x86_64 no workflow `instrumented.yml`
(`app/src/androidTest`), inclusive o LibreOffice.
