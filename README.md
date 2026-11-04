# Project Zero (Android)

Mini drama izleyici — ReelShort, DramaBox, NetShort ve DramaWave kaynaklarini tek Android uygulamasinda birlestirir.

## Gereksinimler

- Android Studio Ladybug veya uzeri (JDK 17)
- Android SDK 35

## Derleme

```bash
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Kurulum (ADB)

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Ozellikler

- Platform secimi ve dil (TR / EN)
- Bos aramada katalog / kategori sekmeleri
- NetShort sifreli API (AES + RSA) yerel istemci
- DramaWave icin cihaz uzerinde mobil ag relay (port 8899)

## Not

Bu depo yalnizca Android uygulamasini icerir. Eski web sunucusu ve Node araclari kaldirildi.
