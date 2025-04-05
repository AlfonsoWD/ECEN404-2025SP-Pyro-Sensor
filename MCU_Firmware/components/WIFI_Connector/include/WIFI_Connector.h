#ifndef WIFI_Connector
#define WIFI_Connector

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_SSID_LENGTH 19
#define MAX_PASSWORD_LENGTH 19
#define MAX_USER_ID_LENGTH 38
#define MAX_USER_ID_LENGTH2 19
#define ROOM_NAME_LENGTH 19
#define MAX_RETRIES 10
#define MAX_RETRIES_RUNTIME 

char* Connect_To_WIFI();


#ifdef __cplusplus
}
#endif

#endif
