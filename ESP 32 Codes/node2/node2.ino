#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include "time.h"

const char* ssid = "YOUR_WIFI_SSID"; 
const char* password = "YOUR_WIFI_PASSWORD";
const char* mqttServer = "SERVERLINK";
const int mqttPort = 8883;
const char* mqttUsername = "USERNAME";
const char* mqttPassword = "PASSWORD";

// PIN ASSIGNMENTS
const int trig1 = 5; const int echo1 = 18;  // Ultra 1
const int trig2 = 19; const int echo2 = 21; // Ultra 2
const int irMotion = 13;
const int ldrDoor = 32;
const int ldrInternal = 35; // Inner compartment
const int ledBlue = 22; // Cloud Heartbeat
const int ledRed = 23;  // Alarm/Breach

WiFiClientSecure espClient;
PubSubClient client(espClient);
unsigned long lastPingTime = 0;
String lastStatus = "";

void setup() {
  Serial.begin(115200);
  pinMode(trig1, OUTPUT); pinMode(echo1, INPUT);
  pinMode(trig2, OUTPUT); pinMode(echo2, INPUT);
  pinMode(irMotion, INPUT);
  pinMode(ledBlue, OUTPUT); pinMode(ledRed, OUTPUT);
  
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) { delay(500); }
  
  configTime(19800, 0, "pool.ntp.org"); // IST
  espClient.setInsecure();
  client.setServer(mqttServer, mqttPort);
}

long getDistance(int trig, int echo) {
  digitalWrite(trig, LOW); delayMicroseconds(2);
  digitalWrite(trig, HIGH); delayMicroseconds(10);
  digitalWrite(trig, LOW);
  return pulseIn(echo, HIGH) * 0.034 / 2;
}

void reconnect() {
  while (!client.connected()) {
    if (client.connect("Node2_Monitor", mqttUsername, mqttPassword)) {
      digitalWrite(ledBlue, HIGH);
    } else { delay(5000); }
  }
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();

  // 1. HEARTBEAT (Blue LED Blink)
  if (millis() - lastPingTime > 60000) {
    digitalWrite(ledBlue, LOW);
    unsigned long now = time(nullptr);
    client.publish("vault/ping", String(now).c_str());
    lastPingTime = millis();
    digitalWrite(ledBlue, HIGH);
  }

  // 2. SENSOR LOGIC
  long d1 = getDistance(trig1, echo1);
  long d2 = getDistance(trig2, echo2);
  int motion = digitalRead(irMotion);
  int light1 = analogRead(ldrDoor);
  int light2 = analogRead(ldrInternal);

  String currentStatus = "SECURE";
  if (motion == LOW || d1 < 20 || d2 < 20) currentStatus = "WARNING: Motion Detected";
  if (d1 < 10 || d2 < 10) currentStatus = "CRITICAL: Proximity Breach";
  if (light1 > 2500 || light2 > 2500) currentStatus = "BREACH: Safe Opened";

  // 3. LED ALARM LOGIC
  if (currentStatus != "SECURE") {
    digitalWrite(ledRed, HIGH); // Alarm ON
  } else {
    digitalWrite(ledRed, LOW);  // Alarm OFF
  }

  if (currentStatus != lastStatus) {
    client.publish("vault/status", currentStatus.c_str());
    lastStatus = currentStatus;
  }
  delay(500);
}
