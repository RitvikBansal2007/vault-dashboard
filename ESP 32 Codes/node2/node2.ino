#include <WiFi.h>
#include <FirebaseESP32.h>
#include "time.h"

const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";
#define FIREBASE_HOST "bank-vault-8e868-default-rtdb.firebaseio.com"
#define FIREBASE_AUTH "XSYlD5KsejgJzorB68mpuiXAbh9s25bcbAa64A3c"

// NTP Settings for Heartbeat
const char* ntpServer = "pool.ntp.org";
const long  gmtOffset_sec = 19800; // IST (UTC +5:30)
const int   daylightOffset_sec = 0;

// Sensor Pins
const int trigSafe = 5; const int echoSafe = 18;
const int irInnerPin = 13; const int ldrSafePin = 32;

FirebaseData fbData;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastPingTime = 0;
String lastStatus = "";

void setup() {
  Serial.begin(115200);
  pinMode(trigSafe, OUTPUT); pinMode(echoSafe, INPUT);
  pinMode(irInnerPin, INPUT);
  
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) { delay(500); }

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
  
  // Sync Time for Heartbeat
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
}

unsigned long getUnixTime() {
  time_t now;
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) return 0;
  time(&now);
  return now;
}

void loop() {
  // 1. HEARTBEAT: Update Last_Ping every 60 seconds
  if (millis() - lastPingTime > 60000) {
    unsigned long now = getUnixTime();
    if (now > 0) {
      Firebase.setInt(fbData, "Vault/Last_Ping", now);
      lastPingTime = millis();
    }
  }

  // 2. SENSOR LOGIC
  digitalWrite(trigSafe, LOW); delayMicroseconds(2);
  digitalWrite(trigSafe, HIGH); delayMicroseconds(10);
  digitalWrite(trigSafe, LOW);
  long dist = pulseIn(echoSafe, HIGH) * 0.034 / 2;

  int irSense = digitalRead(irInnerPin);
  int lightSense = analogRead(ldrSafePin);

  String currentStatus = "SECURE";
  if (irSense == LOW || dist < 20) currentStatus = "WARNING: Motion Detected";
  if (dist < 10) currentStatus = "CRITICAL: Proximity Breach";
  if (lightSense > 2500) currentStatus = "BREACH: Safe Opened";

  // 3. DATABASE UPDATE (Only on change)
  if (currentStatus != lastStatus) {
    Firebase.setString(fbData, "Vault/Status", currentStatus);
    lastStatus = currentStatus;
  }
  
  delay(500);
}