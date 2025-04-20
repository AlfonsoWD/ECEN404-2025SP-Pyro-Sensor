#ifndef ADC1
#define ADC1

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

//ADC1 structure for parameters
struct ADC1_Initialization_Parameters {
    adc_oneshot_unit_handle_t adc1_handle;
    adc_oneshot_unit_init_cfg_t init_config1;
    adc_oneshot_chan_cfg_t config;
};

typedef struct ADC1_Initialization_Parameters ADC1_Ini_Parameters;
//************************************************************************ */

//Structure for Gas sensor status
typedef struct {
    char message[100];   // To store the message (e.g., "Gas detected: <concentration> ppm")
    bool sensor_triggered;  // Boolean to indicate if the gas sensor is triggered
    bool sensor_in_scope;   // Boolean to indicate if the sensor is in scope
    bool gas_warning;
} GasSensorData;
//****************************************************************** */

typedef struct {
    char message[100];
    bool sensor_triggered;
    bool sensor_confirmed;
    bool ir_warning;
} IRSensorData;

typedef struct {
    char message[100];
    bool sensor_triggered;
    bool sensor_confirmed;
    bool uv_warning;
} UVSensorData;

//process: start port(i.e.,, ADC1), calibrate channel(e.g., ADC1 Channel3), voltage reading logic(could be infinite loop). If needed: decalibrate channel, calibrate channel. 
ADC1_Ini_Parameters ADC1_Port_calibration();
static bool ADC1_Channel_calibration(ADC1_Ini_Parameters Channel_Parameters, int Channel_Number, adc_cali_handle_t *out_handle);
static void ADC1_Channel_decalibration(adc_cali_handle_t handle);
static void ADC1_Delete_Port(adc_oneshot_unit_handle_t Port_Handle);
int read_voltage(bool CalibrationStatus, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Channel_Handle,int Channel_number, int Port_number);
void run_adc1(void* param); 

#ifdef __cplusplus
}
#endif

#endif