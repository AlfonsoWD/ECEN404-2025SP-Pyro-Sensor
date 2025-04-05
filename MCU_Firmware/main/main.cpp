
//TO DO: SOMETIMES ALARM SOUNDS WHEN UPLOADING CODE THE FIRST TIME
//TO DO: TRY TO DECREASE TIME DURING ADDING DEVICE PHASE THAT TAKES TO ADD NEW DEVICE
//TO DO: discuss with app about prompting messages of limited strings to users for uuids
//TO DO: FIX replacing battery from 6V to 9V and alarm going off
//TO DO: RUN TEST AND EDGE CASES on app TO CATCH ERROR AND CRASHES
//TO DO: FIX link up between replacing battery from 9V to 6Vand alarm status for gas triggering


//TO DO: MEASURE AH OF DEVICE, AND GET IN TERMS WITH WHAT POWER SUPPLY WILL BE USED
//possible solutions: put it into deep sleep and disconnect wifi as soon as low battery message is sent to Firebase, and then just continue to work (even alarm sound if detected) locally. That is, connect again once battery is replaced.
//BATTERY SHOULD LAST AT LEAST 6 MINUTES UNTIL FIREFIGHTER ARRIVES
//SCHEDULE FULL SCALE TESTING OF THE SYSTEM WITH REAL SOURCES
//power cable: https://www.amazon.com/18AWG-Pigtail-Wiring-Replacement-120VAC/dp/B0829QG69V/ref=sr_1_3?crid=OQDJPK2EMRZ4&dib=eyJ2IjoiMSJ9.J1C3sED6sCaYAXV5p9mT9PTEeGWqxyDeezMcAoJXv4H2xL7TnhyzAw7ji2xssPzppUp5WJugJXK4wZ9zOQx7gainI8-Bc1UvJS6tXYHVrug_Fz9BTxYXFlxi1stsmMUh0ZEguo_6900nYG6eSm1tSHQRZRNrJLZIDwXEgXHQKw0wsYHU_sJ7TZpWFnpq2EqmqK4VhrQU1AfIaRoFQQgp3MAIr-PdDYnUvpQ8cRrvP2NQ-UzZLH1HgC1Wegr61gBokPSCnIz4vivrnJUdGG8ZE5HGq3BVJKNoS5cu76Zyk8Y.5EDS_lzpcA7XCP8yCWvCZj40uBSjuOqv4LzUWMdmuLE&dib_tag=se&keywords=three+prong+open+ac+power+cord+cable&qid=1743653447&s=electronics&sprefix=three+prong+open+ac+power+cord+cable%2Celectronics%2C94&sr=1-3


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

#include <time.h>
#include <sys/time.h>
#include "esp_attr.h"
#include "esp_sleep.h"
#include "esp_sntp.h"

#include "lwip/err.h"
#include "lwip/sys.h"
 
#define API_KEY "AIzaSyCTkErKuaRfsmr3F_fxTcb0OykQ_6rwzCE" //Pyro Sensor project API Key | Needed to create database object
#define DATABASE_URL "https://pyro-sensor-default-rtdb.firebaseio.com/"  //Pyro Sensor Realtime database link | Needed to create database object

char ROOM_NAME[ROOM_NAME_LENGTH];
volatile bool successful_initial_connection = false;
char Current_Date_Time[100];
static const char *TAG_time = "wifi station";

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

/*
Smoke Sensor 
***************************************************************
*/
#define SMOKE_QUEUE_LENGTH 10
#define SMOKE_QUEUE_ITEM_SIZE sizeof(SmokeSensorData)

QueueHandle_t smoke_sensor_queue = NULL;
//****************************************************************** */

volatile bool ADC1_Reset_Request = false;
volatile bool Smoke_Reset_Request = false;
volatile bool device_disconnected = false;
volatile bool Delete_Tasks = false;

void time_sync_notification_cb(struct timeval *tv)
{
    ESP_LOGI(TAG_time, "Notification of a time synchronization event");
}

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

void sendDataToFirebase(ESPFirebase::RTDB& db, const std::string& path, const Json::Value& data) {
    // Convert the Json::Value to a string
    Json::FastWriter writer;
    std::string json_str = writer.write(data);

    // Send the data to Firebase at the specified path
    db.putData(path.c_str(), json_str.c_str());  // Convert both path and json_str to const char*
    ESP_LOGI("Firebase", "Data sent to Firebase: %s", json_str.c_str());
}

void updateDataToFirebase(ESPFirebase::RTDB& db, const std::string& path, const Json::Value& data) {
    // Convert the Json::Value to a string
    Json::FastWriter writer;
    std::string json_str = writer.write(data);

    // Send the data to Firebase at the specified path
    db.patchData(path.c_str(), json_str.c_str());  // Convert both path and json_str to const char*
    ESP_LOGI("Firebase", "Data sent to Firebase: %s", json_str.c_str());
}

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

//db.deleteData("/person3/subset2");

extern "C" void app_main(void) {
    //esp_log_level_set("*", ESP_LOG_NONE); // DISABLE ESP LOGGERS
    bool ExternalReset = false;
    bool ExternalDelete = false;
    successful_initial_connection = false;
    printf("Requesting Wi-Fi credentials via Bluetooth...\n");
    char*  user_id = Connect_To_WIFI();
    printf("Successfully connected to Wi-Fi!\n");
    printf("Received User ID: %s\n", user_id);
    successful_initial_connection = true;

    unsigned char mac_base[6] = {0};
    esp_efuse_mac_get_default(mac_base);
    esp_read_mac(mac_base, ESP_MAC_WIFI_STA);

    char mcu_name[18];
    snprintf(mcu_name, sizeof(mcu_name), "%02X:%02X:%02X:%02X:%02X:%02X", 
             mac_base[0], mac_base[1], mac_base[2], mac_base[3], mac_base[4], mac_base[5]);

    ESP_LOGI("app_main", "ESP32 MAC address: %s", mcu_name);

    FirebaseApp app = FirebaseApp(API_KEY);
    RTDB db = RTDB(&app, DATABASE_URL);

    std::string firebase_path = "/users/" + std::string(user_id) + "/sensors" + "/" + mcu_name;
    printf("Firebase path: %s\n", firebase_path.c_str());

    Json::Value UserReset;
    Json::Value alarm_json;
    Json::Value Initial_Device_Name;
    Json::Value BatteryJson;
    Json::Value DeleteDevice;
    char warning_message[400] = "";

    DeleteDevice["Delete_Sensor"] = "No";
    sendDataToFirebase(db, firebase_path + "/Delete", DeleteDevice);
    
    UserReset["Reset_Peripherals"] = "No";
    sendDataToFirebase(db, firebase_path + "/Reset", UserReset);

    alarm_json["alarm_status"] = "Safe";
    alarm_json["message"] = "Alarm is off";
    sendDataToFirebase(db, firebase_path + "/Alarm", alarm_json);

    Initial_Device_Name["Sensor"] = ROOM_NAME;
    printf("room name = %s\r\n",ROOM_NAME);
    sendDataToFirebase(db,firebase_path + "/Name", Initial_Device_Name);

    BatteryJson["message"] = "Battery is in good state.";
    sendDataToFirebase(db,firebase_path + "/Battery", BatteryJson);

    alarm_json["alarmTime"] = "";
    sendDataToFirebase(db, firebase_path + "/Alarm", alarm_json);

    gas_sensor_queue = xQueueCreate(GAS_QUEUE_LENGTH, GAS_QUEUE_ITEM_SIZE);
    ir_sensor_queue = xQueueCreate(IR_QUEUE_LENGTH, IR_QUEUE_ITEM_SIZE);
    uv_sensor_queue = xQueueCreate(UV_QUEUE_LENGTH, UV_QUEUE_ITEM_SIZE);
    battery_queue = xQueueCreate(BATTERY_QUEUE_LENGTH, BATTERY_QUEUE_ITEM_SIZE);
    smoke_sensor_queue = xQueueCreate(SMOKE_QUEUE_LENGTH, SMOKE_QUEUE_ITEM_SIZE);

    if (!gas_sensor_queue || !ir_sensor_queue || !uv_sensor_queue || !battery_queue) {
        ESP_LOGE("app_main", "Queue creation failed!");
        return;
    }

    GasSensorData gas_received_data;
    IRSensorData ir_received_data;
    UVSensorData uv_received_data;
    BatteryData battery_data;
    SmokeSensorData smoke_received_data;

    xTaskCreate(run_adc1, "ADC1 \r\n", 8192, NULL, 6, NULL);
    xTaskCreate(smoke_task, "Smoke Sensor", 4096, NULL, 5, NULL);
    xTaskCreate(run_adc2, "ADC2 \r\n", 8192, NULL, 4, NULL);

    setup_pwm();

    bool alarmOn = false;
    bool battery_was_good = true; //dictates if the battery was at 9V
    bool battery_replaced = false;

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
    Set_SystemTime_SNTP();

    // Initialize alarm status
    while (true) {

        battery_replaced = false;
        printf("Loop iteration \r\n");
        fireDetected = false;
        memset(warning_message, 0, sizeof(warning_message));

        if (xQueueReceive(battery_queue, &battery_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "Battery Received message: %s", battery_data.message);

            BatteryJson["message"] = battery_data.message;
            updateDataToFirebase(db,firebase_path + "/Battery", BatteryJson);
        }       

        if (battery_data.battery_good && !battery_was_good) {
            disable_alarm();
            //reset mq2 sensor
            ADC1_Reset_Request = true;
            Smoke_Reset_Request = true;
            battery_replaced = true;
            vTaskDelay(pdMS_TO_TICKS(1000));
        }
        battery_was_good = battery_data.battery_good;

        // Check Gas Sensor Data
        if (xQueueReceive(gas_sensor_queue, &gas_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "Gas Received message: %s", gas_received_data.message);
            Json::Value gas_json;
            gas_json["gas_active"] = gas_received_data.sensor_triggered;
            gas_json["message"] = gas_received_data.message;
            //updateDataToFirebase(db, firebase_path + "/Gas", gas_json);
        }
        
        // Check IR Sensor Data
        if (xQueueReceive(ir_sensor_queue, &ir_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "IR Received message: %s", ir_received_data.message);
            Json::Value ir_json;
            ir_json["ir_active"] = ir_received_data.sensor_confirmed;
            ir_json["message"] = ir_received_data.message;
            //updateDataToFirebase(db, firebase_path + "/IR", ir_json);
        }
        
        // Check UV Sensor Data
        if (xQueueReceive(uv_sensor_queue, &uv_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "UV Received message: %s", uv_received_data.message);
            Json::Value uv_json;
            uv_json["uv_active"] = uv_received_data.sensor_confirmed;
            uv_json["message"] = uv_received_data.message;
            //updateDataToFirebase(db, firebase_path + "/UV", uv_json);           
        }
        
        // Check Smoke Sensor Data
        if (xQueueReceive(smoke_sensor_queue, &smoke_received_data, (TickType_t)5) == pdTRUE) {
            ESP_LOGI("app_main", "Smoke Received message: %s", smoke_received_data.message);
            Json::Value smoke_json;
            smoke_json["smoke_warning"] = smoke_received_data.sensor_warning;
            smoke_json["smoke_confirmed"] = smoke_received_data.sensor_confirmed;
            smoke_json["message"] = smoke_received_data.message;
            //updateDataToFirebase(db, firebase_path + "/Smoke", smoke_json);
        }
 

        //ALARM STATES------------------------------------------------------------------------------------------------------------------------------------------------------
        if ( (ir_received_data.sensor_confirmed) || (uv_received_data.sensor_confirmed) ||
        (smoke_received_data.sensor_confirmed) || (gas_received_data.sensor_in_scope && gas_received_data.sensor_triggered)  ) {

            /*
                    if ((ir_received_data.sensor_confirmed || uv_received_data.sensor_confirmed) &&
        (smoke_received_data.sensor_confirmed || (gas_received_data.sensor_in_scope && gas_received_data.sensor_triggered))) {
            */

            
        // Alarm condition #1
        if (!alarmOn) {
            fireDetected = true;
            alarmOn = true;
            trigger_alarm();
            alarm_json["alarm_status"] = "Alarm";
            alarm_json["message"] = "Alarm is ON - Fire detected!";
            alarm_json["alarmTime"] = Get_current_date_time();
            updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);
        }
    
    } else if (smoke_received_data.sensor_confirmed_extreme) {
        // Alarm condition #2
        if (!alarmOn) {
            fireDetected = true;
            alarmOn = true;
            trigger_alarm();
            alarm_json["alarm_status"] = "Alarm";
            alarm_json["message"] = "Alarm is ON - Fire detected!";
            alarm_json["alarmTime"] = Get_current_date_time();

            updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);
        }
    
    } else if ( (gas_received_data.gas_warning || ir_received_data.ir_warning ||
               uv_received_data.uv_warning || smoke_received_data.sensor_warning) && (!battery_replaced) ) {//add device_disconnected warning tfrom disconnected wifi to firebase
        // Warning condition
        if (alarmOn) {
            alarmOn = false;
            disable_alarm(); // optional safety
        }
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

        /*
        if (device_disconnected) {
            strcat(warning_message, "Wi-Fi is OFF");
        }
        */

        // Trim trailing space if present
        size_t len = strlen(warning_message);
        if (len > 0 && warning_message[len - 1] == ' ') {
                    warning_message[len - 1] = '\0';  // Remove last space
        }
    
        alarm_json["alarm_status"] = "Warning";
        alarm_json["message"] = warning_message;
        updateDataToFirebase(db, firebase_path + "/Alarm", alarm_json);
    
    } else {
        // Safe condition
        if (alarmOn) {
            alarmOn = false;
        }

        disable_alarm();
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
}