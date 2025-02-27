//TO DO: IMPLEMENT SMOKE SENSOR TO MAIN (threshold value + warning)
//TO DO: SHOW SENSORS WARNINGS IN FIREBASE
//TO DO: IF NEEDED MANAGE BLE FOR STRINGS THAT ARE LONGER THAN 18 BYTES (LENGTH DEPENDING ON CLIENT)

#include <stdio.h>
#include <stdbool.h>
#include <iostream>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "string.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "jsoncpp/value.h"
#include "jsoncpp/json.h"
#include "esp_firebase/app.h"
#include "esp_firebase/rtdb.h"
#include "ADC2.h"
#include "ADC1.h"
#include "Smoke_Sensor.h"
#include "backup_battery_level.h"
#include "Speaker.h"
#include "WIFI_Connector.h"

#define API_KEY "AIzaSyCTkErKuaRfsmr3F_fxTcb0OykQ_6rwzCE" //Pyro Sensor project API Key | Needed to create database object

#define DATABASE_URL "https://pyro-sensor-default-rtdb.firebaseio.com/"  //Pyro Sensor Realtime database link | Needed to create database object

using namespace ESPFirebase;
/*
Gas Sensor
***************************************************************
*/
#define GAS_QUEUE_LENGTH 10
#define GAS_QUEUE_ITEM_SIZE sizeof(GasSensorData)

QueueHandle_t gas_sensor_queue = NULL;
//****************************************************************** */

/*
IR Sensor
***************************************************************
*/
#define IR_QUEUE_LENGTH 10
#define IR_QUEUE_ITEM_SIZE sizeof(IRSensorData)

QueueHandle_t ir_sensor_queue = NULL;
//****************************************************************** */

/*
UV Sensor
***************************************************************
*/
#define UV_QUEUE_LENGTH 10
#define UV_QUEUE_ITEM_SIZE sizeof(UVSensorData)

QueueHandle_t uv_sensor_queue = NULL;
//****************************************************************** */

/*
Backup Battery 
***************************************************************
*/
#define BATTERY_QUEUE_LENGTH 10
#define BATTERY_QUEUE_ITEM_SIZE sizeof(BatteryData)

QueueHandle_t battery_queue = NULL;
//****************************************************************** */


void sendDataToFirebase(ESPFirebase::RTDB& db, const std::string& path, const Json::Value& data) {
    // Convert the Json::Value to a string
    Json::FastWriter writer;
    std::string json_str = writer.write(data);

    // Send the data to Firebase at the specified path
    db.putData(path.c_str(), json_str.c_str());  // Convert both path and json_str to const char*
    ESP_LOGI("Firebase", "Data sent to Firebase: %s", json_str.c_str());
}

 Json::Value readDataFromFirebase(ESPFirebase::RTDB& db, const std::string& path) {
    // Call getData() to fetch data from Firebase
    Json::Value data = db.getData(path.c_str());

    // Check if the data retrieval was successful
    if (!data.isNull()) {

        ESP_LOGI("Firebase", "Data received successfully from path: %s", path.c_str());
        
        return data; // Returning the reference to the received data
    } else {
        ESP_LOGE("Firebase", "Failed to retrieve data from Firebase at path: %s", path.c_str());

        // Return a default empty object in case of failure
        static Json::Value empty_data; 

        return empty_data;  // Returning reference to a static empty Json::Value
    }
}

bool ResetDeviceFunction(ESPFirebase::RTDB DB_object, std::string String_path) {
    bool ExternalReset = false;
    Json::Value UserReset = readDataFromFirebase(DB_object, String_path + "/Reset");
    if (!UserReset.isNull()) {
        if (UserReset.isObject()) {
            std::string ResetDevice = UserReset.get("Reset_Peripherals", "No").asString();
            //Handle the alarm status based on the value
            if (ResetDevice == "Yes") {
                ESP_LOGI("Firebase", "Resetting device.");
                UserReset["Reset_Peripherals"] = "No";
                sendDataToFirebase(DB_object, String_path + "/Reset", UserReset);
                ExternalReset = true;                     
            }
            else if (ResetDevice == "No") {
                ESP_LOGI("Firebase", "Reset not commanded.");
                ExternalReset = false;
            }
        }
        else {
            ESP_LOGE("Firebase", "Data retrieved is not an object.");
        }
    }
    else {
        ESP_LOGE("Firebase", "No valid data received from Firebase.");
    }
    return ExternalReset;
}

extern "C" void app_main(void) { 
    //initial Wifi Connect attempt
    bool ExternalReset = false;
    
    printf("Requesting Wi-Fi credentials via Bluetooth...\n");

    // Call the function to receive Wi-Fi credentials and connect
    char* user_id = Connect_To_WIFI();

    // Once connected, user_id contains the received User ID
    printf("Successfully connected to Wi-Fi!\n");
    printf("Received User ID: %s\n", user_id);

    // Get the MAC address of the Wi-Fi station interface (ESP32 MAC address)
    unsigned char mac_base[6] = {0};
    esp_efuse_mac_get_default(mac_base);
    esp_read_mac(mac_base, ESP_MAC_WIFI_STA);

    // Create a unique MCU name from the MAC address
    char mcu_name[18];  // MAC address will be in format "XX:XX:XX:XX:XX:XX"
    snprintf(mcu_name, sizeof(mcu_name), "%02X:%02X:%02X:%02X:%02X:%02X", 
             mac_base[0], mac_base[1], mac_base[2], mac_base[3], mac_base[4], mac_base[5]);

    ESP_LOGI("app_main", "ESP32 MAC address: %s", mcu_name);  // Log the MAC address

    // Config Firebase
    FirebaseApp app = FirebaseApp(API_KEY);
    RTDB db = RTDB(&app, DATABASE_URL); // Initialize the RTDB instance    
/*
------------------------------------------------------------------------------------------------------------------------------
declare variables
------------------------------------------------------------------------------------------------------------------------------
*/
    gas_sensor_queue = xQueueCreate(GAS_QUEUE_LENGTH, GAS_QUEUE_ITEM_SIZE);
    ir_sensor_queue = xQueueCreate(IR_QUEUE_LENGTH, IR_QUEUE_ITEM_SIZE);
    uv_sensor_queue = xQueueCreate(UV_QUEUE_LENGTH, UV_QUEUE_ITEM_SIZE);
    battery_queue = xQueueCreate(BATTERY_QUEUE_LENGTH, BATTERY_QUEUE_ITEM_SIZE);

    if (!gas_sensor_queue || !ir_sensor_queue || !uv_sensor_queue || !battery_queue) {
        ESP_LOGE("app_main", "Queue creation failed!");
        return;
    }

    GasSensorData gas_received_data;
    IRSensorData ir_received_data;
    UVSensorData uv_received_data;
    BatteryData battery_data;

    std::string firebase_path = "/users/" + std::string(user_id) + "/sensors" + "/" + mcu_name;
    printf("Firebase path: %s\n", firebase_path.c_str());

   xTaskCreate(TESTING_adc1,"ADC1 \r\n",8192,NULL, 5, NULL);
   xTaskCreate(run_adc2,"ADC2 \r\n",8192,NULL, 4, NULL);

   setup_pwm();
   Json::Value UserReset;
   Json::Value alarm_json;
   
/*
------------------------------------------------------------------------------------------------------------------------------
*/
        bool alarmOn = false;
        soundSpeaker(alarmOn);

        UserReset["Reset_Peripherals"] = "No";
        sendDataToFirebase(db, firebase_path + "/Reset", UserReset);       
    
        alarm_json["alarm_status"] = "deactivated";
        alarm_json["message"] = "Alarm is off";        
        sendDataToFirebase(db, firebase_path + "/Alarm", alarm_json); 
    
        gas_received_data.sensor_triggered=false;
        ir_received_data.sensor_confirmed=false;
        uv_received_data.sensor_confirmed=false;    

        printf("STUCK1\r\n");

        //Sensor loop/STANDBY MODE
        while (true) {
            /*
            ---------------------------------------------------------------------------------------------------------
            //Step 1: Acquire data from all sensors
            ------------------------------------------------------------------------------------------------------------
            */

            if (xQueueReceive(gas_sensor_queue, &gas_received_data, (TickType_t)5) == pdTRUE) {
                ESP_LOGI("app_main", "Gas Received message: %s", gas_received_data.message);
            }
        // Receive and process queue from IR sensor
            if (xQueueReceive(ir_sensor_queue, &ir_received_data, (TickType_t)5) == pdTRUE) {
                ESP_LOGI("app main", "IR Received message: %s", ir_received_data.message);
            }
            // Receive and process queue from UV sensor
            if (xQueueReceive(uv_sensor_queue, &uv_received_data, (TickType_t)5) == pdTRUE) {
                ESP_LOGI("app main", "UV Received message: %s", uv_received_data.message);
            }
            // Receive and process queue from Battery
            if (xQueueReceive(battery_queue, &battery_data, (TickType_t)5) == pdTRUE) {
                ESP_LOGI("app main", "Battery in good condition: %s", battery_data.battery_good ? "Yes" : "No");

                // Create JSON object for Battery data
                Json::Value battery_json;
                battery_json["battery_good"] = battery_data.battery_good;
                battery_json["message"] = battery_data.message;

                // Send to Firebase
                sendDataToFirebase(db, firebase_path + "/Battery", battery_json);       
            }        
            // Receive and process queue from Smoke sensor

            /*
            -------------------------------------------------------------------------------------------------------
            // Step 2: Check IR sensor
            ----------------------------------------------------------------------------------------------------------------
            */
            if (ir_received_data.sensor_confirmed == true) {
                printf("STUCK3\r\n");

            /*
            -------------------------------------------------------------------------------------------------------
            // Step 3: Check UV sensor
            ----------------------------------------------------------------------------------------------------------------
            */
                if (uv_received_data.sensor_confirmed == true) {
                    printf("STUCK4\r\n");

            /*
            -------------------------------------------------------------------------------------------------------
            // Step 3: Check Gas/Smoke Sensor
            ----------------------------------------------------------------------------------------------------------------
            */

                    if (((gas_received_data.sensor_triggered == true) && (gas_received_data.sensor_in_scope == true))) {
                        // Set alarm status and message for Firebase when it is triggered
                        alarm_json["alarm_status"] = "triggered";
                        alarm_json["message"] = "Fire detected! Alarm activated";
                        sendDataToFirebase(db, firebase_path + "/Alarm", alarm_json);  

                        printf("STUCK5\r\n");

                    /*
                    -------------------------------------------------------------------------------------------------------
                    // Step 3.1: Alarm on until all sensors deactivate
                    ----------------------------------------------------------------------------------------------------------------
                    */
                        printf("Alarm remains ON - sensors active.\n"); 
                        alarmOn = true; //turn on Buzzer
                        soundSpeaker(alarmOn);                 
                        while ((gas_received_data.sensor_triggered == true) ||      //ALARM MODE
                                (ir_received_data.sensor_confirmed == true) ||
                                (uv_received_data.sensor_confirmed == true)                           
                            )
                        {
                            // Receive and process queue from gas sensor
                            if (xQueueReceive(gas_sensor_queue, &gas_received_data, (TickType_t)5) == pdTRUE) {
                                ESP_LOGI("app_main", "Gas Received message: %s", gas_received_data.message);
                            }
                            // Receive and process queue from IR sensor
                            if (xQueueReceive(ir_sensor_queue, &ir_received_data, (TickType_t)5) == pdTRUE) {
                                ESP_LOGI("app main", "IR Received message: %s", ir_received_data.message);             
                            }
                            // Receive and process queue from UV sensor
                            if (xQueueReceive(uv_sensor_queue, &uv_received_data, (TickType_t)5) == pdTRUE) {
                                ESP_LOGI("app main", "UV Received message: %s", uv_received_data.message);                   
                            }
                            // Receive and process queue from Smoke sensor

                       
                            //if ExternalReset triggered here, then go back to start of sensor loop
                            ExternalReset = ResetDeviceFunction(db, firebase_path);
                            if (ExternalReset == true) {
                                ExternalReset = false;
                                break;                                
                            }
                        }                                           
                        //recalibrate after fire is out
                        printf("Alarm remains Off - all sensors not detecting fire.\n");
                        alarmOn = false;
                        soundSpeaker(alarmOn);
                        printf("STUCK7\r\n");

                    }
                }
            }      
            ExternalReset = ResetDeviceFunction(db, firebase_path);
            if (ExternalReset == true) {
                ExternalReset = false;
                continue;                                
            }
        }//sensor loop/standy mode
}