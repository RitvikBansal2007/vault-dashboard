#include <WiFi.h>
#include <FirebaseESP32.h>
#include <Adafruit_Fingerprint.h>

// WiFi & Firebase Config
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";
#define FIREBASE_HOST "bank-vault-8e868-default-rtdb.firebaseio.com"
#define FIREBASE_AUTH "XSYlD5KsejgJzorB68mpuiXAbh9s25bcbAa64A3c" 

// Hardware Serial for Fingerprint (RX2=16, TX2=17)
HardwareSerial mySerial(2);
Adafruit_Fingerprint finger = Adafruit_Fingerprint(&mySerial);

FirebaseData fbData;
FirebaseAuth auth;
FirebaseConfig config;

void setup() {
  Serial.begin(115200);
  
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) { delay(500); }

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  finger.begin(57600);
  if (finger.verifyPassword()) {
    Serial.println("Fingerprint sensor detected!");
  }
}

void loop() {
  int id = getFingerprintID();
  
  if (id > 0) {
    Serial.print("Authorized ID Found: "); Serial.println(id);
    
    // Dynamic Lookup: Fetch name from Vault/Authorized_Users/id
    String path = "Vault/Authorized_Users/" + String(id);
    String userName = "Unknown User";
    
    if (Firebase.getString(fbData, path)) {
      userName = fbData.stringData();
    }

    // Grant Access
    Firebase.setString(fbData, "Vault/User", userName);
    Firebase.setString(fbData, "Vault/Auth", "UNLOCKED");
    
    Serial.println("Access Granted to: " + userName);

    // Stay unlocked for 10 seconds, then reset
    delay(10000); 
    Firebase.setString(fbData, "Vault/Auth", "LOCKED");
    Firebase.setString(fbData, "Vault/User", "None");
  }
  delay(500);
}

int getFingerprintID() {
  uint8_t p = finger.getImage();
  if (p != FINGERPRINT_OK) return -1;
  p = finger.image2Tz();
  if (p != FINGERPRINT_OK) return -1;
  p = finger.fingerFastSearch();
  if (p != FINGERPRINT_OK) return -1;
  return finger.fingerID;
}