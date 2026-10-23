# Onda Libre

App Android de música **gratis, legal y sin anuncios**, con **radio de canciones relacionadas** al estilo Spotify.

- **Buscar** canciones, artistas o géneros del catálogo de Jamendo (más de 600.000 temas con licencia Creative Commons).
- **Radio**: en cualquier canción pulsa ⋮ → *Iniciar radio* (o desde el reproductor) y la app va encadenando canciones parecidas por género, ambiente, ritmo y artista, sin repetir y sin fin. También hay radios por género (Rock, Metal, Hip Hop, Electrónica…).
- **Favoritos** guardados en el móvil.
- **Mi música**: reproduce tus MP3/FLAC/M4A del teléfono.
- Sigue sonando **en segundo plano y con la pantalla apagada**, con controles en la notificación, pantalla de bloqueo y auriculares Bluetooth. Se pausa sola al quitar los auriculares.

## 1. Consigue tu Client ID de Jamendo (gratis, 2 minutos)

1. Entra en https://devportal.jamendo.com y crea una cuenta.
2. *My applications* → crea una aplicación (cualquier nombre y web, por ejemplo `https://github.com`).
3. Copia el **Client ID**. Lo pegarás en la app, en **Ajustes**.

## 2. Compilar la APK sin instalar nada (GitHub Actions)

1. Crea un repositorio nuevo en GitHub (puede ser privado) y sube **todo el contenido** de esta carpeta
   (en la web: *Add file → Upload files*, arrastra las carpetas y archivos, incluida `.github`).
   > Si la web no te deja subir la carpeta `.github` (empieza por punto), usa GitHub Desktop o `git push`.
2. Ve a la pestaña **Actions**: la compilación «Compilar APK» arranca sola (tarda 4–6 min).
   Si no arranca, entra en ella y pulsa **Run workflow**.
3. Cuando salga en verde, abre la ejecución y descarga **OndaLibre-apk** (abajo, en *Artifacts*). Dentro está `OndaLibre.apk`.
4. Pásala al móvil, ábrela y permite *Instalar apps desconocidas* para tu gestor de archivos.

Opcional: si añades un secreto `JAMENDO_CLIENT_ID` en *Settings → Secrets and variables → Actions*, la APK ya vendrá con la clave puesta.

## 2b. Alternativa: Android Studio

Abre la carpeta con Android Studio (Ladybug o más nuevo), espera a que sincronice y pulsa
*Build → Build App Bundle(s) / APK(s) → Build APK(s)*.

## Requisitos

Android 8.0 o superior.
