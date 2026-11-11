# PixelBot Android

A lightweight Android companion that displays a tiny pixel-art Claude-inspired mascot and reacts to VK and Telegram notifications.

## Current starter
- Kotlin / Android Gradle project
- Notification access listener (package-level detection only; message content is not read)
- Optional floating overlay, requiring the user's explicit overlay permission
- GitHub Actions workflow builds a debug APK on every push

## Build
Open the project in Android Studio, or use the **Android Debug APK** workflow in the Actions tab. The APK is uploaded as a workflow artifact.

## Permissions
Enable **Display over other apps** and **Notification access** for PixelBot in Android settings. XOS may also require allowing background activity / autostart.

## Privacy
The notification listener checks only the originating package name for VK and Telegram. It does not read notification title, sender, or message text.

## Status
Starter scaffold. The APK build is automated; device behavior and battery usage still need testing on the target phone.
