#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>

const char* ssid = "YOUR_WIFI_SSID"; 
const char* password = "YOUR_WIFI_PASSWORD";
const char* mqttServer = "893f221f30f648dbbfd49580f6bf8ad6.s1.eu.hivemq.cloud";
const int mqttPort = 8883;
const char* mqttUsername = "vault_admin";
const char* mqttPassword = "12345#@aA#@12345";

// PIN ASSIGNMENTS
const int pulsePinEntry = 32;
const int pulsePinExit = 33;
const int irEntryDetect = 25; // NEW: Detecting presence at the door
const int ledYellow = 26; // System Ready
const int ledGreen = 27;  // Access Granted

const int pulseThreshold = 2500;

WiFiClientSecure espClient;
PubSubClient client(espClient);

void setup() {
  Serial.begin(115200);
  pinMode(irEntryDetect, INPUT);
  pinMode(ledYellow, OUTPUT);
  pinMode(ledGreen, OUTPUT);
  
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) { delay(500); }

  espClient.setInsecure();
  client.setServer(mqttServer, mqttPort);
}

void reconnect() {
  while (!client.connected()) {
    if (client.connect("Node1_Gatekeeper", mqttUsername, mqttPassword)) {
      digitalWrite(ledYellow, HIGH); // Ready indicator
    } else { delay(5000); }
  }
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();

  int entryAuth = analogRead(pulsePinEntry);
  int exitAuth = analogRead(pulsePinExit);
  int presence = digitalRead(irEntryDetect);

  // LOGIC: Access Granted
  if ((entryAuth > pulseThreshold || exitAuth > pulseThreshold) && presence == HIGH) {
    digitalWrite(ledYellow, LOW);
    digitalWrite(ledGreen, HIGH);
    client.publish("vault/user", "Authorized Access");
    client.publish("vault/auth", "UNLOCKED");
    
    delay(10000); // Wait 10s
    
    digitalWrite(ledGreen, LOW);
    digitalWrite(ledYellow, HIGH);
    client.publish("vault/auth", "LOCKED");
    client.publish("vault/user", "None");
  }
  delay(100);
}