# OndaLibre — proyecto Android

Aplicación Android nativa (Kotlin + Jetpack Compose) para descubrir y reproducir audio de fuentes públicas y legales. Incluye reproductor en segundo plano mediante Media3, búsqueda de música independiente por Jamendo, búsqueda de pódcast y lectura de feeds RSS, radio en directo mediante Radio Browser, descargas de episodios/temas cuando el proveedor expone un enlace descargable y acceso externo a Spotify.

## Funciones implementadas

- **Música libre:** búsqueda en la API oficial de Jamendo. Requiere un Client ID gratuito propio, que se guarda localmente en el dispositivo. Jamendo solo muestra un botón de descarga cuando la API indica que la descarga está permitida.
- **Pódcast:** búsqueda en el catálogo público de iTunes/Apple Podcasts y lectura de episodios desde su RSS. Los episodios con URL de audio pueden reproducirse y descargarse para uso personal si las condiciones del proveedor lo permiten.
- **Radio:** búsqueda de emisoras y reproducción de streams directos mediante Radio Browser. La disponibilidad y estabilidad de cada stream dependen de la emisora.
- **Reproductor:** Media3/ExoPlayer, reproducción en segundo plano, notificación de medios y mini reproductor en la app.
- **Biblioteca:** listado de archivos descargados en el almacenamiento específico de la app.
- **Personalización:** tema oscuro con dos colores de acento.
- **Spotify:** botón que abre la aplicación oficial de Spotify o su web, con búsqueda opcional. No extrae audio, no bloquea anuncios y no imita una suscripción Premium.

## Importante sobre Spotify

La plataforma Spotify establece que el streaming de música mediante sus herramientas solo está disponible para usuarios Premium y que las aplicaciones deben respetar sus políticas. Por eso, OndaLibre **no** obtiene ni descarga canciones de Spotify ni mezcla su flujo de audio con los reproductores externos. La entrada de Spotify abre el servicio oficial fuera de OndaLibre. Para reproducción completa sin anuncios de Spotify, se aplican los requisitos de cuenta y suscripción de Spotify.

Documentación oficial:
- Spotify Android SDK: https://developer.spotify.com/documentation/android
- Política de Spotify: https://developer.spotify.com/policy
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

Este paquete contiene el **código fuente del proyecto**, no un APK precompilado. En el entorno en el que se preparó no están instalados Android SDK ni Gradle y no ha sido posible compilar o probar el APK aquí.

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
