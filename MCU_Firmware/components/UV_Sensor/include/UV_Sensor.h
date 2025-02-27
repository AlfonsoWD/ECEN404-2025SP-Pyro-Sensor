#ifndef UV_Sensor
#define UV_Sensor

#ifdef __cplusplus
extern "C" {
#endif

#include "ADC1.h"

/*
This function returns the averaged V0 (in V) from the IR_Sensor, this function should be ran under lights on, and during daytime if possible
*/
float UVSensor_Calibrate(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number);

/*
This function calculates the analog voltage (A0) from the infrared Sensor, returns A0 in Volts
*/
float UVSensor_calculate_A0(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number);

#ifdef __cplusplus
}
#endif

#endif
