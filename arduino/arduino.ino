#include "SoftwareSerial.h"

#include <EEPROM.h>

/* ******************** PIN DEFINITIONS ******************** */
SoftwareSerial Bluetooth(2, 3); // RX | TX 
const int buzzerPin = 7;
const int triggerPin = 9;
const int echoPin = 10;

/* ******************** BLUETOOTH ******************** */
const int terminus = 10;

String command;
char data[64];
int idx = 0;

/* ******************** BUZZER ******************** */

// A4, B4, C4, D4, E4, F4, G4
const int notes[] = {440, 494, 523, 587, 659, 698, 784};

/* ******************** ULTRASONIC ******************** */

int CALIBRATION_ADDRESS = 0;
const long masks[] = {0xFF000000, 0xFF0000, 0xFF00, 0xFF};

long limit;

const int BUFFER_SIZE = 4;
long readings[BUFFER_SIZE];
int rIdx;
long sum;
long avg;

int distance;

/* ******************** NOTIFY ******************** */

unsigned long last = 0;
unsigned long block = 0;
const unsigned long delta = 900000;// Delay for 15 minutes (in milliseconds)

bool triggerFlag = false;
bool triggerFlagged = false;

// bool powerFlag;
// bool powerFlagged;

int flag = 0;

void setup() 
{   
  Serial.begin(9600); 
  Bluetooth.begin(9600); 
  pinMode(triggerPin, OUTPUT); // Sets the trigPin as an Output
  pinMode(echoPin, INPUT); // Sets the echoPin as an Input
  // pinMode(buzzerPin, OUTPUT);
  limit = read_long(CALIBRATION_ADDRESS);

  // Take some initial readings to populate ring buffer (for running average)
  for(int i = 0; i < BUFFER_SIZE; i++) ultrasonic_read();

  Serial.println("Ready to connect"); 
} 

void loop() 
{ 
 bluetooth_monitor();
 ultrasonic_monitor();

 notify();
}

// void(* reset) (void) = 0;

void bluetooth_monitor(){
  if (Bluetooth.available()) 
   flag = Bluetooth.read(); 

   if(flag != terminus && flag != 0){
    data[idx] = flag;
    idx++;

    if(idx >= 64) idx = 0;
    Serial.println(flag);
   } else if(idx > 0){
    data[idx - 1] = '\0';
    command = String(data);

    if(command == "x"){// Clear trigger
      triggerFlag = false;
      triggerFlagged = false;
      Serial.println("Trigger reset");
    }else if(command == "s"){// Snooze
      block = millis() + delta;
      triggerFlag = false;
      triggerFlagged = false;
      Serial.println("Trigger snoozed");
    }else if(command == "m"){// Get distance request
      Bluetooth.println(avg);
    }else if(command == "l"){// Get distance request
      Bluetooth.println(limit);
    }else if(command == "p"){// Get distance request
      if(triggerFlag){
        Bluetooth.println("T");
      }else{
        Bluetooth.println("g");
      }
    }else if(command == "c"){// Calibration request
      calibrate_ultrasonic();
      block = last;// Remove block when calibrating
      Bluetooth.println("C");
    }

    // Serial.println(command);
    command = "";
    idx = 0;
   }
}

void notify(){
  if(triggerFlag && !triggerFlagged){
    // if(Bluetooth.availableForWrite()){
    //   Bluetooth.println("T");
    //   triggerFlagged = true;
    // }
      Bluetooth.println("T");
      triggerFlagged = true;
  }

  if(millis() - last > 30000){
    triggerFlagged = false;
    Serial.println("Notify reset");
    last = millis();
  }

 buzzer_notify();
}

void buzzer_notify(){
  if(triggerFlag){
    tone(buzzerPin, notes[2]);
  }else{
    noTone(buzzerPin);
  }
}

void ultrasonic_monitor(){
  ultrasonic_read();

  avg = sum / BUFFER_SIZE;
  triggerFlag = triggerFlag || (last >= block && (avg < limit));
  if(triggerFlag && !triggerFlagged){
    Serial.print("Ultrasonic triggered: ");
    Serial.print(avg);
    Serial.print(" / ");
    Serial.println(limit);
  }
}

void ultrasonic_read(){
  digitalWrite(triggerPin, LOW);
  delayMicroseconds(2);

  digitalWrite(triggerPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(triggerPin, LOW);

  sum = sum - readings[rIdx];
  readings[rIdx] = pulseIn(echoPin, HIGH);
  sum = sum + readings[rIdx]; 

  rIdx = rIdx + 1; 
  if(rIdx >= BUFFER_SIZE) rIdx = 0;
}

void calibrate_ultrasonic(){
  Serial.print("Calibrating Ultrasonic... \nNew limit: ");

  limit = avg * 8 / 10;// Set limit for 80% of current running average
  write_long(CALIBRATION_ADDRESS, limit);
  Serial.println(limit);

  triggerFlag = false;
  triggerFlagged = false;
}

void write_long(int address, long data){
  for(int i = 0; i < 4; i++){
    uint8_t seg = (uint8_t)((data & masks[i]) >> (8 * (3 - i)));
    EEPROM.update(address + i, seg);
  }
}

long read_long(int address){
  long data = 0;
    for(int i = 0; i < 4; i++){
      data = data + ((long)EEPROM.read(address + i) << (8 * (3 - i)));
  }

  return data;
}