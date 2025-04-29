/**
 * @file ADC2.c
 * @brief This file contains functions for ADC2 calibration and for reading and processing backup battery voltage.
 *
 * It initializes ADC2, performs calibration, reads voltage from a specified ADC channel, and determines the 
 * status of the backup battery (e.g., low, good, or over-limit) based on the voltage readings.
 */

 #include "ADC2.h"
 #include "backup_battery_level.h"
 
 #define EXAMPLE_ADC_ATTEN2 ADC_ATTEN_DB_12 ///< ADC attenuation setting (12dB)
 #define Battery_A0_Channel ADC_CHANNEL_0 ///< ADC channel for backup battery (GPIO 11)
 #define Battery_Wait_Duration 1000 ///< Time in milliseconds between each battery level check
 #define Battery_Standard 9.8 ///< Standard voltage for a fully charged backup battery (9.8V)
 #define Battery_threshold_voltage 7.2 ///< Voltage at which the backup battery is considered dead
 #define Battery_Halfway 7.6 ///< Voltage at which the battery is considered half-charged
 
 extern QueueHandle_t battery_queue; ///< Queue to send battery data to app_main
 extern volatile bool Delete_Tasks; ///< Flag to signal task deletion
 
 /**
  * @brief Initializes ADC2 port with default configuration.
  *
  * This function configures ADC2 for reading the backup battery voltage and returns the initialized parameters.
  * It sets up the ADC unit, clock source, and obtains a handle for ADC2 to be used in subsequent operations.
  *
  * @return The initialized ADC2 parameters including unit configuration and handle.
  */
 ADC2_Ini_Parameters ADC2_Port_calibration() {
     ADC2_Ini_Parameters ADC2_start;
     ADC2_start.init_config2.unit_id = ADC_UNIT_2;
     ADC2_start.init_config2.clk_src = 0;
     ESP_ERROR_CHECK(adc_oneshot_new_unit(&ADC2_start.init_config2, &ADC2_start.adc2_handle));
     ADC2_start.config2.bitwidth = ADC_BITWIDTH_DEFAULT;
     ADC2_start.config2.atten = EXAMPLE_ADC_ATTEN2;
     return ADC2_start;
 }
 
 /**
  * @brief Calibrates the ADC2 channel with the specified parameters.
  *
  * This function performs calibration on the ADC2 channel using curve fitting or line fitting depending on 
  * the calibration scheme supported by the ESP32 hardware. It returns a boolean indicating whether the calibration 
  * was successful.
  *
  * @param Channel_Parameters The configuration parameters for the ADC2 channel.
  * @param Channel_Number The ADC channel number to calibrate.
  * @param[out] out_handle A pointer to store the handle for the calibration process.
  * @return True if calibration was successful, false otherwise.
  */
 static bool ADC2_Channel_calibration(ADC2_Ini_Parameters Channel_Parameters, int Channel_Number, adc_cali_handle_t *out_handle) {
     ESP_ERROR_CHECK(adc_oneshot_config_channel(Channel_Parameters.adc2_handle, Channel_Number, &Channel_Parameters.config2));
 
     bool calibrated = false;
     adc_cali_handle_t handle = NULL;
     esp_err_t ret = ESP_FAIL;
 
     #if ADC_CALI_SCHEME_CURVE_FITTING_SUPPORTED
         if (!calibrated) {
             ESP_LOGI("ADC2_Port_calibration", "calibration scheme version is %s", "Curve Fitting");
             adc_cali_curve_fitting_config_t cali_config = {
                 .unit_id = Channel_Parameters.init_config2.unit_id,
                 .chan = Channel_Number,
                 .atten = Channel_Parameters.config2.atten,
                 .bitwidth = Channel_Parameters.config2.bitwidth,
             };
             ret = adc_cali_create_scheme_curve_fitting(&cali_config, &handle);
             if (ret == ESP_OK) {
                 calibrated = true;
             }
         }
     #endif
 
     #if ADC_CALI_SCHEME_LINE_FITTING_SUPPORTED
         if (!calibrated) {
             ESP_LOGI("ADC2 Calibration", "calibration scheme version is %s", "Line Fitting");
             adc_cali_line_fitting_config_t cali_config = {
                 .unit_id = Channel_Parameters.init_config2.unit_id,
                 .atten = Channel_Number,
                 .bitwidth = Channel_Parameters.config2.bitwidth,
             };
             ret = adc_cali_create_scheme_line_fitting(&cali_config, &handle);
             if (ret == ESP_OK) {
                 calibrated = true;
             }
         }
     #endif
 
     *out_handle = handle;
     if (ret == ESP_OK) {
         ESP_LOGI("ADC2_Channel_calibration", "Calibration Success");
     } else if (ret == ESP_ERR_NOT_SUPPORTED || !calibrated) {
         ESP_LOGW("ADC2_Channel_calibration", "eFuse not burnt, skip software calibration");
     } else {
         ESP_LOGE("ADC2_Channel_calibration", "Invalid arg or no memory");
     }
 
     return calibrated;
 }
 
 /**
  * @brief De-calibrates the ADC2 channel calibration handle.
  *
  * This function releases the calibration handle and de-registers the calibration scheme used for ADC2.
  *
  * @param handle The calibration handle to be de-registered.
  */
 static void ADC2_Channel_decalibration(adc_cali_handle_t handle) {
     #if ADC_CALI_SCHEME_CURVE_FITTING_SUPPORTED
         ESP_LOGI("ADC2_Channel_decalibration", "deregister %s calibration scheme", "Curve Fitting");
         ESP_ERROR_CHECK(adc_cali_delete_scheme_curve_fitting(handle));
 
     #elif ADC_CALI_SCHEME_LINE_FITTING_SUPPORTED
         ESP_LOGI("ADC2_Channel_decalibration", "deregister %s calibration scheme", "Line Fitting");
         ESP_ERROR_CHECK(adc_cali_delete_scheme_line_fitting(handle));
     #endif   
 }
 
 /**
  * @brief Reads the voltage from the specified ADC channel.
  *
  * This function reads the raw ADC value from the specified channel and, if calibration is enabled, 
  * converts the raw value to a corresponding voltage. The voltage is returned in millivolts.
  *
  * @param CalibrationStatus A flag indicating whether to use calibration.
  * @param Port_Handle The ADC port handle to be used.
  * @param Channel_Handle The calibration handle for the channel.
  * @param Channel_Number The number of the ADC channel to read.
  * @param Port_number The ADC port number.
  * @return The voltage reading from the ADC channel in millivolts.
  */
 int read_voltage2(bool CalibrationStatus, adc_oneshot_unit_handle_t Port_Handle, adc_cali_handle_t Channel_Handle, int Channel_Number, int Port_number) {
     int voltage[1];
     int adc_raw[1];
 
     ESP_ERROR_CHECK(adc_oneshot_read(Port_Handle, Channel_Number, &adc_raw[0]));
     if (CalibrationStatus) {
         ESP_ERROR_CHECK(adc_cali_raw_to_voltage(Channel_Handle, adc_raw[0], &voltage[0]));
     }
 
     return voltage[0];
 }
 
 /**
  * @brief Deletes the ADC2 port handle to release resources.
  *
  * This function deinitializes the ADC2 port handle and releases the associated resources.
  *
  * @param Port_Handle The ADC port handle to delete.
  */
 static void ADC2_Delete_Port(adc_oneshot_unit_handle_t Port_Handle) {
     ESP_ERROR_CHECK(adc_oneshot_del_unit(Port_Handle));
 }
 
 /**
  * @brief Periodically checks the backup battery voltage and updates the battery status.
  *
  * This function periodically reads the backup battery voltage using ADC2, compares it against predefined 
  * thresholds, and sends the battery status (e.g., low, good, or over-limit) to a queue for further processing.
  *
  * @param param Unused parameter, typically NULL for task initialization.
  */
 void run_adc2(void* param) {
     // Initialize ADC2 port and calibration
     ADC2_Ini_Parameters TestingParameters2 = ADC2_Port_calibration();
 
     // Initialize calibration handle for Battery_A0_Channel
     adc_cali_handle_t Battery_Handle_Channel2 = NULL;
     bool Battery_CalibrationOutcome = ADC2_Channel_calibration(TestingParameters2, Battery_A0_Channel, &Battery_Handle_Channel2);
 
     float BatteryVoltage = 0.0;
     BatteryData battery_data;
 
     while(true) {
         // Calculate battery voltage
         BatteryVoltage = calculate_BatteryVoltage(Battery_CalibrationOutcome, TestingParameters2.adc2_handle, Battery_Handle_Channel2, Battery_A0_Channel, 1 + TestingParameters2.init_config2.unit_id);
 
         // Check if the backup battery is below threshold
         if (BatteryVoltage < Battery_threshold_voltage) {
             battery_data.battery_good = false;
             snprintf(battery_data.message, sizeof(battery_data.message), "Alert: Backup battery is discharged. Replace 9V battery");
         } else if (BatteryVoltage >= Battery_threshold_voltage && BatteryVoltage <= Battery_Halfway) {
             battery_data.battery_good = true;
             snprintf(battery_data.message, sizeof(battery_data.message), "Warning: Low battery level.");
         } else if (BatteryVoltage >= Battery_Halfway && BatteryVoltage <= Battery_Standard) {
             battery_data.battery_good = true;
             snprintf(battery_data.message, sizeof(battery_data.message), "Battery level within acceptable range.");
         } else {
             battery_data.battery_good = false;
             snprintf(battery_data.message, sizeof(battery_data.message), "Battery level is over-limit. Replace with a 9V battery.");
         }
 
         // Send battery data to the queue
         if (xQueueOverwrite(battery_queue, &battery_data) != pdPASS) {
             ESP_LOGE("run_adc2", "Failed to send backup battery data to queue");
         }
         
         vTaskDelay(pdMS_TO_TICKS(Battery_Wait_Duration));
 
         // Check if tasks should be deleted
         if (Delete_Tasks == true) {
             break;
         }
     }
 
     vTaskDelete(NULL);
 }
 