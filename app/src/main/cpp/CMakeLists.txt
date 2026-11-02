# libwebp alinhada a páginas de 16 KB. O ffmpeg do youtubedl-android traz a libwebp
# alinhada a 4 KB, que o Android não carrega em aparelhos com páginas de 16 KB; sem ela o
# ffmpeg nem abre. O app troca aquelas cópias por estas (ver WebpLibraries.kt).
cmake_minimum_required(VERSION 3.22.1)
project(convertia_webp C)

include(FetchContent)
FetchContent_Declare(libwebp
    URL https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.6.0.tar.gz
    URL_HASH SHA256=e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564
)

add_link_options("LINKER:-z,max-page-size=16384")
set(BUILD_SHARED_LIBS ON)
# Só as bibliotecas (libwebp, libwebpdecoder, libwebpdemux, libwebpmux, libsharpyuv).
foreach(tool ANIM_UTILS CWEBP DWEBP GIF2WEBP IMG2WEBP VWEBP WEBPINFO WEBPMUX EXTRAS)
    set(WEBP_BUILD_${tool} OFF CACHE BOOL "" FORCE)
endforeach()
FetchContent_MakeAvailable(libwebp)
