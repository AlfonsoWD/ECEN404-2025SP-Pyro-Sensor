#include <stdio.h>
#include "IR_Sensor.h"

//boot up
#define V0_SAMPLING_DURATION 2000  //time in ms for ir sensor to get base values (i.e., and ir sensor to get room value)

/*
Normal operation
*/
#define A0_SAMPLING_DURATION 500 //time (in milliseconds) to average Rs
#define A0_SAMPLING_PERIOD 20//time (in milliseconds) between readings during A0_SAMPLING_DURATION
#define IR_SENSOR_RATIO 1.768 

//initiazliation delay (in ms) duration for averaging V0 (in V)
float IRSensor_Calibrate(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number)
{
    int sum_voltage = 0;
    int count = 0;
    int VoltageOutput = 0;

    // Get the start time for averaging (two minutes)
    TickType_t start_time = xTaskGetTickCount();

    // Step 4: Read voltage continuously for initialization delay
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(V0_SAMPLING_DURATION)) { 
    
        VoltageOutput = read_voltage(CalibrationOutcome1, Port_Handle, Handle_Channel, number_Channel, Port_number);
        // Accumulate the voltage readings
        sum_voltage += VoltageOutput;
        count++;
        //printf("Read voltage for ir sensor: %u\r\n",VoltageOutput);

        // Delay second between readings (you can adjust this delay); sampling rate
        vTaskDelay(pdMS_TO_TICKS(A0_SAMPLING_PERIOD));     
    }

    float result = (float)(sum_voltage) / count;
    // Step 5: Calculate the average voltage
    float V0 = (count > 0) ? result : 0;


    if (V0 > 3157.0) {////temporary decision until new divider ratio is soldered, i.e., max = 2.4 V to the pins
        V0 = 3157.0;
    }

    V0 = V0 * IR_SENSOR_RATIO;
    V0 = V0 / 1000.0;
    //printf("Vin for the IR Sensor: %f\r\n",V0);

    return V0;
}

float IRSensor_calculate_A0(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number) {
    int sum_voltage = 0;
    int count = 0;
    int VoltageOutput = 0;
    // Get the start time for averaging 
    TickType_t start_time = xTaskGetTickCount();
    // Step 4: Read voltage continuously for RS_SAMPLING_DURATION 
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(A0_SAMPLING_DURATION)) {
        //printf("Reading value\r\n");
        // Read the voltage from one of the channels (you can choose to read from either or both)
        VoltageOutput = read_voltage(CalibrationOutcome1, Port_Handle, Handle_Channel, number_Channel, Port_number);
        //printf("Read voltage for ir sensor: %u\r\n",VoltageOutput);

        // Accumulate the voltage readings
        sum_voltage += VoltageOutput;
        count++;

        // Delay second between readings (you can adjust this delay); sampling rate
        vTaskDelay(pdMS_TO_TICKS(A0_SAMPLING_PERIOD));
    }

    float result = (float)(sum_voltage) / count;
    // Step 5: Calculate the average voltage
    float A0 = (count > 0) ? result : 0;

    if (A0 > 3157.0) {////temporary decision until new divider ratio is soldered, i.e., max = 2.4 V to the pins
        A0 = 3157.0;
    }

  
    A0 = A0 * IR_SENSOR_RATIO;  
    A0 = A0 / 1000.0;
    //printf("Vin for the IR sensor: %f\r\n",A0);
    return A0;
}


