// Brisa: sensor de temperatura da casa do idoso (ESP32 + DHT22), simulado no Wokwi.
// Publica {"temperatura": 36.0} no tópico brisa/fiap561902/casa/<IDOSO_ID> do broker público da HiveMQ.
#include <WiFi.h>
#include <PubSubClient.h>
#include "DHTesp.h"

// Id da Dona Lurdes no painel (veja a URL /idosos/<id>). Troque depois de cada cadastro novo.
const char* IDOSO_ID = "1";

const char* BROKER = "broker.hivemq.com";
const int PINO_DHT = 15;

DHTesp dht;
WiFiClient wifi;
PubSubClient mqtt(wifi);
String topico;
float ultimaEnviada = -100;
unsigned long ultimoEnvio = 0;

void conectarMqtt() {
  while (!mqtt.connected()) {
    String id = "brisa-esp32-" + String(random(0xffff), HEX);
    if (mqtt.connect(id.c_str())) {
      Serial.println("MQTT conectado");
    } else {
      delay(1000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  dht.setup(PINO_DHT, DHTesp::DHT22);
  WiFi.begin("Wokwi-GUEST", "", 6);
  while (WiFi.status() != WL_CONNECTED) delay(100);
  Serial.println("Wi-Fi conectado");
  mqtt.setServer(BROKER, 1883);
  topico = String("brisa/fiap561902/casa/") + IDOSO_ID;
}

void loop() {
  conectarMqtt();
  mqtt.loop();
  float t = dht.getTemperature();
  // Só envia quando a temperatura muda 0,5 °C ou a cada 60 s, para não lotar a linha do tempo.
  if (!isnan(t) && (fabs(t - ultimaEnviada) >= 0.5 || millis() - ultimoEnvio > 60000)) {
    String payload = "{\"temperatura\": " + String(t, 1) + "}";
    mqtt.publish(topico.c_str(), payload.c_str());
    Serial.println(topico + " " + payload);
    ultimaEnviada = t;
    ultimoEnvio = millis();
  }
  delay(1000);
}
