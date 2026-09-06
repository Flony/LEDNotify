# 🔴 LED Notify — AODiode Notification & Charging Indicator
## ✨ Let's bring back the era of the notification LED.
**LED Notify** is a lightweight, privacy-first, zero-battery-drain notification LED indicator designed for Android devices with AMOLED / OLED displays (especially those with front camera punch-hole cutouts like Nothing Phone (2), Google Pixel 9, Xiaomi 11T Pro, and others).


---
[![Buy Me A Coffee](https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png)](https://buymeacoffee.com/flony)

## ✨ Features

- 🎯 **AODiode Camera Cutout Overlay**: Custom notification indicator positioned precisely around your front camera punch-hole.
- 📐 **4 Customizable Shapes**:
  - **`(DOT)`**: Solid Filled Circle
  - **`(RING)`**: Hollow Stroke Circle
  - **`(PILL)`**: Solid Stadium / Pill
  - **`(EMPTY_PILL)`**: Hollow Stroke Stadium / Pill
  - **Multi-Touch Gestures**: Adjust Pill width and height directly on screen using 2-finger pinch/stretch gestures or step-by-step arrows.
- ⚡ **True 1Hz ECO Mode (LTPO Hardware Locking)**: For devices with variable refresh rate LTPO displays (like Nothing Phone (2), Pixel 9, Galaxy S24), locks display hardware refresh rate strictly to **1 Hz** during illumination for absolute minimum power consumption.
- 🔴 **Power-Efficient Default Color**: Uses pure **Red (`#FF0000`)** by default — only red subpixels illuminate on AMOLED displays, drawing up to 75% less power than white or blue.
- 🎨 **Per-App Custom Colors**: Assign distinct colors to specific applications or system events (missed calls, SMS). Unconfigured apps automatically follow your global color setting.
- 🛡️ **Whitelist / Blacklist App Filter**: Choose exactly which apps are allowed to illuminate AODiode with a 1-tap "Invert Selection" button for instant configuration. Blacklisted apps are clearly badged in orange.
- ⚙️ **Advanced Settings**:
  - **AODiode Timeout**: Automatically turns off AODiode after a configurable duration (15 min to 12 hours, default 60 min) to prevent unnecessary battery usage.
  - **Quiet Hours & System DND**: Set custom quiet hours (e.g. 23:00 to 07:00) or automatically respect Android system **Do Not Disturb (DND)** mode.
  - **Low Battery Auto-Disable**: Automatically disables AODiode when battery drops below a set threshold (e.g., 20%).
  - **Charging LED Indicator**: Displays AODiode when connected to charger (**Orange** = Charging, **Green** = Fully Charged 100%).
- 🎛️ **Quick Settings Tile**: Add a "LED Notify" tile to your Android status bar pulldown menu to toggle the app ON or OFF with a single tap.
- 🛡️ **Burn-in Protection**:
  - **Subpixel-Dithering**: Alternating checkerboard subpixel grid (Privacy Display principle) for zero positional movement and maximum pixel longevity.
  - **Pixel-Shift**: Micro-position shifting every 60 seconds to distribute wear evenly.
- 🌗 **Adaptive Light & Dark Themes**: Fully optimized with persistent sticky bottom action bars for both Dark and Light system themes.
- 🔒 **100% Offline & Private**:
  - **Zero Internet Permission** (`android.permission.INTERNET` is completely absent in Manifest).
  - No analytics, no data collection, no external servers. 100% local on-device processing.
- 🌍 **Multi-Language Support**: Supports 9 languages out of the box (English, Čeština, Deutsch, Slovensky, Polski, Español, Português, 简体中文).

---

## 📱 Supported Devices

Optimized for all Android devices running **Android 8.1 (API 27) up to Android 15 (API 35)** with OLED/AMOLED screens:
- **Nothing Phone (1) / (2) / (2a)**
- **Google Pixel 6 / 7 / 8 / 9 Series**
- **Xiaomi / Redmi / POCO (MIUI & HyperOS)**
- **Samsung Galaxy S / Z Series (One UI)**
- **OnePlus / Realme / Motorola**

---

## 🛠️ Architecture & Tech Stack

- **Language**: 100% Idiomatic Kotlin
- **UI & Layouts**: Native Android Framework (Zero-allocation GC-optimized custom `View` drawing via `Canvas` & `BitmapShader`)
- **System Integration**:
  - `NotificationListenerService` for lightweight notification monitoring.
  - `TileService` for Quick Settings Tile integration.
  - Android `Display.supportedModes` & `WindowManager.LayoutParams` for hardware LTPO 1Hz refresh rate locking.
  - Display Cutout API (`layoutInDisplayCutoutMode = SHORT_EDGES / ALWAYS`) for seamless edge-to-edge drawing into notch/camera cutout areas.

---

## 🚀 Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/Flony/LEDNotify.git
   ```
2. Open the project in **Android Studio (Ladybug / 2024.2+)**.
3. Build the debug APK using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install on your device:
   ```bash
   ./gradlew installDebug
   ```

---

## 💖 Support

If you like **LED Notify** and find it useful, you can support further development by buying me a coffee:

[![Buy Me A Coffee](https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png)](https://buymeacoffee.com/flony)

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
