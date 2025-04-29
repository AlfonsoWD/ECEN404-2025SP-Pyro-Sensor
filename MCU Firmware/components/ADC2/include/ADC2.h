/*
NOTE: ADC2 reading is floating if it's not connected to anything (left open), so some delay has to be added before start taking reading
*/

#ifndef ADC2
#define ADC2

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "soc/soc_caps.h"
#include "esp_log.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"

struct ADC2_Initialization_Parameters {
    adc_oneshot_unit_handle_t adc2_handle;
    adc_oneshot_unit_init_cfg_t init_config2;
    adc_oneshot_chan_cfg_t config2;
};

typedef struct ADC2_Initialization_Parameters ADC2_Ini_Parameters;

typedef struct {
    char message[100];
    bool battery_good;
} BatteryData;

//process: start port(i.e., ADC2), calibrate channel(e.g., ADC2 Channel), voltage reading logic(could be infinite loop). 
//If needed: decalibrate channel, calibrate channel.
ADC2_Ini_Parameters ADC2_Port_calibration();
static bool ADC2_Channel_calibration(ADC2_Ini_Parameters Channel_Parameters, int Channel_Number, adc_cali_handle_t *out_handle);
static void ADC2_Channel_decalibration(adc_cali_handle_t handle);
static void ADC2_Delete_Port(adc_oneshot_unit_handle_t Port_Handle);
int read_voltage2(bool CalibrationStatus, adc_oneshot_unit_handle_t Port_Handle, adc_cali_handle_t Channel_Handle, int Channel_number, int Port_number);
void run_adc2(void* param);

#ifdef __cplusplus
}
#endif

#endif // ADC2
