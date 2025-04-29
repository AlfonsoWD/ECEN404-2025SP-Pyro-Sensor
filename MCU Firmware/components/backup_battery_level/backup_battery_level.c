/**
 * @file backup_battery_level.cpp
 * @brief This file contains a function to calculate the voltage of the backup battery.
 *
 * It reads the voltage from a specified ADC channel for a given sampling duration and calculates
 * the average voltage. The result is converted to the corresponding backup battery voltage.
 */

 #include <stdio.h>
 #include "backup_battery_level.h"
 
 #define Vout_SAMPLING_DURATION 1000 ///< Time in milliseconds to average the Rs readings
 #define Vout_SAMPLING_PERIOD 20 ///< Time in milliseconds between readings during sampling
 #define R1 20000 ///< Resistor R1 value (20k Ohms)
 #define R2 10000 ///< Resistor R2 value (10k Ohms)
 #define Battery_VCC 9 ///< Backup battery voltage (9V)
 #define Vin_Ratio_for_battery 2.69696969696969696 ///< Voltage divider ratio for the battery
 
 /**
  * @brief Calculates the backup battery voltage.
  *
  * This function reads the ADC channel multiple times over a specified sampling duration, averages
  * the readings, and converts the result into the corresponding backup battery voltage. The voltage 
  * is then adjusted based on the voltage divider ratio and the output is returned in volts.
  *
  * @param CalibrationOutcome2 Boolean indicating the calibration outcome for the ADC reading.
  * @param Port_Handle The ADC port handle for reading the voltage.
  * @param Handle_Channel The ADC calibration handle for the channel being used.
  * @param number_Channel The number of the ADC channel to be read.
  * @param Port_number The number of the ADC port.
  * @return The calculated backup battery voltage in volts.
  */
 float calculate_BatteryVoltage(bool CalibrationOutcome2, adc_oneshot_unit_handle_t Port_Handle,
                                adc_cali_handle_t Handle_Channel, int number_Channel, int Port_number) {
     int sum_voltage = 0; ///< Sum of all the voltage readings
     int count = 0; ///< Count of the number of voltage readings
     int VoltageOutput = 0; ///< The current voltage output reading
 
     TickType_t start_time = xTaskGetTickCount(); ///< Store the start time for the sampling duration
     
     // Collect voltage readings for the specified sampling duration
     while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(Vout_SAMPLING_DURATION)) {
         // Read the voltage from the specified ADC channel
         VoltageOutput = read_voltage2(CalibrationOutcome2, Port_Handle, Handle_Channel, number_Channel, Port_number);
 
         // Accumulate the voltage readings for averaging
         sum_voltage += VoltageOutput;
         count++;
 
         // Delay before taking the next reading (sampling period)
         vTaskDelay(pdMS_TO_TICKS(Vout_SAMPLING_PERIOD));
     }
 
     // Step 5: Calculate the average voltage from the readings
     int average_voltage = (count > 0) ? (sum_voltage / count) : 0;
 
     // Temporary limit for maximum allowed voltage until new divider ratio is used
     if (average_voltage > 3157) {
         average_voltage = 3157;
     }
 
     // Step 6: Calculate the corresponding battery voltage in volts
     float VRL = (average_voltage * Vin_Ratio_for_battery);
     VRL = VRL / 1000.0; ///< Convert the voltage from mV to V
 
     // Adjust the voltage by adding a constant offset (0.7V)
     float return_value = VRL + 0.7;
 
     // Return the final calculated voltage value
     return return_value;
 }
 