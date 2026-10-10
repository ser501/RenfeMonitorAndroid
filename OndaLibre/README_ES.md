# OndaLibre — proyecto Android

Aplicación Android nativa (Kotlin + Jetpack Compose) para descubrir y reproducir audio de fuentes públicas y legales. Incluye reproductor en segundo plano mediante Media3, búsqueda de música independiente por Jamendo, búsqueda de pódcast y lectura de feeds RSS, radio musical por estilos con cola continua, radio en directo mediante Radio Browser y descargas autorizadas de episodios/temas.

## Funciones implementadas

- **Música integrada:** búsqueda en la API oficial de Jamendo y reproducción con ExoPlayer dentro de OndaLibre, sin abrir otro reproductor. Requiere un Client ID gratuito propio, que se guarda localmente en el dispositivo. Jamendo solo muestra un botón de descarga cuando la API indica que la descarga está permitida.
- **Radio musical personalizada:** elige Rock, Metal, Pop, Electrónica, Hip hop, Chill, Lo-fi o Jazz; OndaLibre crea una cola aleatoria y la reproduce en bucle. También puedes crear una radio a partir de un artista o tema. La selección depende del catálogo autorizado de Jamendo.
- **Pódcast:** búsqueda en el catálogo público de iTunes/Apple Podcasts y lectura de episodios desde su RSS. Los episodios con URL de audio pueden reproducirse y descargarse para uso personal si las condiciones del proveedor lo permiten.
- **Radio:** búsqueda de emisoras y reproducción de streams directos mediante Radio Browser. La disponibilidad y estabilidad de cada stream dependen de la emisora.
- **Reproductor:** Media3/ExoPlayer, reproducción en segundo plano, notificación de medios y mini reproductor en la app.
- **Biblioteca:** listado de archivos descargados en el almacenamiento específico de la app.
- **Personalización:** tema oscuro con dos colores de acento.
- **Sin redirecciones a Spotify/YouTube:** la app no abre esos servicios para reproducir música. El catálogo integrado usa fuentes que permiten el streaming autorizado.

## Sobre Spotify y YouTube

OndaLibre no extrae ni copia canciones protegidas de Spotify o YouTube ni evita sus sistemas de acceso. Sus catálogos no se pueden convertir en una fuente de audio libre para otra app sin autorización. Por eso, la reproducción integrada usa el catálogo de Jamendo y streams de emisoras, que se escuchan directamente en OndaLibre. Para incorporar canciones comerciales concretas sería necesario disponer de archivos con licencia o de un proveedor que autorice su reproducción dentro de esta aplicación.

Documentación:
- API de Jamendo: https://developer.jamendo.com/v3.0/docs
- Radio Browser: https://www.radio-browser.info/
- Apple Search API: https://itunes.apple.com/search

## Cómo generar el APK en Android Studio

1. Instala una versión reciente de **Android Studio** y los componentes Android SDK Platform 35 y Build Tools que sugiera el IDE.
2. Abre la carpeta `OndaLibre` como proyecto existente.
3. Configura Gradle JDK en **Java 17** o en el JDK 17 incluido con Android Studio.
4. Acepta la sincronización Gradle y deja que descargue las dependencias. Hace falta conexión a Internet la primera vez.
5. Selecciona **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. El APK de depuración aparecerá normalmente en `app/build/outputs/apk/debug/app-debug.apk`.

Alternativa desde terminal, si ya tienes Gradle 8.9 instalado en el PATH:

```bash
gradle assembleDebug
```

El APK de depuración se genera con GitHub Actions. La compilación automática comprueba que el código compila, pero no sustituye una prueba de reproducción en un dispositivo real.

## Configurar Jamendo

1. Entra en https://developer.jamendo.com/ y registra una aplicación.
2. Copia el `Client ID` proporcionado.
3. Abre OndaLibre → Música → pega el identificador → **Guardar ID**.
4. Busca un artista, una canción o un estilo.

No publiques tu identificador si tu proyecto de Jamendo tiene condiciones que lo desaconsejen. La aplicación lo guarda en preferencias locales, no en un servidor propio.

## Límites y privacidad

- Se necesitan permisos de Internet y conexión para consultar los catálogos y reproducir streams.
- Las descargas se guardan en el espacio específico de OndaLibre. Android puede eliminarlas al desinstalar la app.
- Una URL accesible no significa automáticamente que el audio tenga una licencia abierta: respeta las condiciones del creador, feed o emisora.
- La radio depende de servicios externos y algunas emisoras pueden fallar, geobloquearse o incluir su propia publicidad.
- Este proyecto no incorpora publicidad propia ni elimina la publicidad insertada por los proveedores.
