#include <ESP32Servo.h>
#include <WiFi.h>

// Konfigurasi Nama Wi-Fi dan Password Access Point
const char* ssid = "Palang Pintu";
const char* password = "palang123";

// Konfigurasi IP Statis (Memperbaiki masalah RTO)
IPAddress local_IP(192, 168, 10, 10); 
IPAddress gateway(192, 168, 10, 1);   
IPAddress subnet(255, 255, 255, 0);  

WiFiServer server(80);

// Membuat objek servo
Servo gateServo;

// Pin Sensor Ultrasonik 1 (Masuk)
int trigPin1 = 23;
int echoPin1 = 22;

// Pin Sensor Ultrasonik 2 (Keluar)
int trigPin2 = 19;
int echoPin2 = 18;

// Pin Servo, LED, dan LDR
int servoPin = 21;
int ledPin = 25;
int ldrPin = 34;

// Batas sensor cahaya LDR
int lightThreshold = 500; 

long duration1, duration2;
int distance1, distance2;

bool gateOpened = false;
bool manualMode = false; // true jika sudah pernah dikontrol manual via /OPEN atau /CLOSE
unsigned long lastDetectedTime = 0;
const unsigned long holdDuration = 1000; // servo tetap terbuka 1 detik setelah objek hilang

void setup() {
  Serial.begin(115200);
  delay(10);

  // 1. Alokasi Timer PWM untuk Servo (Mencegah servo bergetar/dengung saat Wi-Fi aktif)
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);
  gateServo.setPeriodHertz(50); 

  // 2. AKTIVASI IP STATIS (Solusi utama agar IP 192.168.10.10 bisa diakses)
  WiFi.softAPConfig(local_IP, gateway, subnet);

  // Menyalakan Wi-Fi Access Point
  WiFi.softAP(ssid, password);
  
  // Menampilkan IP yang aktif di Serial Monitor untuk memastikan
  IPAddress IP = WiFi.softAPIP();
  Serial.print("Access Point Berhasil Dibuat! IP Address: ");
  Serial.println(IP); 

  server.begin();

  // Konfigurasi Mode Pin I/O
  pinMode(trigPin1, OUTPUT);
  pinMode(echoPin1, INPUT);
  pinMode(trigPin2, OUTPUT);
  pinMode(echoPin2, INPUT);
  pinMode(ledPin, OUTPUT);

  // Hubungkan servo ke Pin 21
  gateServo.attach(servoPin, 500, 2400);   
  gateServo.write(0); // Posisi awal palang pintu tertutup (0 derajat)
}

// Fungsi pembaca jarak ultrasonik
int readUltrasonic(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duration = pulseIn(echoPin, HIGH, 30000); // Timeout 30ms agar tidak hang
  int distance = duration * 0.034 / 2;

  return distance;
}

void loop() {
  // 3. Logika Sensor Otomatisasi Lampu (LDR)
  int ldrValue = analogRead(ldrPin);
  digitalWrite(ledPin, ldrValue > lightThreshold ? HIGH : LOW);

  // Baca jarak dari kedua sensor ultrasonik
  distance1 = readUltrasonic(trigPin1, echoPin1);
  distance2 = readUltrasonic(trigPin2, echoPin2);

  // 4. Logika Palang Pintu Otomatis (Gerakan Cepat 0 atau 90 derajat)
  bool sensor1Detected = distance1 > 0 && distance1 <= 50;
  bool sensor2Detected = distance2 > 0 && distance2 <= 50;

  // Logika sensor otomatis hanya berjalan selama belum ada override manual
  if (!manualMode) {

    // Keluar: Sensor 2 boleh membuka palang langsung dari kondisi idle
    if (!gateOpened && sensor2Detected) {
      gateOpened = true;
      gateServo.write(90);
      lastDetectedTime = millis(); // set timer awal saat gerbang mulai terbuka
    }

    if (gateOpened) {

      if (sensor1Detected || sensor2Detected) {

        gateServo.write(90);
        lastDetectedTime = millis(); // reset timer selama masih ada objek

      } else if (millis() - lastDetectedTime >= holdDuration) {

        gateServo.write(0);
        gateOpened = false;
      }
    }
  }

  delay(100); // Jeda pembacaan loop

  // 5. Logika Pengendali Jarak Jauh via Web Browser / Request JSON
  WiFiClient client = server.available();
  if (client) {
    String req = client.readStringUntil('\r');
    client.flush();

    // Jika menerima perintah IP_ESP/OPEN
    if (req.indexOf("/OPEN") != -1) {
      manualMode = true;   // override mutlak, sensor otomatis nonaktif
      gateOpened = true;
      gateServo.write(90);

      client.print("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n");
      client.print("Gate Opened");
    } 
    else if (req.indexOf("/CLOSE") != -1) {
      manualMode = false;  // kembali ke alur otomatis
      gateOpened = false;
      gateServo.write(0);

      client.print("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n");
      client.print("Gate Closed");
    }
    // Jika mengakses halaman utama IP_ESP (Membaca data sensor via browser)
    else if (req.indexOf("GET / ") != -1) {
      client.print("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n");
      client.print("{");
      client.print("\"sensor1\":");
      client.print(distance1);
      client.print(",");
      client.print("\"sensor2\":");
      client.print(distance2);
      client.print(",");
      client.print("\"ldr\":");
      client.print(ldrValue);
      client.print(",");
      client.print("\"gate\":");
      client.print(gateOpened ? "true" : "false");
      client.print(",");
      client.print("\"manualMode\":");
      client.print(manualMode ? "true" : "false");
      client.print("}");
    }
    else {
      // Mengembalikan respon default jika request tidak dikenal
      client.print("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
    }

    delay(1);
    client.stop(); // Putuskan koneksi client setelah data terkirim
  }
}