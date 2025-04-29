#ifndef BACKUP_BATTERY_LEVEL
#define BACKUP_BATTERY_LEVEL

#ifdef __cplusplus
extern "C" {
#endif

#include "ADC2.h"

float calculate_BatteryVoltage(bool CalibrationOutcome2, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel, int number_Channel, int Port_number);

#ifdef __cplusplus
}
#endif

#endif //BACKUP_BATTERY_LEVEL