/*
 make sure to keep voltage input to GPIO pin below 3.3 V, use voltage divider if necessary : 10k, 20k resistors + 10n/100n.. capacitors to filter out noise
 https://ai.thestempedia.com/docs/evive-iot-kit/interfacing-mq-2-gas-sensor-with-evive/ //how to write detecting formula
 https://www.l-com.com/Images/Downloadables/Manuals/M_SRAQ-G004.pdf //sensor datasheet

 //continue implementing the detecting formula and the initilization for R0 during boot up 

 */


#include "SRAQ_Gas_Sensor.h"
#include <math.h>

// Define ADC1 Channels
#define A0 ADC_CHANNEL_5  // GPIO pin 6 on the ESP32S3 board (IO4 = A0)
#define D0 ADC_CHANNEL_4  // GPIO pin 5 on the ESP32S3 board (IO5 = D0)

#define A0_VOLTAGE_DIVIDER_RATIO 0.577367205543 // Adjust as needed. R1 = 10k, R2 = 20K. R2 bottom resistor (connected to ground)
#define D0_VOLTAGE_DIVIDER_RATIO 0.658 // Adjust as needed
#define VCC 5 //voltage supplied to gas sensor (in V)
#define RL 1000 //load resistor in Ohms

/*
Boot up
*/
#define R0_SAMPLING_DURATION 2000  // time (in milliseconds) duration to average R0 (e.g., 2 minutes)
#define R0_SAMPLING_PERIOD 10//time (in milliseconds) between readings during R0_SAMPLING_DURATION

/*
Normal operation
*/
#define RS_SAMPLING_DURATION 1000 //time (in milliseconds) to average Rs
#define RS_SAMPLING_PERIOD 20//time (in milliseconds) between readings during R0_SAMPLING_DURATION

int calculate_R0(bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number) {
    int sum_voltage = 0;
    int count = 0;
    int VoltageOutput = 0;

    // Get the start time for averaging (two minutes)
    TickType_t start_time = xTaskGetTickCount();
    //printf("Got in2\r\n");
    // Step 4: Read voltage continuously for 2 minutes (120,000 ms)
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(R0_SAMPLING_DURATION)) {
        //printf("Reading value\r\n");
        // Read the voltage from one of the channels (you can choose to read from either or both)
        VoltageOutput = read_voltage(CalibrationOutcome1, Port_Handle, Handle_Channel, number_Channel, Port_number);
        
        // Accumulate the voltage readings
        sum_voltage += VoltageOutput;
        count++;

        // Delay second between readings (you can adjust this delay); sampling rate
        vTaskDelay(pdMS_TO_TICKS(R0_SAMPLING_PERIOD));
    }

    // Step 5: Calculate the average voltage
    int average_voltage = (count > 0) ? (sum_voltage / count) : 0;

    if (average_voltage > 3157) {////temporary decision until new divider ratio is soldered, i.e., max = 2.4 V to the pins
        average_voltage = 3157;
    }

    // Step 6: Output the average voltage (log it or return it)
    float VRL = average_voltage * A0_VOLTAGE_DIVIDER_RATIO;
    VRL = VRL / 1000.0; //convert from mV to V

   // ESP_LOGI("calculate_R0()", "Average VRL over %d seconds: %.4f Volts", R0_SAMPLING_DURATION / 1000, VRL);
   printf("Gas Vo = %f\r\n",VRL);
    int R0 = (VCC*RL)/(VRL) - RL;
    //printf("R0: %u\r\n",R0);
    //ESP_LOGI("calculate_R0()", "Average R0 over %d seconds: %d Ohms", R0_SAMPLING_DURATION / 1000, R0);
    return R0;
}

//this function returns the ratio Rs/R0 (the values in the y-axis of MQ2 plot) after averaging Rs
//inputs: R0 in linear scale, and ADC configuration
float calculate_RS_R0_ratio(int R0, bool CalibrationOutcome1, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Handle_Channel,int number_Channel, int Port_number) {
    int sum_voltage = 0;
    int count = 0;
    int VoltageOutput = 0;
    // Get the start time for averaging 
    TickType_t start_time = xTaskGetTickCount();
    //printf("Got in2\r\n");
    // Step 4: Read voltage continuously for RS_SAMPLING_DURATION 
    while (xTaskGetTickCount() - start_time < pdMS_TO_TICKS(RS_SAMPLING_DURATION)) {
        //printf("Reading value\r\n");
        // Read the voltage from one of the channels (you can choose to read from either or both)
        VoltageOutput = read_voltage(CalibrationOutcome1, Port_Handle, Handle_Channel, number_Channel, Port_number);
        
        // Accumulate the voltage readings
        sum_voltage += VoltageOutput;
        count++;

        // Delay second between readings (you can adjust this delay); sampling rate
        vTaskDelay(pdMS_TO_TICKS(RS_SAMPLING_PERIOD));
    }

    // Step 5: Calculate the average voltage
    int average_voltage = (count > 0) ? (sum_voltage / count) : 0;

    if (average_voltage > 3157) {////temporary decision until new divider ratio is soldered, i.e., max = 2.4 V to the pins
        average_voltage = 3157;
    }

    // Step 6: Output the average voltage (log it or return it)
    float VRL = average_voltage * A0_VOLTAGE_DIVIDER_RATIO;
    VRL = VRL / 1000.0; //convert from mV to V

    printf("Vrl = %f\r\n",VRL);
   // ESP_LOGI("calculate_RS_R0_ratio()", "Average VRL over %d seconds: %.4f Volts", RS_SAMPLING_DURATION / 1000, VRL);
    float RS = (VCC*RL)/(VRL) - RL; 
    //printf("RS = %f\r\n",RS);
    float linear_RS_R0_ratio = RS/R0;
    return linear_RS_R0_ratio;

}

//this function calculates the ppm of a particular gas given it's Rs/R0 ratio in linear scale
//y-intercept in linear scale, and slope in logarithmic scale
//the return value is the gas's ppm in linear scale
float calculate_ppm(float linear_RS_RO_Ratio, float y_intercept, float slope){
    float logarithmic_ppm = (log10(linear_RS_RO_Ratio) - y_intercept)/slope; //log(x) = [log(y) - b] / m
    float linear_ppm = pow(10,logarithmic_ppm); //x = 10 ^ {[log(y) - b] / m}
    return linear_ppm;
}