#include <stdio.h>
#include "IR_Sensor.h"

// Define constants for sensor operation
#define V0_SAMPLING_DURATION 2000  // Time (in ms) for IR sensor to get base values (room value)

#define A0_SAMPLING_DURATION 500  // Time (in milliseconds) to average Rs
#define A0_SAMPLING_PERIOD 20     // Time (in milliseconds) between readings during A0_SAMPLING_DURATION
#define IR_SENSOR_RATIO 1.768     // Calibration ratio for IR sensor voltage

/**
 * @brief Calibrate the IR sensor by averaging the voltage over a sampling period.
 *        This function collects sensor readings for a defined duration (V0_SAMPLING_DURATION),
 *        averages them, and applies a calibration ratio to get the final value.
 *
 * @param CalibrationOutcome1   Boolean to determine if calibration passed or failed
 * @param Port_Handle           ADC port handle to access the ADC unit
 * @param Handle_Channel        ADC calibration handle for the specific channel
 * @param number_Channel        Channel number for the ADC to read from
 * @param Port_number           The number of the port to be used for voltage readings
 *
 * @return float                The calibrated voltage value (V0) in Volts
 */
float IRSensor_Calibrate(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle, adc_cali_handle_t Handle_Channel, int number_Channel, int Port_number)
{
    int sum_voltage = 0;         // Variable to accumulate the sum of voltage readings
    int count = 0;               // Variable to count the number of readings
    int VoltageOutput = 0;       // Variable to store individual voltage readings

    // Get the start time for averaging
    TickType_t start_time = xTaskGetTickCount();

    // Read voltage continuously for V0_SAMPLING_DURATION (two minutes)
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(V0_SAMPLING_DURATION)) { 
        VoltageOutput = read_voltage(CalibrationOutcome1, Port_Handle, Handle_Channel, number_Channel, Port_number); // Read voltage from the sensor
        sum_voltage += VoltageOutput;  // Accumulate the voltage readings
        count++;  // Increment the reading count
        
        // Delay between readings to maintain the sampling period
        vTaskDelay(pdMS_TO_TICKS(A0_SAMPLING_PERIOD));     
    }

    // Calculate the average voltage
    float result = (float)(sum_voltage) / count;
    float V0 = (count > 0) ? result : 0;  // Avoid division by zero if no readings

    // Apply a temporary limit for voltage (max 2.4V)
    if (V0 > 3157.0) {
        V0 = 3157.0;  // Cap the voltage at 3157mV until the new divider ratio is used
    }

    V0 = V0 * IR_SENSOR_RATIO;  // Apply the sensor calibration ratio
    V0 = V0 / 1000.0;  // Convert the value to Volts

    return V0;  // Return the calibrated voltage
}

/**
 * @brief Read and calculate the A0 value by averaging the voltage over a sampling period.
 *        This function collects sensor readings for a defined duration (A0_SAMPLING_DURATION),
 *        averages them, and applies a calibration ratio to get the final value.
 *
 * @param CalibrationOutcome1   Boolean to determine if calibration passed or failed
 * @param Port_Handle           ADC port handle to access the ADC unit
 * @param Handle_Channel        ADC calibration handle for the specific channel
 * @param number_Channel        Channel number for the ADC to read from
 * @param Port_number           The number of the port to be used for voltage readings
 *
 * @return float                The calibrated voltage value (A0) in Volts
 */
float IRSensor_calculate_A0(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle, adc_cali_handle_t Handle_Channel, int number_Channel, int Port_number) {
    int sum_voltage = 0;         // Variable to accumulate the sum of voltage readings
    int count = 0;               // Variable to count the number of readings
    int VoltageOutput = 0;       // Variable to store individual voltage readings
    
    // Get the start time for averaging
    TickType_t start_time = xTaskGetTickCount();

    // Read voltage continuously for A0_SAMPLING_DURATION
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(A0_SAMPLING_DURATION)) {
        VoltageOutput = read_voltage(CalibrationOutcome1, Port_Handle, Handle_Channel, number_Channel, Port_number); // Read voltage from the sensor
        sum_voltage += VoltageOutput;  // Accumulate the voltage readings
        count++;  // Increment the reading count

        // Delay between readings to maintain the sampling period
        vTaskDelay(pdMS_TO_TICKS(A0_SAMPLING_PERIOD));
    }

    // Calculate the average voltage
    float result = (float)(sum_voltage) / count;
    float A0 = (count > 0) ? result : 0;  // Avoid division by zero if no readings

    // Apply a temporary limit for voltage (max 2.4V)
    if (A0 > 3157.0) {
        A0 = 3157.0;  // Cap the voltage at 3157mV until the new divider ratio is used
    }

    A0 = A0 * IR_SENSOR_RATIO;  // Apply the sensor calibration ratio
    A0 = A0 / 1000.0;  // Convert the value to Volts

    return A0;  // Return the calibrated voltage
}
