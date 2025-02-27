#include <stdio.h>
#include "backup_battery_level.h"

#define Vout_SAMPLING_DURATION 1000 //time (in milliseconds) to average Rs
#define Vout_SAMPLING_PERIOD 20//time (in milliseconds) between readings during R0_SAMPLING_DURATION
#define R1 20000 //5.6k Ohms
#define R2 10000 //3.3k Ohms
#define Battery_VCC 9 // 9V backup battery

float calculate_BatteryVoltage(bool CalibrationOutcome2, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel, int number_Channel, int Port_number) {
    int sum_voltage = 0;
    int count = 0;
    int VoltageOutput = 0;

    TickType_t start_time = xTaskGetTickCount();   
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(Vout_SAMPLING_DURATION)) {
        //printf("Reading value\r\n");
        // Read the voltage from one of the channels (you can choose to read from either or both)
        VoltageOutput = read_voltage2(CalibrationOutcome2, Port_Handle, Handle_Channel, number_Channel, Port_number);
        
        // Accumulate the voltage readings
        sum_voltage += VoltageOutput;
        count++;

        // Delay second between readings (you can adjust this delay); sampling rate
        vTaskDelay(pdMS_TO_TICKS(Vout_SAMPLING_PERIOD));
    }

        // Step 5: Calculate the average voltage

    int average_voltage = (count > 0) ? (sum_voltage / count) : 0;
    if (average_voltage > 3157) { //temporary decision until new divider ratio is soldered, i.e., max = 2.4 V to the pins
        average_voltage = 3157;
    }                          
    printf("average voltage = %u\r\n",average_voltage);
    // Step 6: Output the average voltage (log it or return it)
    float voltage_divider_ratio = (float) (R1 + R2) / R2; 
    float VRL = (float) (average_voltage) * voltage_divider_ratio;
    VRL = VRL / 1000.0; //convert from mV to V

    float return_value = VRL;

    return return_value;
}

