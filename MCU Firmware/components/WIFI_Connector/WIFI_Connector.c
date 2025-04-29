//Team member: Oscar Hernandez
//Subsystem: MCU Firmware

//************************************************************************************************************INTRODUCTION********************************************************************************************** */
//This code allows the ESP32-S3 to connect to wifi by gaining Wi-Fi credentials through BLE Client

//Client should:
// 1) Connect to device name "PYRO_SERVER"
// 2) Inside the "custom/unkown Service" UUID = d69f19de17c4a287ab4b8d0785a44361 (ignore Generic Access and Generic Attribute services):
     // a)Send the SSID value to UUID = f9772ab62d31407687c03e0c3ddb467c
     // b)Send the password value to UUID = 317fa8f398f6406ab1f7b97d829dae6f
     // c)Send the userID firt half value to UUID = 06f22fda6c3245db9c02a5d4efdfb999
     // d)Send the userID second half value to UUID = 0d0a67ef530b4447a2c08f08b2d44b63

        //***NOTE: ALL VALUES WRITTEN TO THE ABOVE CHARACTERISTICS MUST BE UTF-8 text
        //***NOTE: UUID is a hexadecimal number
//****************************************************************************************************************************************************************************************************************** */

#include <stdio.h>
#include "string.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"
#include "esp_event.h"
#include "nvs_flash.h"
#include "esp_log.h"
#include "esp_nimble_hci.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "esp_wifi.h"
#include "sdkconfig.h"
#include "WIFI_Connector.h"
bool reconnection_successful = false;

char *TAG = "ESP-Device";
uint8_t ble_addr_type;
extern volatile bool device_disconnected;

const char *Empty_Password = "";
bool empty_password = false;
extern volatile bool successful_initial_connection;

void ble_app_advertise(void);
void start_ble_advertising(void);
void stop_ble(void);
void wifi_connection(void);
void ble_app_on_sync(void);
static const struct ble_gatt_svc_def gatt_svcs[];

extern volatile int64_t device_start_time;

esp_netif_t *wifi_netif = NULL;

static const ble_uuid128_t UserID_Identifier_Second_Half = BLE_UUID128_INIT( //SECOND HALF OF USER ID
    0x0d, 0x0a, 0x67, 0xef, 0x53, 0x0b, 0x44, 0x47,
    0xa2, 0xc0, 0x8f, 0x08, 0xb2, 0xd4, 0x4b, 0x63);

static const ble_uuid128_t Service_Identifier = BLE_UUID128_INIT(//declaration of Service Identifier from online code generator
    0xd6, 0x9f, 0x19, 0xde, 0x17, 0xc4, 0xa2, 0x87,
    0xab, 0x4b, 0x8d, 0x07, 0x85, 0xa4, 0x43, 0x61);

static const ble_uuid128_t SSID_Identifier = BLE_UUID128_INIT(
    0xf9, 0x77, 0x2a, 0xb6, 0x2d, 0x31, 0x40, 0x76,
    0x87, 0xc0, 0x3e, 0x0c, 0x3d, 0xdb, 0x46, 0x7c);

static const ble_uuid128_t Password_Identifier = BLE_UUID128_INIT(
    0x31, 0x7f, 0xa8, 0xf3, 0x98, 0xf6, 0x40, 0x6a,
    0xb1, 0xf7, 0xb9, 0x7d, 0x82, 0x9d, 0xae, 0x6f);

static const ble_uuid128_t UserID_Identifier_First_Half = BLE_UUID128_INIT(//FIRST HALF OF USER ID
    0x06, 0xf2, 0x2f, 0xda, 0x6c, 0x32, 0x45, 0xdb,
    0x9c, 0x02, 0xa5, 0xd4, 0xef, 0xdf, 0xb9, 0x99);

static const ble_uuid128_t Room_Name = BLE_UUID128_INIT(
    0x80, 0xdb, 0x7a, 0xbf, 0xd2, 0xc4, 0x48, 0xaf,
    0x8e, 0x30, 0x0c, 0x15, 0x58, 0xad, 0x37, 0x3f);

                
uint8_t Counter = 0;

char WIFI_SSID[MAX_SSID_LENGTH];
char WIFI_PASSWORD[MAX_PASSWORD_LENGTH];
char USER_ID[MAX_USER_ID_LENGTH];
char USER_ID2[MAX_USER_ID_LENGTH2];
extern char ROOM_NAME[ROOM_NAME_LENGTH];

int retry_num = 0;
bool wifi_connected = false;
bool waiting_for_credentials = true; // Flag to indicate when credentials are needed

void ble_hs_task(void *param);

// Function to stop the Wi-Fi interface if it exists
void stop_wifi() {
    if (wifi_netif != NULL) {
        esp_netif_destroy(wifi_netif); // Destroy the Wi-Fi interface to free resources
        wifi_netif = NULL;             // Set pointer to NULL after destruction
    }
    esp_wifi_stop(); // Stop the Wi-Fi driver
}

// Callback function to handle Wi-Fi and IP events
static void wifi_event_handler(void *event_handler_arg, esp_event_base_t event_base, int32_t event_id, void *event_data) {
    printf("retry num = %u\r\n",retry_num);
    
    if (event_id == WIFI_EVENT_STA_START) {
        ESP_LOGI(TAG, "Wi-Fi connecting...");
        retry_num = 0;
        device_disconnected = false;
    
    } 
    else if (event_id == WIFI_EVENT_STA_CONNECTED) {
        ESP_LOGI(TAG, "Wi-Fi connected");
        wifi_connected = true;
        retry_num = 0;
        device_disconnected = false;

    } 
    else if ((event_id == WIFI_EVENT_STA_DISCONNECTED)&&(successful_initial_connection)) {
        // Handle disconnection after a previously successful connection
        ESP_LOGI(TAG, "Wi-Fi lost connection");
        wifi_connected = false;

        ESP_LOGI(TAG, "Retrying Wi-Fi connection");
        esp_wifi_connect(); // Attempt reconnection
    }
    else if (event_id == WIFI_EVENT_STA_DISCONNECTED) {
        ESP_LOGI(TAG, "Wi-Fi lost connection");
        wifi_connected = false;
        
        if (retry_num < MAX_RETRIES) {
            esp_wifi_connect(); // Attempt to reconnect
            retry_num++;
            ESP_LOGI(TAG, "Retrying Wi-Fi connection... Attempt %d/%d", retry_num, MAX_RETRIES);
            device_disconnected = true;
        } else {
            ESP_LOGI(TAG, "Wi-Fi connection failed. Restarting BLE for new credentials...");
            esp_wifi_stop(); // Stop Wi-Fi
            waiting_for_credentials = true; // Request new credentials
            retry_num = 0; // Reset retry counter
            start_ble_advertising(); // Restart BLE advertising to accept new credentials
        }
    } else if (event_id == IP_EVENT_STA_GOT_IP) {
        retry_num = 0;
        ESP_LOGI(TAG, "Wi-Fi got IP...");
        device_disconnected = false; // Clear disconnect flag
    }
}


// Function to reinitialize BLE advertising and GATT services
void Readvertise_ble() {
    vTaskDelay(pdMS_TO_TICKS(1000));              // Delay to allow BLE to reset
    nimble_port_init();                           // Initialize NimBLE stack
    ble_svc_gatt_init();                          // Initialize GATT services
    ble_gatts_count_cfg(gatt_svcs);               // Count attributes in GATT services
    ble_gatts_add_svcs(gatt_svcs);                // Add GATT services to the server
    ble_hs_cfg.sync_cb = ble_app_on_sync;         // Set the sync callback
    nimble_port_freertos_init(ble_hs_task);       // Start BLE host task
}

void wifi_connection() {
    esp_netif_init();                             // Initialize TCP/IP stack
    esp_event_loop_create_default();              // Create default event loop
    
    wifi_netif = esp_netif_create_default_wifi_sta(); // Create default Wi-Fi station
    if (wifi_netif == NULL) {
        ESP_LOGE(TAG, "Failed to create default Wi-Fi STA interface");
        return;
    }

    wifi_init_config_t wifi_initiation = WIFI_INIT_CONFIG_DEFAULT(); // Default Wi-Fi init config
    esp_wifi_init(&wifi_initiation);           // Initialize Wi-Fi with configuration

    esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event_handler, NULL); // Register Wi-Fi event handler
    esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL); // Register IP event handler

    wifi_config_t wifi_configuration = { 0 };   // Initialize Wi-Fi config struct
    strcpy((char*)wifi_configuration.sta.ssid, WIFI_SSID); // Copy SSID into config

    if (empty_password == true) {
        printf("Connecting to empty password\r\n");
        strcpy((char*)wifi_configuration.sta.password, Empty_Password); // Use empty password
    } else {
        strcpy((char*)wifi_configuration.sta.password, WIFI_PASSWORD); // Use provided password
    }

    esp_wifi_set_config(ESP_IF_WIFI_STA, &wifi_configuration); // Set Wi-Fi configuration
    esp_wifi_start();                      // Start Wi-Fi driver
    esp_wifi_set_mode(WIFI_MODE_STA);     // Set mode to Station
    esp_wifi_connect();                   // Attempt to connect
    ESP_LOGI(TAG, "Wi-Fi connection initiated.");
}

// BLE write handler for Room Name characteristic
static int device_write_ROOM_NAME(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg) {
    size_t len = ctxt->om->om_len;                      // Get length of received data
    if (len >= ROOM_NAME_LENGTH) len = ROOM_NAME_LENGTH - 1; // Prevent buffer overflow
    strncpy(ROOM_NAME, (char *)ctxt->om->om_data, len); // Copy received data to ROOM_NAME buffer
    ROOM_NAME[len] = '\0';                              // Null-terminate the string
    ESP_LOGI(TAG, "Received Room Name: %s", ROOM_NAME); // Log the received room name
    Counter = Counter + 1;                              // Increment counter
    return 0;                                           // Indicate success
}


// BLE GATT write handler: receives and stores Wi-Fi SSID from BLE client
static int device_write_SSID(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg) {
    size_t len = ctxt->om->om_len;
    if (len >= MAX_SSID_LENGTH) len = MAX_SSID_LENGTH - 1; // Prevent buffer overflow
    strncpy(WIFI_SSID, (char *)ctxt->om->om_data, len);
    WIFI_SSID[len] = '\0'; // Null-terminate
    ESP_LOGI(TAG, "Received SSID: %s", WIFI_SSID);
    Counter = Counter + 1; // Increment credential part counter
    return 0;
}

// BLE GATT write handler: receives and stores Wi-Fi password from BLE client
static int device_write_PASSWORD(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg) {
    size_t len = ctxt->om->om_len;

    // Copy the received data into WIFI_PASSWORD buffer
    if (len >= MAX_PASSWORD_LENGTH) {
        len = MAX_PASSWORD_LENGTH - 1;
    }
    strncpy(WIFI_PASSWORD, (char *)ctxt->om->om_data, len);
    WIFI_PASSWORD[len] = '\0'; // Ensure null termination

    // Check if the received password is the literal "\0" string
    if (WIFI_PASSWORD[0] == '\\' && WIFI_PASSWORD[1] == '0' && WIFI_PASSWORD[2] == '\0') {
        printf("Got a literal \"\\0\" string, assuming open network \r\n");
        empty_password = true;
    } else if (len == 0) {  // Check if the password is empty
        empty_password = true;
        printf("The string is empty.\n");
    } else {
        printf("Didn't get an empty string\r\n");
    }

    ESP_LOGI(TAG, "Received Password: %s", WIFI_PASSWORD);
    Counter++;

    return 0;
}

// Function to handle writing the first half of the user ID to the device.
static int device_write_USERID(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg) {
    // Get the length of the incoming data.
    size_t len = ctxt->om->om_len;
    
    // Ensure the length does not exceed the maximum allowed for the USER_ID.
    if (len >= MAX_USER_ID_LENGTH) len = MAX_USER_ID_LENGTH - 1;
    
    // Copy the received data into the USER_ID buffer, ensuring it's null-terminated.
    strncpy(USER_ID, (char *)ctxt->om->om_data, len);
    USER_ID[len] = '\0';
    
    // Log the received first half of the user ID for debugging.
    ESP_LOGI(TAG, "Received User ID first half: %s", USER_ID);
    
    // Increment the counter to track the number of times this function is called.
    Counter = Counter + 1;
    
    return 0; // Return success.
}

// Function to handle writing the second half of the user ID to the device.
static int device_write_USERID2(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg) {
    // Get the length of the incoming data.
    size_t len = ctxt->om->om_len;
    
    // Ensure the length does not exceed the maximum allowed for the USER_ID2.
    if (len >= MAX_USER_ID_LENGTH2) len = MAX_USER_ID_LENGTH2 - 1;
    
    // Copy the received data into the USER_ID2 buffer, ensuring it's null-terminated.
    strncpy(USER_ID2, (char *)ctxt->om->om_data, len);
    USER_ID2[len] = '\0';
    
    // Log the received second half of the user ID for debugging.
    ESP_LOGI(TAG, "Received User ID second half: %s", USER_ID2);
    
    // Increment the counter to track the number of times this function is called.
    Counter = Counter + 1;
    
    return 0; // Return success.
}

// Function to stop BLE operations by deinitializing the BLE stack.
void stop_ble() {
    // Log a message to indicate BLE is stopping.
    ESP_LOGI(TAG, "Stopping BLE...");
    
    // Stop the BLE port and deinitialize the BLE stack.
    nimble_port_stop();
    nimble_port_deinit();
}

// Function to start BLE advertising.
void start_ble_advertising() {
    // Log a message to indicate BLE advertising is starting.
    ESP_LOGI(TAG, "Starting BLE advertising...");
    
    // Call function to initiate BLE advertising.
    ble_app_advertise();
}

// BLE event handling
static int ble_gap_event(struct ble_gap_event *event, void *arg)
{
    printf("Inside ble_gap_event \r\n");
    switch (event->type)
    {
    // Advertise if connected
    case BLE_GAP_EVENT_CONNECT:
        ESP_LOGI("GAP", "BLE GAP EVENT CONNECT %s", event->connect.status == 0 ? "OK!" : "FAILED!");
        if (event->connect.status != 0)
        {
            ble_app_advertise();
        }
        break;
    // Advertise again after completion of the event
    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI("GAP", "BLE GAP EVENT DISCONNECTED");
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        ESP_LOGI("GAP", "BLE GAP EVENT");
        ble_app_advertise();
        break;
    default:
        break;
    }
    return 0;
}

/*
 * Function to initialize and start BLE advertisement.
 * It sets the device name and configures the connectivity parameters for advertising.
 */
void ble_app_advertise(void) {
        // GAP - device name definition
        struct ble_hs_adv_fields fields;
        const char *device_name;
        memset(&fields, 0, sizeof(fields));
        device_name = ble_svc_gap_device_name(); // Read the BLE device name
        fields.name = (uint8_t *)device_name;
        fields.name_len = strlen(device_name);
        fields.name_is_complete = 1;
        ble_gap_adv_set_fields(&fields);
    
        // GAP - device connectivity definition
        struct ble_gap_adv_params adv_params;
        memset(&adv_params, 0, sizeof(adv_params));
        adv_params.conn_mode = BLE_GAP_CONN_MODE_UND; // connectable or non-connectable
        adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN; // discoverable or non-discoverable
        ble_gap_adv_start(ble_addr_type, NULL, BLE_HS_FOREVER, &adv_params, ble_gap_event, NULL);
}

/*
 * Function for the infinite task to run NimBLE's main loop.
 * It handles BLE events and task scheduling.
 */
void ble_hs_task(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit(); // This function will return only when nimble_port_stop() is executed
}

/*
 * Descriptor for the Wi-Fi SSID characteristic.
 * This function returns the description for the SSID when requested.
 */
static int SSID_Descriptor(uint16_t conn_handle, uint16_t attr_handle,
    struct ble_gatt_access_ctxt *ctxt, void *arg) {
    char string[] = "WIFI SSID";
    ble_uuid128_t gatt_svr_dsc_uuid = BLE_UUID128_INIT(0x2901);
    int rc;
    rc = os_mbuf_append(ctxt->om,
                        string,
                        strlen(string));
    return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

/*
 * Descriptor for the Wi-Fi Password characteristic.
 * This function returns the description for the password when requested.
 */
static int Password_Descriptor(uint16_t conn_handle, uint16_t attr_handle,
    struct ble_gatt_access_ctxt *ctxt, void *arg) {
    char string[] = "WIFI Password";
    ble_uuid128_t gatt_svr_dsc_uuid = BLE_UUID128_INIT(0x2901);
    int rc;
    rc = os_mbuf_append(ctxt->om,
                        string,
                        strlen(string));
    return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

/*
 * Descriptor for the First User ID characteristic.
 * This function returns the description for the first user ID when requested.
 */
static int First_User_ID_Descriptor(uint16_t conn_handle, uint16_t attr_handle,
    struct ble_gatt_access_ctxt *ctxt, void *arg) {
    char string[] = "First User ID";
    ble_uuid128_t gatt_svr_dsc_uuid = BLE_UUID128_INIT(0x2901);
    int rc;
    rc = os_mbuf_append(ctxt->om,
                        string,
                        strlen(string));
    return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

/*
 * Descriptor for the Second User ID characteristic.
 * This function returns the description for the second user ID when requested.
 */
static int Second_User_ID_Descriptor(uint16_t conn_handle, uint16_t attr_handle,
    struct ble_gatt_access_ctxt *ctxt, void *arg) {
    char string[] = "Second User ID";
    ble_uuid128_t gatt_svr_dsc_uuid = BLE_UUID128_INIT(0x2901);
    int rc;
    rc = os_mbuf_append(ctxt->om,
                        string,
                        strlen(string));
    return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

/*
 * Descriptor for the Device Name characteristic.
 * This function returns the description for the device name when requested.
 */
static int Device_Name_Descriptor(uint16_t conn_handle, uint16_t attr_handle,
    struct ble_gatt_access_ctxt *ctxt, void *arg) {
    char string[] = "Device Name";
    ble_uuid128_t gatt_svr_dsc_uuid = BLE_UUID128_INIT(0x2901);
    int rc;
    rc = os_mbuf_append(ctxt->om,
                        string,
                        strlen(string));
    return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

/*
 * Array of GATT service definitions. This array configures
 * the characteristics and descriptors for each service.
 */
static const struct ble_gatt_svc_def gatt_svcs[] = {
    {.type = BLE_GATT_SVC_TYPE_PRIMARY,
    .uuid = (ble_uuid_t *)&Service_Identifier,                 // Define UUID for device type
        .characteristics = (struct ble_gatt_chr_def[]){
        {
            .uuid = (ble_uuid_t *)&UserID_Identifier_Second_Half,           // Define UUID for reading
            .flags = BLE_GATT_CHR_F_WRITE,
            .access_cb = device_write_USERID2,
            .descriptors = (struct ble_gatt_dsc_def[])
            {
                {
                    .uuid = BLE_UUID16_DECLARE(0x2901), // User Description Descriptor UUID
                    .att_flags = BLE_ATT_F_READ,                  
                    .access_cb = Second_User_ID_Descriptor,
                }, {
                    0,
                }
            },          
        },
        {
            .uuid = (ble_uuid_t *)&SSID_Identifier,           // Define UUID for writing
            .flags = BLE_GATT_CHR_F_WRITE,
            .access_cb = device_write_SSID,
            .descriptors = (struct ble_gatt_dsc_def[])
            {
                {
                    .uuid = BLE_UUID16_DECLARE(0x2901), // User Description Descriptor UUID
                    .att_flags = BLE_ATT_F_READ,                  
                    .access_cb = SSID_Descriptor,
                }, {
                    0,
                }
            },
        },
        {
            .uuid = (ble_uuid_t *)&Password_Identifier,           // Define UUID for writing
            .flags = BLE_GATT_CHR_F_WRITE,
            .access_cb = device_write_PASSWORD,
            .descriptors = (struct ble_gatt_dsc_def[])
            {
                {
                    .uuid = BLE_UUID16_DECLARE(0x2901), // User Description Descriptor UUID
                    .att_flags = BLE_ATT_F_READ,                  
                    .access_cb = Password_Descriptor,
                }, {
                    0,
                }
            },
        },
        {
            .uuid = (ble_uuid_t *)&UserID_Identifier_First_Half,           // Define UUID for writing
            .flags = BLE_GATT_CHR_F_WRITE,
            .access_cb = device_write_USERID,
            .descriptors = (struct ble_gatt_dsc_def[])
            {
                {
                    .uuid = BLE_UUID16_DECLARE(0x2901), // User Description Descriptor UUID
                    .att_flags = BLE_ATT_F_READ,                  
                    .access_cb = First_User_ID_Descriptor,
                }, {
                    0,
                }
            },
        },

        {
            .uuid = (ble_uuid_t *)&Room_Name,  
            .flags = BLE_GATT_CHR_F_WRITE,    
            .access_cb = device_write_ROOM_NAME,
            .descriptors = (struct ble_gatt_dsc_def[])
            {
                {
                    .uuid = BLE_UUID16_DECLARE(0x2901), // User Description Descriptor UUID
                    .att_flags = BLE_ATT_F_READ,                  
                    .access_cb = Device_Name_Descriptor,
                }, {
                    0,
                }
            },
        },

        {0}}},
    {0}
};

/*
 * Function to handle BLE synchronization callback.
 * This is invoked when BLE synchronization is complete.
 */
void ble_app_on_sync(void)
{
    ble_hs_id_infer_auto(0, &ble_addr_type); // Determines the best address type automatically
    ble_app_advertise();                     // Define the BLE connection
}

/*
 * Function to establish a Wi-Fi connection based on BLE-provided credentials.
 * It first initializes BLE, advertises, and waits for the user to input Wi-Fi credentials.
 * Once the credentials are received, it connects to the Wi-Fi.
 */
char* Connect_To_WIFI() {


    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ESP_ERROR_CHECK(nvs_flash_init());
    }

    //attempt one connection

    nimble_port_init();
    ble_svc_gap_device_name_set("PYRO_SERVER"); // 4 - Initialize NimBLE configuration - server name
    ble_svc_gap_init();                        // 4 - Initialize NimBLE configuration - gap service
    ble_svc_gatt_init();                       // 4 - Initialize NimBLE configuration - gatt service
    ble_gatts_count_cfg(gatt_svcs);            // 4 - Initialize NimBLE configuration - config gatt services
    ble_gatts_add_svcs(gatt_svcs);             // 4 - Initialize NimBLE configuration - queues gatt services.
    ble_hs_cfg.sync_cb = ble_app_on_sync;      // 5 - Initialize application
    nimble_port_freertos_init(ble_hs_task);    // 6 - Run Host task
    

    while (true) {
        if (Counter == 5) {
             device_start_time = esp_timer_get_time();

            waiting_for_credentials = false;
            Counter = 0;

            //concate first and second half of user id strings
            printf("USER_ID = %s\r\n",USER_ID);
            printf("USER_ID2 = %s\r\n",USER_ID2);
            strcat(USER_ID,USER_ID2);

            printf("USER ID inside loop = %s\r\n",USER_ID);
        }
        if (waiting_for_credentials) {
            ESP_LOGI(TAG, "Waiting for Wi-Fi credentials via BLE...");
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }
        
        ESP_LOGI(TAG, "Stopping BLE and connecting to Wi-Fi...");
        stop_ble();
        wifi_connection();
        
        // Wait for Wi-Fi connection result
        int wait_time = 0;
        while (wait_time < 10 && !wifi_connected) {
            vTaskDelay(pdMS_TO_TICKS(1000));
            wait_time++;
        }

        if (!wifi_connected) {
            ESP_LOGI(TAG, "Wi-Fi connection failed. Restarting BLE for new credentials...");
            stop_wifi();  // Use stop_wifi() instead of esp_wifi_stop()
            waiting_for_credentials = true;
            retry_num = 0;
            Readvertise_ble();
        }
        
        else {
            break;
        }
    }

    // Populate the struct with FULL_USER_ID and ROOM_NAME

    USER_ID[MAX_USER_ID_LENGTH - 1] = '\0';  // Ensure null termination
    
return USER_ID;
} 