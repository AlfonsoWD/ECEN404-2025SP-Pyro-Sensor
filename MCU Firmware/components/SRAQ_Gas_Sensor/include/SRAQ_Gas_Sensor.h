#ifndef SRAQ_Gas
#define SRAQ_Gas

#ifdef __cplusplus
extern "C" {
#endif

#include "ADC1.h"

//function to initialize sensor: calculate resistance in fresh air
int calculate_R0(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number);
float calculate_RS_R0_ratio(int R0, bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number);
float calculate_ppm(float linear_RS_RO_Ratio, float y_intercept, float slope);

//function to actively calculate resistance

//function that sends Rs/R0 

#ifdef __cplusplus
}
#endif

#endif

/*Iniitalize: 
   -run calculateRs() with ADC1 for gas sensor from GasSensor() component as a task
      -this main function in GasSensor() should: calculate_R0()
      -calculate_Rs()
      -Calculate_Sensitivity()
         -use a queue to send RS/R0, and ppm to app_main()
      
*threshold logic: 
      -follow "Gas Sensor" schematic sheet to develop signal generation, and information dispaly based on value of Rs/R0

*/


