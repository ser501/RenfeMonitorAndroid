# OndaLibre — proyecto Android

Esta carpeta contiene la app Android de música y pódcast OndaLibre.

## Compilar el APK
El workflow `.github/workflows/build-ondalibre.yml` intenta compilar el proyecto con GitHub Actions y publicar el APK como artefacto.

> Nota: el APK solo estará disponible si la estructura completa del proyecto Android (incluido `app/build.gradle`, `settings.gradle` y el código fuente) está incluida en esta carpeta. Este repositorio no descarga automáticamente el ZIP que está adjunto en la conversación.

## Fuentes y limitaciones
- Spotify: acceso a la aplicación oficial; no se extrae ni retransmite audio protegido de Spotify.
- Música independiente: integración prevista con fuentes legales como Jamendo (puede exigir un Client ID).
- Radio: reproducción de streams de emisoras públicas; la disponibilidad depende de sus URLs.
- Pódcast: reproducción de feeds RSS compatibles.
- Descargas sin conexión: solo para archivos/episodios que el proveedor permita descargar.

Esta app no promete acceso gratuito sin anuncios a todo el catálogo de Spotify.
