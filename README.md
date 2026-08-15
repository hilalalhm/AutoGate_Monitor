# 🚧 Automatic Gate Monitoring System

An IoT-based automatic gate monitoring system using **ESP32** and an Android application. The system automatically controls a servo-based gate using ultrasonic sensors, monitors light conditions using an LDR sensor, and provides real-time monitoring through a mobile application.

## ✨ Features

### ESP32

* 🚧 Automatic gate control
* 📡 Wi-Fi Access Point
* 📏 Dual ultrasonic distance sensors
* 💡 LDR-based light monitoring
* 🔄 Automatic gate opening and closing
* 🎛️ Manual gate control through HTTP commands
* 📊 Real-time sensor data API

### Android Application

* 📱 Real-time gate monitoring
* 📏 Displays ultrasonic sensor readings
* 💡 Displays LDR sensor values
* 🔔 Automatic gate notifications
* 📜 Activity history
* 🗑️ Clear activity history
* 📶 Direct communication with ESP32 via Wi-Fi
* 🎨 Modern UI using Jetpack Compose

## 🛠️ Hardware Components

* ESP32
* 2× Ultrasonic Sensor
* Servo Motor
* LDR Sensor
* LED
* Resistors
* Jumper Wires
* Power Supply

## 💻 Software & Technologies

### ESP32

* Arduino IDE
* C++
* ESP32 Wi-Fi
* ESP32Servo Library
* HTTP Server

### Android

* Kotlin
* Jetpack Compose
* Material 3
* Android Navigation
* OkHttp
* StateFlow
* ViewModel

## ⚙️ System Architecture

```text
                ┌──────────────────┐
                │  Ultrasonic #1   │
                └────────┬─────────┘
                         │
                ┌────────▼─────────┐
                │                  │
                │      ESP32       │◄──── LDR Sensor
                │                  │
                └───────┬──────────┘
                        │
                 ┌──────▼──────┐
                 │ Servo Motor │
                 │    Gate     │
                 └─────────────┘
                        │
                        │ Wi-Fi
                        ▼
               ┌─────────────────┐
               │ Android Monitor │
               │ Kotlin + Compose│
               └─────────────────┘
```

## 📡 Wi-Fi Configuration

The ESP32 creates its own Wi-Fi Access Point:

```text
SSID      : Palang Pintu
Password  : palang123
IP Address: 192.168.10.10
```

Connect the Android device to this Wi-Fi network before starting the monitoring application.

## 🚧 Automatic Gate Logic

The gate automatically opens when either ultrasonic sensor detects an object within **50 cm**.

```text
Sensor 1 ≤ 50 cm ──┐
                   ├──► Gate OPEN
Sensor 2 ≤ 50 cm ──┘

Sensor 1 > 50 cm
        AND
Sensor 2 > 50 cm
        │
        ▼
    Gate CLOSE
```

The servo motor uses:

```text
0°  → Gate Closed
90° → Gate Open
```

## 🌐 HTTP Endpoints

The ESP32 provides simple HTTP endpoints for communication with the Android application.

| Endpoint | Function        |
| -------- | --------------- |
| `/`      | Get sensor data |
| `/OPEN`  | Open the gate   |
| `/CLOSE` | Close the gate  |

### Sensor Data Response

The ESP32 returns JSON data:

```json
{
  "sensor1": 25,
  "sensor2": 80,
  "ldr": 620
}
```

Where:

* `sensor1` — Distance from ultrasonic sensor 1 in cm
* `sensor2` — Distance from ultrasonic sensor 2 in cm
* `ldr` — LDR analog reading

## 🔔 Android Notifications

The Android application monitors the sensor data and detects changes in the gate state.

When an object is detected within the configured distance, the application records:

```text
Palang Terbuka (Otomatis)
```

When the object is no longer detected:

```text
Palang Tertutup (Otomatis)
```

The application can also display a notification when the gate opens automatically.

## 📁 Project Structure

```text
Automatic-Gate-Monitoring/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/monitor/
│           │       ├── MainActivity.kt
│           │       └── ui/
│           │           └── theme/
│           └── res/
│
├── final.ino
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## 🚀 Installation

### ESP32

1. Open `final.ino` using Arduino IDE.
2. Install the ESP32 board package.
3. Install the `ESP32Servo` library.
4. Connect the required sensors and servo motor.
5. Upload the program to the ESP32.
6. Open the Serial Monitor at **115200 baud**.
7. Verify the ESP32 Access Point IP address.

### Android

1. Open the project in Android Studio.
2. Connect an Android device or start an emulator.
3. Build and run the application.
4. Connect the Android device to the ESP32 Wi-Fi network.
5. Launch the monitoring application.

## 🔌 Pin Configuration

| Component         | ESP32 Pin |
| ----------------- | --------: |
| Ultrasonic 1 TRIG |   GPIO 23 |
| Ultrasonic 1 ECHO |   GPIO 22 |
| Ultrasonic 2 TRIG |   GPIO 19 |
| Ultrasonic 2 ECHO |   GPIO 18 |
| Servo Motor       |   GPIO 21 |
| LED               |   GPIO 25 |
| LDR               |   GPIO 34 |

## 🔮 Future Improvements

* 📊 Real-time sensor charts
* ☁️ Cloud-based monitoring
* 🗄️ Database for permanent activity history
* 🔐 User authentication
* 📷 Camera integration
* 🚗 Vehicle detection
* 📱 Remote gate control over the Internet
* ⚡ Improved power management

## ⚠️ Safety

Make sure the ESP32, servo motor, sensors, and power supply are connected correctly. Use an appropriate power supply for the servo and avoid exceeding the ESP32 GPIO electrical limits.

## 📄 License

This project is intended for educational and personal use.

---

Made with 🚧 by **Hilal Al Hamdi**
