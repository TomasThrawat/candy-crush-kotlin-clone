# 🍬 Candy Crush Kotlin Clone

A **Candy Crush Saga**–style match-3 game built natively in **Kotlin** for Android.  
Swap adjacent candies to match 3 or more of the same color, score points, and try not to run out of moves!

---

## ✨ Features

- 🎨 **Colorful candy grid** (8x8 board)
- 🔄 **Tap-to-swap mechanics** with swipe gesture support
- 💥 **Match-3 detection** (horizontal & vertical)
- 🎯 **Score system** with animated point popups
- ❤️ **Limited moves** per level
- ⏱️ Smooth animations & cascading candies
- 🔊 Sound effects (optional)
- 📱 Material Design UI

---

## 🛠 Tech Stack

| Layer       | Tech                       |
|-------------|----------------------------|
| Language    | Kotlin 1.9.x               |
| Min SDK     | API 21 (Android 5.0)       |
| Target SDK  | API 34 (Android 14)        |
| Build       | Gradle 8.x                 |
| UI          | Custom View + Canvas       |
| Architecture| MVVM-lite                  |

---

## 🚀 Build the APK

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- Android SDK 34

### Steps
1. Clone the repo
   ```bash
   git clone https://github.com/TomasThrawat/candy-crush-kotlin-clone.git
   cd candy-crush-kotlin-clone
   ```
2. Open the project in **Android Studio**
3. Let Gradle sync
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. The signed APK will appear at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Command line build
```bash
./gradlew assembleDebug
```

---

## 📂 Project Structure

```
candy-crush-kotlin-clone/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/tomasthrawat/candycrush/
│   │   │   ├── MainActivity.kt
│   │   │   ├── model/Candy.kt
│   │   │   ├── model/GameBoard.kt
│   │   │   ├── view/GameView.kt
│   │   │   └── viewmodel/GameViewModel.kt
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       ├── values/colors.xml
│   │       ├── values/strings.xml
│   │       └── values/themes.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## 🎮 How to Play

1. Tap a candy, then tap an **adjacent** candy to swap them.
2. Make a line of **3 or more** identical candies (horizontal or vertical).
3. Matched candies disappear, candies above fall down, new candies spawn from the top.
4. Chain reactions give **combo bonuses**.
5. Reach the target score before running out of **moves** to win!

---

## 📜 License

MIT License — feel free to use, modify, and distribute.

---

## 🙏 Credits

Inspired by **King's Candy Crush Saga**. This is an educational clone built from scratch.
