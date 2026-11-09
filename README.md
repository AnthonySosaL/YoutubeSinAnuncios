# Zaza

Cliente de YouTube para Android **sin anuncios**, con tu cuenta de Google.

- **Creador:** Anthony Sosa
- **Fecha:** 8 de noviembre de 2026
- **Versión:** 2.0.0

## Qué hace

- **Sin anuncios ni rastreadores.**
- **Tu cuenta de Google:** inicias sesión una vez y luego:
  - el inicio muestra tus recomendaciones;
  - ves tus suscripciones y tus canales;
  - tienes tu historial, tus "Me gusta" y "Ver más tarde".
- **Acciones reales en tu cuenta:** me gusta / no me gusta, suscribirse, guardar en playlists y crear o borrar playlists. Todo se guarda en YouTube.
- **Historial:** los videos que ves aparecen en tu historial de YouTube.
- **Reproductor:**
  - calidad hasta 1080p, velocidad y subtítulos (español primero);
  - reproducción automática;
  - segundo plano y ventanita (picture-in-picture);
  - gestos de volumen, brillo y adelantar.
- **Comentarios, pestañas de canal** (Videos, Shorts, En vivo, Playlists) y **filtros de búsqueda**.
- **Enlaces de YouTube:** los que abras desde otras apps (WhatsApp, navegador…) se abren en Zaza.

## Compilar

Requisitos: JDK 17 y el SDK de Android (compileSdk 35).

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Tecnología

- Kotlin y Jetpack Compose (Material 3).
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) para obtener los videos.
- Media3 / ExoPlayer como reproductor.
- La API interna de YouTube (InnerTube), con la sesión del usuario, para las acciones de cuenta.

## Versión anterior

La primera versión del proyecto (app en Java) está en [`old-version/`](old-version/).
