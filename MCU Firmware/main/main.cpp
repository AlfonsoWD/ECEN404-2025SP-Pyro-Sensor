//Team member: Oscar Hernandez
//Subsystem: MCU Firmware

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
#include "esp_timer.h"
#include <time.h>
#include <sys/time.h>
#include "esp_attr.h"
#include "esp_sleep.h"
#include "esp_sntp.h"
#include "lwip/err.h"
#include "lwip/sys.h"

#define API_KEY "AIzaSyCTkErKuaRfsmr3F_fxTcb0OykQ_6rwzCE" //Pyro Sensor project API Key | Needed to create database object
#define DATABASE_URL "https://pyro-sensor-default-rtdb.firebaseio.com/"  //Pyro Sensor Realtime database link | Needed to create database object
#define Potential_Fire_Time 15000000 //Time (in us) that should be waited to confirm a potential fire 

volatile int64_t device_start_time = 0;
volatile int64_t wifi_connected_time = 0;
volatile int64_t alarm_trigger_end = 0;
volatile int64_t alarm_trigger_start = 0;

char ROOM_NAME[ROOM_NAME_LENGTH];
volatile bool successful_initial_connection = false;
char Current_Date_Time[100];
static const char *TAG_time = "wifi station";

using namespace ESPFirebase;
/*
Gas Sensor
***************************************************************
*/
#define GAS_QUEUE_LENGTH 1
#define GAS_QUEUE_ITEM_SIZE sizeof(GasSensorData)

QueueHandle_t gas_sensor_queue = NULL;
//****************************************************************** */

/*
IR Sensor
***************************************************************
*/

#define IR_QUEUE_LENGTH 1
#define IR_QUEUE_ITEM_SIZE sizeof(IRSensorData)

QueueHandle_t ir_sensor_queue = NULL;
//****************************************************************** */

/*
UV Sensor
***************************************************************
*/
#define UV_QUEUE_LENGTH 1
#define UV_QUEUE_ITEM_SIZE sizeof(UVSensorData)

QueueHandle_t uv_sensor_queue = NULL;
//****************************************************************** */

/*
Backup Battery 
***************************************************************
*/
#define BATTERY_QUEUE_LENGTH 1
#define BATTERY_QUEUE_ITEM_SIZE sizeof(BatteryData)

QueueHandle_t battery_queue = NULL;
//****************************************************************** */

/*
Smoke Sensor 
***************************************************************
*/
#define SMOKE_QUEUE_LENGTH 1
#define SMOKE_QUEUE_ITEM_SIZE sizeof(SmokeSensorData)

QueueHandle_t smoke_sensor_queue = NULL;
//****************************************************************** */

volatile bool ADC1_Reset_Request = false;
volatile bool Smoke_Reset_Request = false;
volatile bool device_disconnected = false;
volatile bool Delete_Tasks = false;

/**
 * @brief Callback function triggered when a time synchronization event occurs.
 * 
 * @param tv Pointer to timeval structure with the synchronized time.
 */
void time_sync_notification_cb(struct timeval *tv)
{
    ESP_LOGI(TAG_time, "Notification of a time synchronization event");
}

/**
 * @brief Initializes SNTP (Simple Network Time Protocol) to synchronize system time with an NTP server.
 */
static void initialize_sntp(void)
{
    ESP_LOGI(TAG_time, "Initializing SNTP");
    sntp_setoperatingmode(SNTP_OPMODE_POLL);
    sntp_setservername(0, "pool.ntp.org");
    sntp_set_time_sync_notification_cb(time_sync_notification_cb);
#ifdef CONFIG_SNTP_TIME_SYNC_METHOD_SMOOTH
    sntp_set_sync_mode(SNTP_SYNC_MODE_SMOOTH);
#endif
    sntp_init();
}

/**
 * @brief Obtains and waits for system time synchronization from an NTP server.
 */
static void obtain_time(void)
{
    initialize_sntp();
    // wait for time to be set
    time_t now = 0;
    struct tm timeinfo = { 0 };
    int retry = 0;
    const int retry_count = 10;
    while (sntp_get_sync_status() == SNTP_SYNC_STATUS_RESET && ++retry < retry_count) {
        ESP_LOGI(TAG_time, "Waiting for system time to be set... (%d/%d)", retry, retry_count);
        vTaskDelay(2000 / portTICK_PERIOD_MS);
    }
    time(&now);
    localtime_r(&now, &timeinfo);
}

/**
 * @brief Sets the system time via SNTP if not already set.
 */
void Set_SystemTime_SNTP()  {
    time_t now;
       struct tm timeinfo;
       time(&now);
       localtime_r(&now, &timeinfo);
       // Is time set? If not, tm_year will be (1970 - 1900).
       if (timeinfo.tm_year < (2016 - 1900)) {
           ESP_LOGI(TAG_time, "Time is not set yet. Connecting to WiFi and getting time over NTP.");
           obtain_time();
           // update 'now' variable with current time
           time(&now);
       }
}

/**
 * @brief Returns the current date and time as a string in Central Time (adjusts for DST).
 * 
 * @return std::string The current formatted date/time.
 */
std::string Get_current_date_time() {
    char strftime_buf[64];
    time_t now;
    struct tm timeinfo;
    time(&now);
    localtime_r(&now, &timeinfo);

    // Set timezone to Central Daylight Time (CDT, UTC-5)
    setenv("TZ", "CST6CDT", 1); // CST6CDT is the standard time zone for Central Time in the U.S. (it adjusts for daylight saving time automatically)
    tzset();
    localtime_r(&now, &timeinfo);

    strftime(strftime_buf, sizeof(strftime_buf), "%c", &timeinfo);
    ESP_LOGI(TAG_time, "The current date/time in CDT is: %s", strftime_buf);

    return std::string(strftime_buf);  // Return a string instead of copying into char array
}

/**
 * @brief Sends data to Firebase Realtime Database at the specified path.
 * 
 * @param db Reference to the Firebase RTDB object.
 * @param path Target path in the database.
 * @param data JSON object to send.
 */
void sendDataToFirebase(ESPFirebase::RTDB& db, const std::string& path, const Json::Value& data) {
    // Convert the Json::Value to a string
    Json::FastWriter writer;
    std::string json_str = writer.write(data);

    // Send the data to Firebase at the specified path
    db.putData(path.c_str(), json_str.c_str());  // Convert both path and json_str to const char*
    ESP_LOGI("Firebase", "Data sent to Firebase: %s", json_str.c_str());
}

/**
 * @brief Updates (patches) data at a specified Firebase path.
 * 
 * @param db Reference to the Firebase RTDB object.
 * @param path Target path in the database.
 * @param data JSON object with fields to update.
 */
void updateDataToFirebase(ESPFirebase::RTDB& db, const std::string& path, const Json::Value& data) {
    // Convert the Json::Value to a string
    Json::FastWriter writer;
    std::string json_str = writer.write(data);

    // Send the data to Firebase at the specified path
    db.patchData(path.c_str(), json_str.c_str());  // Convert both path and json_str to const char*
    ESP_LOGI("Firebase", "Data sent to Firebase: %s", json_str.c_str());
}

/**
 * @brief Reads JSON data from Firebase at a given path.
 * 
 * @param db Reference to the Firebase RTDB object.
 * @param path Target path in the database.
 * @return Json::Value Retrieved JSON data, or an empty object if failed.
 */
 Json::Value readDataFromFirebase(ESPFirebase::RTDB& db, const std::string& path) {
    // Call getData() to fetch data from Firebase
    Json::Value data = db.getData(path.c_str());

    // Check if the data retrievalf was successful
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

/**
 * @brief Checks if a reset command has been issued via Firebase. Resets peripherals if commanded.
 * 
 * @param DB_object Firebase RTDB object.
 * @param String_path Firebase path to the reset command.
 * @return true If reset was commanded and acknowledged.
 * @return false If no reset was commanded or an error occurred.
 */
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
                DB_object.patchData((String_path + "/Reset").c_str(), UserReset.toStyledString().c_str());
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

/**
 * @brief Checks if a delete command has been issued via Firebase. Deletes device entry if commanded.
 * 
 * @param DB_object Firebase RTDB object.
 * @param String_path Firebase path to the delete command.
 * @return true If deletion was commanded and performed.
 * @return false If no deletion was commanded or an error occurred.
 */
bool DeleteDeviceFunction(ESPFirebase::RTDB DB_object, std::string String_path) {
    bool ExternalDelete = false;
    Json::Value UserDelete = readDataFromFirebase(DB_object, String_path + "/Delete");
    if (!UserDelete.isNull()) {
        if (UserDelete.isObject()) {
            std::string DeleteDevice = UserDelete.get("Delete_Sensor", "No").asString();
            //Handle the alarm status based on the value
            if (DeleteDevice == "Yes") {
                ESP_LOGI("Firebase", "Deleting device.");
                DB_object.deleteData(String_path.c_str());//Remove device node from Firebase
                ExternalDelete = true;                     
            }
            else if (DeleteDevice == "No") {
                ESP_LOGI("Firebase", "Delete not commanded.");
                ExternalDelete = false;
            }
        }
        else {
            ESP_LOGE("Firebase", "Data retrieved is not an object.");
        }
    }
    else {
        ESP_LOGE("Firebase", "No valid data received from Firebase.");
    }
    return ExternalDelete;
}

/**
 * @brief Main application entry point. Initializes Wi-Fi via BLE, connects to Firebase,
 * sets default values in the database, and creates queues for sensor data.
 */
extern "C" void app_main(void) {
    esp_log_level_set("*", ESP_LOG_NONE); // Disable all ESP loggers to suppress output

    bool ExternalReset = false;
    bool ExternalDelete = false;
    successful_initial_connection = false;

    printf("Requesting Wi-Fi credentials via Bluetooth...\n");

    // Connect to Wi-Fi through BLE and retrieve user ID
    char* user_id = Connect_To_WIFI();

    printf("Successfully connected to Wi-Fi!\n");
    printf("Received User ID: %s\n", user_id);
    successful_initial_connection = true;

    // Retrieve ESP32 MAC address
    unsigned char mac_base[6] = {0};
    esp_efuse_mac_get_default(mac_base);
    esp_read_mac(mac_base, ESP_MAC_WIFI_STA);

    // Format MAC address as a readable string
    char mcu_name[18];
    snprintf(mcu_name, sizeof(mcu_name), "%02X:%02X:%02X:%02X:%02X:%02X", 
             mac_base[0], mac_base[1], mac_base[2], mac_base[3], mac_base[4], mac_base[5]);

    ESP_LOGI("app_main", "ESP32 MAC address: %s", mcu_name);

    // Initialize Firebase app and database
    FirebaseApp app = FirebaseApp(API_KEY);
    RTDB db = RTDB(&app, DATABASE_URL);

    // Create the Firebase path based on user ID and MCU MAC
    std::string firebase_path = "/users/" + std::string(user_id) + "/sensors" + "/" + mcu_name;
    printf("Firebase path: %s\n", firebase_path.c_str());

    // Prepare initial Firebase JSON objects
    Json::Value UserReset;
    Json::Value alarm_json;
    Json::Value Initial_Device_Name;
    Json::Value BatteryJson;
    Json::Value DeleteDevice;
    char warning_message[400] = "";

    // Set initial device state in Firebase
    DeleteDevice["Delete_Sensor"] = "No";
    sendDataToFirebase(db, firebase_path + "/Delete", DeleteDevice);

    wifi_connected_time = esp_timer_get_time();
    printf("Device setup time: %lld us\n", (wifi_connected_time - device_start_time));

    vTaskDelay(pdMS_TO_TICKS(100));

    UserReset["Reset_Peripherals"] = "No";
    sendDataToFirebase(db, firebase_path + "/Reset", UserReset);

    vTaskDelay(pdMS_TO_TICKS(100));

    alarm_json["alarm_status"] = "Safe";
    alarm_json["message"] = "Alarm is off";
    sendDataToFirebase(db, firebase_path + "/Alarm", alarm_json);

    vTaskDelay(pdMS_TO_TICKS(100));

    Initial_Device_Name["Sensor"] = ROOM_NAME;
    printf("room name = %s\r\n", ROOM_NAME);
    sendDataToFirebase(db, firebase_path + "/Name", Initial_Device_Name);

    vTaskDelay(pdMS_TO_TICKS(100));

    BatteryJson["message"] = "Battery is in good state.";
    sendDataToFirebase(db, firebase_path + "/Battery", BatteryJson);

    vTaskDelay(pdMS_TO_TICKS(100));

    alarm_json["alarmTime"] = "";
    sendDataToFirebase(db, firebase_path + "/Alarm", alarm_json);

    // Create FreeRTOS queues for sensor data
    gas_sensor_queue = xQueueCreate(GAS_QUEUE_LENGTH, GAS_QUEUE_ITEM_SIZE);
    ir_sensor_queue = xQueueCreate(IR_QUEUE_LENGTH, IR_QUEUE_ITEM_SIZE);
    uv_sensor_queue = xQueueCreate(UV_QUEUE_LENGTH, UV_QUEUE_ITEM_SIZE);
    battery_queue = xQueueCreate(BATTERY_QUEUE_LENGTH, BATTERY_QUEUE_ITEM_SIZE);
    smoke_sensor_queue = xQueueCreate(SMOKE_QUEUE_LENGTH, SMOKE_QUEUE_ITEM_SIZE);

    // Check if queues were created successfully
    if (!gas_sensor_queue || !ir_sensor_queue || !uv_sensor_queue || !battery_queue || !smoke_sensor_queue) {
        ESP_LOGE("app_main", "Queue creation failed!");
        return;
    }

    // Initialize sensor data structs
    GasSensorData gas_received_data;
    IRSensorData ir_received_data;
    UVSensorData uv_received_data;
    BatteryData battery_data;
    SmokeSensorData smoke_received_data;

    // Start tasks for ADC readings and smoke sensor handling
    xTaskCreate(run_adc1, "ADC1 \r\n", 8192, NULL, 5, NULL);
    xTaskCreate(smoke_task, "Smoke Sensor", 4096, NULL, 5, NULL);
    xTaskCreate(run_adc2, "ADC2 \r\n", 8192, NULL, 4, NULL);

    // Setup PWM (for alarms, etc.)
    setup_pwm();

    bool alarmOn = false;
    bool battery_was_good = true; // Track previous battery state
    bool battery_replaced = false;

    // Initialize sensor flags
    gas_received_data.sensor_triggered = false;
    gas_received_data.gas_warning = false;

    ir_received_data.sensor_confirmed = false;
    ir_received_data.ir_warning = false;

    uv_received_data.sensor_confirmed = false;
    uv_received_data.uv_warning = false;

    smoke_received_data.sensor_warning = false;
    smoke_received_data.sensor_confirmed = false;
    smoke_received_data.sensor_confirmed_extreme = false;

    battery_data.battery_good = true;

    bool fireDetected = false;
    Set_SystemTime_SNTP(); // Sync system time via NTP

    uint8_t uv_score = 0;
    uint8_t ir_score = 0;
    uint8_t smoke_score = 0;
    uint8_t gas_score = 0;
    uint8_t Certainty = 0;

    bool confirmed_Certainty_value = false;
    int64_t Certainty_last_detection_time = 0;
    int64_t Potential_Fire_Duration = 0;

    // Main loop
    while (true) {
        Certainty = 0;
        battery_replaced = false;
        fireDetected = false;
        memset(warning_message, 0, sizeof(warning_message));

        // Check if battery message received
        if (xQueueReceive(battery_queue, &battery_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "Battery Received message: %s", battery_data.message);
            BatteryJson["message"] = battery_data.message;

            int64_t firebase_push_start = esp_timer_get_time();
            updateDataToFirebase(db, firebase_path + "/Battery", BatteryJson);
            int64_t firebase_push_end = esp_timer_get_time();
        }

        // Check if battery was replaced
        if (battery_data.battery_good && !battery_was_good) {
            disable_alarm();
            ADC1_Reset_Request = true;
            Smoke_Reset_Request = true;
            battery_replaced = true;
            vTaskDelay(pdMS_TO_TICKS(1000));
        }
        battery_was_good = battery_data.battery_good;

        // Read gas sensor queue
        if (xQueueReceive(gas_sensor_queue, &gas_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "Gas Received message: %s", gas_received_data.message);
            Json::Value gas_json;
            gas_json["gas_active"] = gas_received_data.sensor_triggered;
            gas_json["message"] = gas_received_data.message;
        }

        // Read IR sensor queue
        if (xQueueReceive(ir_sensor_queue, &ir_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "IR Received message: %s", ir_received_data.message);
            Json::Value ir_json;
            ir_json["ir_active"] = ir_received_data.sensor_confirmed;
            ir_json["message"] = ir_received_data.message;
        }

        // Read UV sensor queue
        if (xQueueReceive(uv_sensor_queue, &uv_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "UV Received message: %s", uv_received_data.message);
            Json::Value uv_json;
            uv_json["uv_active"] = uv_received_data.sensor_confirmed;
            uv_json["message"] = uv_received_data.message;
        }

        // Read smoke sensor queue
        if (xQueueReceive(smoke_sensor_queue, &smoke_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "Smoke Received message: %s", smoke_received_data.message);
            Json::Value smoke_json;
            smoke_json["smoke_warning"] = smoke_received_data.sensor_warning;
            smoke_json["smoke_confirmed"] = smoke_received_data.sensor_confirmed;
            smoke_json["message"] = smoke_received_data.message;
        }

        // --------------------------------------------------------------------------------------------------
        // Process sensor scores
        uv_score = uv_received_data.sensor_confirmed ? 25 : 0;
        ir_score = ir_received_data.sensor_confirmed ? 25 : 0;
        smoke_score = smoke_received_data.sensor_confirmed ? 25 : 0;
        gas_score = (gas_received_data.sensor_in_scope && gas_received_data.sensor_triggered) ? 25 : 0;
        Certainty = uv_score + ir_score + smoke_score + gas_score;

        // Immediate fire detected (high certainty)
        if (Certainty >= 75) {
            if (!alarmOn) {
                fireDetected = true;
                alarmOn = true;

                alarm_trigger_start = esp_timer_get_time();
                trigger_alarm();

                alarm_json["alarm_status"] = "Alarm";
                alarm_json["message"] = "Alarm is ON - Fire detected!";
                alarm_json["alarmTime"] = Get_current_date_time();
                updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);
            }
        }

        // Possible smoldering fire (medium certainty)
        else if ((Certainty >= 50) && (Certainty < 75)) {
            if (confirmed_Certainty_value == false) {
                Certainty_last_detection_time = esp_timer_get_time();
                confirmed_Certainty_value = true;
            } else {
                Potential_Fire_Duration = (esp_timer_get_time() - Certainty_last_detection_time);
                if (Potential_Fire_Duration >= Potential_Fire_Time) {
                    if (!alarmOn) {
                        fireDetected = true;
                        alarmOn = true;
                        trigger_alarm();
                        alarm_json["alarm_status"] = "Alarm";
                        alarm_json["message"] = "Alarm is ON - Fire detected!";
                        alarm_json["alarmTime"] = Get_current_date_time();
                        updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);
                    }
                }
            }
        }
           
            // Suspicious environment (low certainty)
           else if ((Certainty > 0) && (Certainty < 50)) {//Suspicious environment
            if (alarmOn) {
                alarmOn = false;
                disable_alarm(); // Disable buzzer if previously activated
            }
            
            // Build string of which sensors are active
            std::string confirmed_sensors = "Confirmed Sensors: ";
            
            if (uv_received_data.sensor_confirmed) {
                confirmed_sensors += "UV ";
            }
            if (ir_received_data.sensor_confirmed) {
                confirmed_sensors += "IR ";
            }
            if (smoke_received_data.sensor_confirmed) {
                confirmed_sensors += "Smoke ";
            }
            if (gas_received_data.sensor_in_scope && gas_received_data.sensor_triggered) {
                confirmed_sensors += "Gas ";
            }
            
            ESP_LOGI("app_main", "Confirmed sensors: %s", confirmed_sensors.c_str());
           }

           else if (Certainty == 0) {//WARNING STATE
            confirmed_Certainty_value = false;
            Certainty_last_detection_time = 0;
            if (alarmOn) {
                alarmOn = false;
                disable_alarm(); // optional safety
            }
               
               //WARNING STATES: 
               //gas_received_data.gas_warning: detected gas outside of rated range (i.e., outside of 200-1000 ppm), more likely hardware issue
               //ir_received_data.ir_warning: Infrared value exceeded calibrated threshold
               //uv_received_data.uv_warning: Ultraviolet value exceeded calibrated threshold
               //smoke_received_data.sensor_warning: Smoke value exceeded calibrated threshold for nuisance sources (e.g., candle smoke)
               //show Warming messages
           
               if ( (gas_received_data.gas_warning || ir_received_data.ir_warning ||
                   uv_received_data.uv_warning || smoke_received_data.sensor_warning) && (!battery_replaced) ) {
                      
                        memset(warning_message, 0, sizeof(warning_message));
                    
                        if (ir_received_data.ir_warning) {
                            strcat(warning_message, ir_received_data.message);
                            strcat(warning_message, " ");  // Add space after message
                        }
                        if (uv_received_data.uv_warning) {
                            strcat(warning_message, uv_received_data.message);
                            strcat(warning_message, " ");  // Add space after message
                        }
                        if (smoke_received_data.sensor_warning) {
                            strcat(warning_message, smoke_received_data.message);
                            strcat(warning_message, " ");  // Add space after message
                        }
                        if (gas_received_data.gas_warning) {
                            strcat(warning_message, gas_received_data.message);
                            strcat(warning_message, " ");  // Add space after message
                        }

                    
                        // Trim trailing space if present
                        size_t len = strlen(warning_message);
                        if (len > 0 && warning_message[len - 1] == ' ') {
                                    warning_message[len - 1] = '\0';  // Remove last space
                        }
                    
                        alarm_json["alarm_status"] = "Warning";
                        alarm_json["message"] = warning_message;
                        updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json); 
                        continue;
               }
           
               alarm_json["alarm_status"] = "Safe";
               alarm_json["message"] = "Alarm is OFF - No fire detected.";
               updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);

           } 

        //---------------------------------------------------------------------------------------------------------------------------------------------------------
            
        ExternalReset = ResetDeviceFunction(db, firebase_path);//recalibrate sensors as per User Request
        if (ExternalReset == true) {
            printf("Reset -----------------------------------------------\r\n");

            if (alarmOn) {
                alarmOn = false;
            }
            disable_alarm();

            ADC1_Reset_Request = true;
            Smoke_Reset_Request = true;
            
            alarm_json["alarm_status"] = "Safe";
            alarm_json["message"] = "Alarm is OFF - No fire detected.";
            updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);
        
            alarm_json["alarmTime"] = "";
            updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);       
        }

        vTaskDelay(pdMS_TO_TICKS(10)); // Delay before the next loop iteration

        ExternalDelete = DeleteDeviceFunction(db, firebase_path);
        if (ExternalDelete == true) {
            printf("Delete------------------------------------------------\r\n");
            disable_alarm();
            ESP_LOGI("app_main", "Deleted device");
            break;
        }

        vTaskDelay(pdMS_TO_TICKS(10)); // Delay before the next loop iteration
    }

    ESP_LOGI("app_main", "Resetting device");
    esp_restart();
}
