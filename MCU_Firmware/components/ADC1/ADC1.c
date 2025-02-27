#include "ADC1.h"
#include "SRAQ_Gas_Sensor.h"
#include "IR_Sensor.h"
#include "UV_Sensor.h"
#include "esp_timer.h"
#include <math.h>
//TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function TODO: fix ppm function 
#define EXAMPLE_ADC_ATTEN ADC_ATTEN_DB_12

//Carbon Monoxide parameters as per MQ2 log-log graph for Gas Sensor
#define m_CO_logarithmic_slope -0.3177878851
#define b_CO_yintercept 1.459759961

#define Gas_A0_Channel ADC_CHANNEL_3  // GPIO pin 4 on the ESP32S3 board (IO4 = Gas_A0_Channel)
#define Gas_D0_Channel ADC_CHANNEL_4  // GPIO pin 5 on the ESP32S3 board (IO5 = Gas_D0_Channel)

#define IR_A0_Channel ADC_CHANNEL_5 //GPIO pin 6 
#define IR_D0_Channel ADC_CHANNEL_6 //GPIO pin 7

#define UV_A0_Channel ADC_CHANNEL_8 //gpio pin 9
#define UV_D0_Channel ADC_CHANNEL_9 //gpio pin 10

#define IR_Percentage_Threshold 0.25//percentage difference (in %/100) between IR_A0_Channel and IR_V0 at which IR Sensor is triggered 0.25-1.5%
#define IR_Comparison_Time 5000000//time (in us) for infrared source to remain active before considered as a fire by IR sensor
#define IR_Logic_Mode 0 //0 = logic based on Ao, 1 = logic based on Do
#define IR_VCC 3.1 //Supply voltage of the ir sensor in Volts. For now it's as if was 3.1

#define UV_Percentage_Threshold 0.25//percentage difference (in %/100) between IR_A0_Channel and IR_V0 at which IR Sensor is triggered 0.25-1.5%
#define UV_Comparison_Time 5000000//time (in ms) for infrared source to remain active before considered as a fire by IR sensor
#define UV_Logic_Mode 0 //0 = logic based on Ao, 1 = logic based on Do
#define UV_VCC 3.1 //Supply voltage of the ir sensor in Volts
                             
#define Gas_HeatUP_time 1000 //heat up time (in ms) gas sensor, after long time: should be 30 minutes

extern QueueHandle_t gas_sensor_queue;
extern QueueHandle_t ir_sensor_queue;
extern QueueHandle_t uv_sensor_queue;

ADC1_Ini_Parameters ADC1_Port_calibration() {
    ADC1_Ini_Parameters ADC1_start;
     ADC1_start.init_config1.unit_id = ADC_UNIT_1;
    ADC1_start.init_config1.clk_src = 0;   /////////////////////////////////DELETE????
    ESP_ERROR_CHECK(adc_oneshot_new_unit(&ADC1_start.init_config1, &ADC1_start.adc1_handle)); //might need a pointer for address of &ADC1_start.adc1handle

    ADC1_start.config.bitwidth = ADC_BITWIDTH_DEFAULT;
    ADC1_start.config.atten = EXAMPLE_ADC_ATTEN;
    return ADC1_start;
}

static bool ADC1_Channel_calibration(ADC1_Ini_Parameters Channel_Parameters, int Channel_Number, adc_cali_handle_t *out_handle) {
    ESP_ERROR_CHECK(adc_oneshot_config_channel(Channel_Parameters.adc1_handle, Channel_Number, &Channel_Parameters.config)); //might need a pointer for address of &ADC1_start.adc1handle

    bool calibrated = false; 
    adc_cali_handle_t handle = NULL;
     esp_err_t ret = ESP_FAIL;

    #if ADC_CALI_SCHEME_CURVE_FITTING_SUPPORTED
        if (!calibrated) {
            ESP_LOGI("ADC1_Port_calibration", "calibration scheme version is %s", "Curve Fitting");
            adc_cali_curve_fitting_config_t cali_config = {
                .unit_id = Channel_Parameters.init_config1.unit_id,
                .chan = Channel_Number,
                .atten = Channel_Parameters.config.atten,
                .bitwidth = Channel_Parameters.config.bitwidth,
            };
            ret = adc_cali_create_scheme_curve_fitting(&cali_config, &handle);
            if (ret == ESP_OK) {
                calibrated = true;
            }
        }
    #endif

    #if ADC_CALI_SCHEME_LINE_FITTING_SUPPORTED
        if (!calibrated) {
            ESP_LOGI("ADC1 Calibration", "calibration scheme version is %s", "Line Fitting");
            adc_cali_line_fitting_config_t cali_config = {
                .unit_id = Channel_Parameters.init_config1.unit_id,
                .atten = Channel_Number,
                .bitwidth = Channel_Parameters.config.bitwidth,
            };
            ret = adc_cali_create_scheme_line_fitting(&cali_config, &handle);
            if (ret == ESP_OK) {
                calibrated = true;
            }
        }
    #endif

        *out_handle = handle;
        if (ret == ESP_OK) {
            ESP_LOGI("ADC1_Channel_calibration", "Calibration Success");
        } else if (ret == ESP_ERR_NOT_SUPPORTED || !calibrated) {
            ESP_LOGW("ADC1_Channel_calibration", "eFuse not burnt, skip software calibration");
        } else {
            ESP_LOGE("ADC1_Channel_calibration", "Invalid arg or no memory");
        }

    return calibrated;
}

static void ADC1_Channel_decalibration(adc_cali_handle_t handle) {
    #if ADC_CALI_SCHEME_CURVE_FITTING_SUPPORTED
        ESP_LOGI("ADC1_Channel_decalibration", "deregister %s calibration scheme", "Curve Fitting");
        ESP_ERROR_CHECK(adc_cali_delete_scheme_curve_fitting(handle));

    #elif ADC_CALI_SCHEME_LINE_FITTING_SUPPORTED
        ESP_LOGI("ADC1_Channel_decalibration", "deregister %s calibration scheme", "Line Fitting");
        ESP_ERROR_CHECK(adc_cali_delete_scheme_line_fitting(handle));
    #endif   
}

int read_voltage(bool CalibrationStatus, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Channel_Handle,int Channel_Number, int Port_number) {
    int voltage[1];
    int adc_raw[1];

    ESP_ERROR_CHECK(adc_oneshot_read(Port_Handle, Channel_Number, &adc_raw[0]));
    if (CalibrationStatus) {
        ESP_ERROR_CHECK(adc_cali_raw_to_voltage(Channel_Handle, adc_raw[0], &voltage[0]));
        //ESP_LOGI("read_voltage()", "ADC%d Channel[%d] Cali Voltage: %d mV", Port_number, Channel_Number, voltage[0]);
    }
    else {
        ESP_LOGE("read_voltage()", "CalibrationStatus == False");
    }  
    return voltage[0];
}

static void ADC1_Delete_Port(adc_oneshot_unit_handle_t Port_Handle) {
    ESP_ERROR_CHECK(adc_oneshot_del_unit(Port_Handle));
}


//Below code shows how to read from two channels of ADC1 
void run_adc1(void* param)
{
    vTaskDelay(pdMS_TO_TICKS(Gas_HeatUP_time)); //heat up gas sensor after long time of no use
    //initialize ADC1 Unit/Port
    ADC1_Ini_Parameters TestingParameters = ADC1_Port_calibration();

    //initialize ADC1 Channels
    adc_cali_handle_t Gas_A0_Handle_Channel = NULL;
    adc_cali_handle_t IR_A0_Handle_Channel = NULL;
    adc_cali_handle_t UV_A0_Handle_Channel = NULL;

    //calibrate channels
    bool Gas_A0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters, Gas_A0_Channel, &Gas_A0_Handle_Channel);
    bool IR_A0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,IR_A0_Channel,&IR_A0_Handle_Channel);
    bool UV_A0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,UV_A0_Channel,&UV_A0_Handle_Channel);

    //get base values for analog readings
    
    float Gas_R0 = calculate_R0(Gas_A0_CalibrationOutcome, TestingParameters.adc1_handle, Gas_A0_Handle_Channel, Gas_A0_Channel, 1+TestingParameters.init_config1.unit_id);//Calculate R0 for Gas Sensor
    
    float IR_V0 = IRSensor_Calibrate(IR_A0_CalibrationOutcome,TestingParameters.adc1_handle,IR_A0_Handle_Channel,IR_A0_Channel,1+TestingParameters.init_config1.unit_id);
    //printf("IR_V0 = %f\r\n",IR_V0);
    float UV_V0 = UVSensor_Calibrate(UV_A0_CalibrationOutcome,TestingParameters.adc1_handle,UV_A0_Handle_Channel,UV_A0_Channel,1+TestingParameters.init_config1.unit_id);

    //declare variables needed for gas sensor
    float Rs_R0 = 0;
    float ppm_level = 0;
    GasSensorData gas_sensor_data; //// Create a variable to hold data to send for gas sensor

    //declare variables needed for ir sensor
    float IR_A0 = 0;
    int IR_D0 = 0;
    bool IR_D0_CalibrationOutcome = false;
    int64_t ir_last_detection_time = 0;
    bool ir_flame_detected = false;
    int64_t IR_Comparison_Duration = 0;
    adc_cali_handle_t IR_D0_Handle_Channel = NULL;
    float IR_Percentage_Difference = 0.0;
    IRSensorData ir_sensor_data;

    //declare variables needed for uv sensor
    float UV_A0 = 0;
    int UV_D0 = 0;
    bool UV_D0_CalibrationOutcome = false;
    int64_t uv_last_detection_time = 0;
    bool uv_flame_detected = false;
    int64_t UV_Comparison_Duration = 0;
    adc_cali_handle_t UV_D0_Handle_Channel = NULL;
    float UV_Percentage_Difference = 0.0;
    IRSensorData uv_sensor_data;

    if (IR_Logic_Mode == 1) {//initialize IR_D0 in case D0 logic is used
        IR_D0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,IR_D0_Channel,&IR_D0_Handle_Channel);
    }

    if (UV_Logic_Mode == 1) {//initialize UV_D0 in case D0 logic is used
        UV_D0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,UV_D0_Channel,&UV_D0_Handle_Channel);
    }

    //ADC1 reading loop
    while (true) {
        /*
        //-------------------------------------------------------------------------------------------------------------------------------------------------------------------
                                                                                                                 GAS SENSOR LOGIC
        */  
        Rs_R0 = calculate_RS_R0_ratio(Gas_R0, Gas_A0_CalibrationOutcome, TestingParameters.adc1_handle, Gas_A0_Handle_Channel, Gas_A0_Channel, 1 + TestingParameters.init_config1.unit_id);

        
        Rs_R0 = pow(10,Rs_R0);
        //printf("Rs/R0 logarithmic = %f\r\n",Rs_R0);
        
        // Calculate PPM Level, and status of Gas sensor
        if ((Rs_R0 >= 3) && (Rs_R0 < 5)) {
            ppm_level = calculate_ppm(Rs_R0, b_CO_yintercept, m_CO_logarithmic_slope);
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "CO detected: Gas Concentration = %.2f ppm", ppm_level);
            gas_sensor_data.sensor_triggered = true;
            gas_sensor_data.sensor_in_scope = true;
        } else if ((Rs_R0 >= 0.26) && (Rs_R0 < 3)) {
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas detected: Gas Concentration >= 200 ppm");
            gas_sensor_data.sensor_triggered = true;
            gas_sensor_data.sensor_in_scope = true;
        } else if ((Rs_R0 < 0.26) || (Rs_R0 >= 11.2)) {
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas detected out of scope");
            gas_sensor_data.sensor_triggered = true;
            gas_sensor_data.sensor_in_scope = false;
        } else {
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas not detected");
            gas_sensor_data.sensor_triggered = false;
            gas_sensor_data.sensor_in_scope = true;
        }

        // Log the data for Excel export
       // ESP_LOGI("Sensor Data", "Time: %lu, Rs/R0: %.2f, PPM: %.2f, Triggered: %d, In Scope: %d", 
            //xTaskGetTickCount(), Rs_R0, ppm_level, gas_sensor_data.sensor_triggered, gas_sensor_data.sensor_in_scope);

        /*
        **********************************************************************************************************************************************************************************************************************8
                                                                                                            IR SENSOR LOGIC
        */
        if (IR_Logic_Mode == 0) { // Logic based on A0
            // Read the current IR_A0 value (analog voltage level) 
            IR_A0 = IRSensor_calculate_A0(IR_A0_CalibrationOutcome, TestingParameters.adc1_handle, IR_A0_Handle_Channel, IR_A0_Channel, 1 + TestingParameters.init_config1.unit_id);
           printf("A0 = %f\r\n",IR_A0);

    
            // Calculate the percentage difference between IR_A0 and the baseline IR_V0
            IR_Percentage_Difference = (float)(IR_V0 - IR_A0) / IR_V0;

            printf("Percentage difference = %f\r\n",IR_Percentage_Difference);
            printf("Percentage thresholdd = %f\r\n",IR_Percentage_Threshold);
            // Compare with the threshold to detect if there's flame (IR wavelength detected)
            printf("IR Comparison Duration1: %lld ms\r\n", IR_Comparison_Duration);

            vTaskDelay(pdMS_TO_TICKS(2000));
            if ((IR_Percentage_Difference > IR_Percentage_Threshold) && (IR_Percentage_Difference > 0)) {
                if (ir_flame_detected == false) {
                    printf("got in\r\n");
                    ir_last_detection_time = esp_timer_get_time();
                    ir_flame_detected = true;

                    //printf("Flame detected: IR wavelength detected.\n");
                    snprintf(ir_sensor_data.message, sizeof(ir_sensor_data.message), "IR Sensor:760-1100 nm wavelength detected. Voltage = %f V is below threshold",IR_A0);
                    ir_sensor_data.sensor_triggered = true;
                }
                

                else if (ir_flame_detected == true) {
                    IR_Comparison_Duration = (esp_timer_get_time() - ir_last_detection_time);
                    if (IR_Comparison_Duration >= IR_Comparison_Time) {
                        // Confirm flame detected if the condition holds for the duration
                        //printf("Flame confirmed: IR wavelength detected for %d ms.\n", IR_Comparison_Duration);
                        snprintf(ir_sensor_data.message, sizeof(ir_sensor_data.message), "IR Sensor confirmed: Voltage = %f V is below threshold for %lld s.", IR_A0, IR_Comparison_Duration/1000000);
                        ir_sensor_data.sensor_confirmed = true;    
                        IR_Comparison_Duration = 0;        
                    } 
                    //reset
                    //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                    //printf("IR Comparison Duration1: %d ms\r\n", IR_Comparison_Duration);
                 }



            } 
            
            else {
                snprintf(ir_sensor_data.message, sizeof(ir_sensor_data.message), "IR Sensor: flame not detected. Voltage = %f V",IR_A0);
                ir_sensor_data.sensor_triggered = false;
                ir_sensor_data.sensor_confirmed = false;
                                    printf("Here3\r]n");
                //printf("Flame not detected: Insufficient time.\n");
                ir_flame_detected = false;
            }

                         // Debug print to check the duration calculation
                        //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                        //printf("IR Comparison Duration2: %d ms\r\n", IR_Comparison_Duration);
            
        }  else if (IR_Logic_Mode == 1) { // Logic based on D0 (digital output)
            // Read the current IR_D0 value (digital read from the D0 pin)
            IR_D0 = IRSensor_calculate_A0(IR_D0_CalibrationOutcome, TestingParameters.adc1_handle, IR_D0_Handle_Channel, IR_D0_Channel, 1 + TestingParameters.init_config1.unit_id);

            // Check if IR_D0 value falls within the expected range to detect flame
            if (IR_D0 > (IR_VCC - IR_VCC * 0.01) && IR_D0 < (IR_VCC + IR_VCC * 0.01)) {
                //printf("Flame detected: IR wavelength detected (D0 mode).\n");
                snprintf(ir_sensor_data.message,sizeof(ir_sensor_data.message),"IR Sensor: 760-1100 nm wavelength detected, D0 is in logical high. Voltage = %u V", IR_D0);
                ir_sensor_data.sensor_triggered = true;
            } else {
                //printf("Flame not detected: IR wavelength not detected (D0 mode).\n");
                snprintf(ir_sensor_data.message,sizeof(ir_sensor_data.message),"IR Sensor: flame not detected, D0 is in logical low. Voltage = %u V", IR_D0);
                ir_sensor_data.sensor_triggered = false;
            }
        } else {
            printf("Invalid IR Logic Mode.\n");
        }
            /*
            **********************************************************************************************************************************************************************************************************************8
            */
        /*
        **********************************************************************************************************************************************************************************************************************8
                                                                                                            UV SENSOR LOGIC
        */
        if (UV_Logic_Mode == 0) { // Logic based on A0
            // Read the current IR_A0 value (analog voltage level) 
            UV_A0 = UVSensor_calculate_A0(UV_A0_CalibrationOutcome, TestingParameters.adc1_handle, UV_A0_Handle_Channel, UV_A0_Channel, 1 + TestingParameters.init_config1.unit_id);
       

    
            // Calculate the percentage difference between IR_A0 and the baseline IR_V0
            UV_Percentage_Difference = (float)(UV_V0 - UV_A0) / UV_V0;

            printf("Percentage difference = %f\r\n",UV_Percentage_Difference);
            printf("Percentage thresholdd = %f\r\n",UV_Percentage_Threshold);
            // Compare with the threshold to detect if there's flame (IR wavelength detected)
            printf("UV Comparison Duration1: %lld ms\r\n", UV_Comparison_Duration);

            vTaskDelay(pdMS_TO_TICKS(2000));
            if ((UV_Percentage_Difference > UV_Percentage_Threshold) && (UV_Percentage_Difference > 0)) {
                if (uv_flame_detected == false) {
                    printf("got in\r\n");
                    uv_last_detection_time = esp_timer_get_time();
                    uv_flame_detected = true;

                    //printf("Flame detected: IR wavelength detected.\n");
                    snprintf(uv_sensor_data.message, sizeof(uv_sensor_data.message), "UV Sensor:760-1100 nm wavelength detected. Voltage = %f V is below threshold",UV_A0);
                    uv_sensor_data.sensor_triggered = true;
                }
                

                else if (uv_flame_detected == true) {
                    UV_Comparison_Duration = (esp_timer_get_time() - uv_last_detection_time);
                    if (UV_Comparison_Duration >= UV_Comparison_Time) {
                        // Confirm flame detected if the condition holds for the duration
                        //printf("Flame confirmed: IR wavelength detected for %d ms.\n", IR_Comparison_Duration);
                        snprintf(uv_sensor_data.message, sizeof(uv_sensor_data.message), "UV Sensor confirmed: Voltage = %f V is below threshold for %lld s.", UV_A0, UV_Comparison_Duration/1000000);
                        uv_sensor_data.sensor_confirmed = true;    
                        UV_Comparison_Duration = 0;        
                    } 
                    //reset
                    //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                    //printf("IR Comparison Duration1: %d ms\r\n", IR_Comparison_Duration);
                 }



            } 
            
            else {
                snprintf(uv_sensor_data.message, sizeof(uv_sensor_data.message), "UV Sensor: flame not detected. Voltage = %f V",UV_A0);
                uv_sensor_data.sensor_triggered = false;
                uv_sensor_data.sensor_confirmed = false;
                                    printf("Here3\r]n");
                //printf("Flame not detected: Insufficient time.\n");
                uv_flame_detected = false;
            }

                         // Debug print to check the duration calculation
                        //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                        //printf("IR Comparison Duration2: %d ms\r\n", IR_Comparison_Duration);
            
        } 
        else if (UV_Logic_Mode == 1) { // Logic based on D0 (digital output)
            // Read the current IR_D0 value (digital read from the D0 pin)
            UV_D0 = IRSensor_calculate_A0(UV_D0_CalibrationOutcome, TestingParameters.adc1_handle, UV_D0_Handle_Channel, UV_D0_Channel, 1 + TestingParameters.init_config1.unit_id);

            // Check if IR_D0 value falls within the expected range to detect flame
            if (UV_D0 > (UV_VCC - UV_VCC * 0.01) && UV_D0 < (UV_VCC + UV_VCC * 0.01)) {
                //printf("Flame detected: IR wavelength detected (D0 mode).\n");
                snprintf(uv_sensor_data.message,sizeof(uv_sensor_data.message),"UV Sensor:wavelength de tected, D0 is in logical high. Voltage = %u V", UV_D0);
                uv_sensor_data.sensor_triggered = true;
            } else {
                //printf("Flame not detected: UV wavelength not detected (D0 mode).\n");
                snprintf(uv_sensor_data.message,sizeof(uv_sensor_data.message),"UV Sensor: flame not detected, D0 is in logical low. Voltage = %u V", UV_D0);
                uv_sensor_data.sensor_triggered = false;
            }
        } else {
            printf("Invalid UV Logic Mode.\n");
        }
        
            /*
            **********************************************************************************************************************************************************************************************************************8
            */

        printf("about to send to queue infos\r\n");
            // Send data to queue for app_main from gas sensor logic
        if (xQueueSend(gas_sensor_queue, &gas_sensor_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc1", "Failed to send gas data to queue");
            }
        
            // Send data to queue for app_main from ir sensor logic
        if (xQueueSend(ir_sensor_queue, &ir_sensor_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc1", "Failed to send ir data to queue");
            }

            // Send data to queue for app_main from uv sensor logic
        if (xQueueSend(uv_sensor_queue, &uv_sensor_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc1", "Failed to send uv data to queue");
            }
    }
//********************************************************************************************************************************************************************************************* */
    //delete ADC1: this delets hardware and software ONLY USE AT VERY END OF PROGRAM if hard reset is guaranteed; WILL CRASH PROGRAM; 
    ADC1_Delete_Port(TestingParameters.adc1_handle);
}


void TESTING_adc1(void* param)
{
    vTaskDelay(pdMS_TO_TICKS(Gas_HeatUP_time));
      ADC1_Ini_Parameters TestingParameters = ADC1_Port_calibration();
      adc_cali_handle_t Gas_A0_Handle_Channel = NULL;
          adc_cali_handle_t IR_A0_Handle_Channel = NULL;


        adc_cali_handle_t UV_A0_Handle_Channel = NULL;


    bool Gas_A0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters, Gas_A0_Channel, &Gas_A0_Handle_Channel);
    
        bool IR_A0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,IR_A0_Channel,&IR_A0_Handle_Channel);

    bool UV_A0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,UV_A0_Channel,&UV_A0_Handle_Channel);


    float Gas_R0 = calculate_R0(Gas_A0_CalibrationOutcome, TestingParameters.adc1_handle, Gas_A0_Handle_Channel, Gas_A0_Channel, 1+TestingParameters.init_config1.unit_id);//Calculate R0 for Gas Sensor

    float IR_V0 = IRSensor_Calibrate(IR_A0_CalibrationOutcome,TestingParameters.adc1_handle,IR_A0_Handle_Channel,IR_A0_Channel,1+TestingParameters.init_config1.unit_id);

    float UV_V0 = UVSensor_Calibrate(UV_A0_CalibrationOutcome,TestingParameters.adc1_handle,UV_A0_Handle_Channel,UV_A0_Channel,1+TestingParameters.init_config1.unit_id);


    //declare variables needed for gas sensor
    float Rs_R0 = 0;
    float ppm_level = 0;
    GasSensorData gas_sensor_data; //// Create a variable to hold data to send for gas sensor


    //declare variables needed for ir sensor
    float IR_A0 = 0;
    int IR_D0 = 0;
    bool IR_D0_CalibrationOutcome = false;
    int64_t ir_last_detection_time = 0;
    bool ir_flame_detected = false;
    int64_t IR_Comparison_Duration = 0;
    adc_cali_handle_t IR_D0_Handle_Channel = NULL;
    float IR_Percentage_Difference = 0.0;
    IRSensorData ir_sensor_data;

    //declare variables needed for uv sensor
    float UV_A0 = 0;
    int UV_D0 = 0;
    bool UV_D0_CalibrationOutcome = false;
    int64_t uv_last_detection_time = 0;
    bool uv_flame_detected = false;
    int64_t UV_Comparison_Duration = 0;
    adc_cali_handle_t UV_D0_Handle_Channel = NULL;
    float UV_Percentage_Difference = 0.0;
    IRSensorData uv_sensor_data;

    if (IR_Logic_Mode == 1) {//initialize IR_D0 in case D0 logic is used
        IR_D0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,IR_D0_Channel,&IR_D0_Handle_Channel);
    }

    if (UV_Logic_Mode == 1) {//initialize UV_D0 in case D0 logic is used
        UV_D0_CalibrationOutcome = ADC1_Channel_calibration(TestingParameters,UV_D0_Channel,&UV_D0_Handle_Channel);
    }


while (true) {
        Rs_R0 = calculate_RS_R0_ratio(Gas_R0, Gas_A0_CalibrationOutcome, TestingParameters.adc1_handle, Gas_A0_Handle_Channel, Gas_A0_Channel, 1 + TestingParameters.init_config1.unit_id);

        Rs_R0 = pow(10,Rs_R0);
        //printf("Rs/R0 logarithmic = %f\r\n",Rs_R0);
        
        // Calculate PPM Level, and status of Gas sensor
        if ((Rs_R0 >= 3) && (Rs_R0 < 5)) {
            ppm_level = calculate_ppm(Rs_R0, b_CO_yintercept, m_CO_logarithmic_slope);
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas detected: Gas Concentration = %.2f ppm", ppm_level);
            gas_sensor_data.sensor_triggered = true;
            gas_sensor_data.sensor_in_scope = true;
        } else if ((Rs_R0 >= 0.26) && (Rs_R0 < 3)) {
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas detected: Gas Concentration >= 200 ppm");
            gas_sensor_data.sensor_triggered = true;
            gas_sensor_data.sensor_in_scope = true;
        } else if ((Rs_R0 < 0.26) || (Rs_R0 >= 11.2)) {
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas detected out of scope");
            gas_sensor_data.sensor_triggered = true;
            gas_sensor_data.sensor_in_scope = false;
        } else {
            snprintf(gas_sensor_data.message, sizeof(gas_sensor_data.message), "Gas not detected");
            gas_sensor_data.sensor_triggered = false;
            gas_sensor_data.sensor_in_scope = true;
        }



        if (IR_Logic_Mode == 0) { // Logic based on A0
            // Read the current IR_A0 value (analog voltage level) 
            IR_A0 = IRSensor_calculate_A0(IR_A0_CalibrationOutcome, TestingParameters.adc1_handle, IR_A0_Handle_Channel, IR_A0_Channel, 1 + TestingParameters.init_config1.unit_id);
           //printf("A0 = %f\r\n",IR_A0);

    
            // Calculate the percentage difference between IR_A0 and the baseline IR_V0
            IR_Percentage_Difference = (float)(IR_V0 - IR_A0) / IR_V0;

            //printf("Percentage difference = %f\r\n",IR_Percentage_Difference);
           // printf("Percentage thresholdd = %f\r\n",IR_Percentage_Threshold);
            // Compare with the threshold to detect if there's flame (IR wavelength detected)
            //printf("IR Comparison Duration1: %lld ms\r\n", IR_Comparison_Duration);

            vTaskDelay(pdMS_TO_TICKS(2000));
            if ((IR_Percentage_Difference > IR_Percentage_Threshold) && (IR_Percentage_Difference > 0)) {
                if (ir_flame_detected == false) {
                    //printf("got in\r\n");
                    ir_last_detection_time = esp_timer_get_time();
                    ir_flame_detected = true;

                    //printf("Flame detected: IR wavelength detected.\n");
                    snprintf(ir_sensor_data.message, sizeof(ir_sensor_data.message), "IR Sensor:760-1100 nm wavelength detected. Voltage = %f V is below threshold",IR_A0);
                    ir_sensor_data.sensor_triggered = true;
                }
                

                else if (ir_flame_detected == true) {
                    IR_Comparison_Duration = (esp_timer_get_time() - ir_last_detection_time);
                    if (IR_Comparison_Duration >= IR_Comparison_Time) {
                        // Confirm flame detected if the condition holds for the duration
                        //printf("Flame confirmed: IR wavelength detected for %d ms.\n", IR_Comparison_Duration);
                        snprintf(ir_sensor_data.message, sizeof(ir_sensor_data.message), "IR Sensor confirmed: Voltage = %f V is below threshold for %lld s.", IR_A0, IR_Comparison_Duration/1000000);
                        ir_sensor_data.sensor_confirmed = true;    
                        IR_Comparison_Duration = 0;        
                    } 
                    //reset
                    //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                    //printf("IR Comparison Duration1: %d ms\r\n", IR_Comparison_Duration);
                 }



            } 
            
            else {
                snprintf(ir_sensor_data.message, sizeof(ir_sensor_data.message), "IR Sensor: flame not detected. Voltage = %f V",IR_A0);
                ir_sensor_data.sensor_triggered = false;
                ir_sensor_data.sensor_confirmed = false;
                                    printf("Here3\r]n");
                //printf("Flame not detected: Insufficient time.\n");
                ir_flame_detected = false;
            }

                         // Debug print to check the duration calculation
                        //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                        //printf("IR Comparison Duration2: %d ms\r\n", IR_Comparison_Duration);
            
        }  else if (IR_Logic_Mode == 1) { // Logic based on D0 (digital output)
            // Read the current IR_D0 value (digital read from the D0 pin)
            IR_D0 = IRSensor_calculate_A0(IR_D0_CalibrationOutcome, TestingParameters.adc1_handle, IR_D0_Handle_Channel, IR_D0_Channel, 1 + TestingParameters.init_config1.unit_id);

            // Check if IR_D0 value falls within the expected range to detect flame
            if (IR_D0 > (IR_VCC - IR_VCC * 0.01) && IR_D0 < (IR_VCC + IR_VCC * 0.01)) {
                //printf("Flame detected: IR wavelength detected (D0 mode).\n");
                snprintf(ir_sensor_data.message,sizeof(ir_sensor_data.message),"IR Sensor: 760-1100 nm wavelength detected, D0 is in logical high. Voltage = %u V", IR_D0);
                ir_sensor_data.sensor_triggered = true;
            } else {
                //printf("Flame not detected: IR wavelength not detected (D0 mode).\n");
                snprintf(ir_sensor_data.message,sizeof(ir_sensor_data.message),"IR Sensor: flame not detected, D0 is in logical low. Voltage = %u V", IR_D0);
                ir_sensor_data.sensor_triggered = false;
            }
        } else {
            printf("Invalid IR Logic Mode.\n");
        }

        if (UV_Logic_Mode == 0) { // Logic based on A0
            // Read the current IR_A0 value (analog voltage level) 
            UV_A0 = UVSensor_calculate_A0(UV_A0_CalibrationOutcome, TestingParameters.adc1_handle, UV_A0_Handle_Channel, UV_A0_Channel, 1 + TestingParameters.init_config1.unit_id);
       

    
            // Calculate the percentage difference between IR_A0 and the baseline IR_V0
            UV_Percentage_Difference = (float)(UV_V0 - UV_A0) / UV_V0;

            //printf("Percentage difference = %f\r\n",UV_Percentage_Difference);
            //printf("Percentage thresholdd = %f\r\n",UV_Percentage_Threshold);
            // Compare with the threshold to detect if there's flame (IR wavelength detected)
            //printf("UV Comparison Duration1: %lld ms\r\n", UV_Comparison_Duration);

            vTaskDelay(pdMS_TO_TICKS(2000));
            if ((UV_Percentage_Difference > UV_Percentage_Threshold) && (UV_Percentage_Difference > 0)) {
                if (uv_flame_detected == false) {
                    //printf("got in\r\n");
                    uv_last_detection_time = esp_timer_get_time();
                    uv_flame_detected = true;

                    //printf("Flame detected: IR wavelength detected.\n");
                    snprintf(uv_sensor_data.message, sizeof(uv_sensor_data.message), "UV Sensor:760-1100 nm wavelength detected. Voltage = %f V is below threshold",UV_A0);
                    uv_sensor_data.sensor_triggered = true;
                }
                

                else if (uv_flame_detected == true) {
                    UV_Comparison_Duration = (esp_timer_get_time() - uv_last_detection_time);
                    if (UV_Comparison_Duration >= UV_Comparison_Time) {
                        // Confirm flame detected if the condition holds for the duration
                        //printf("Flame confirmed: IR wavelength detected for %d ms.\n", IR_Comparison_Duration);
                        snprintf(uv_sensor_data.message, sizeof(uv_sensor_data.message), "UV Sensor confirmed: Voltage = %f V is below threshold for %lld s.", UV_A0, UV_Comparison_Duration/1000000);
                        uv_sensor_data.sensor_confirmed = true;    
                        UV_Comparison_Duration = 0;        
                    } 
                    //reset
                    //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                    //printf("IR Comparison Duration1: %d ms\r\n", IR_Comparison_Duration);
                 }



            } 
            
            else {
                snprintf(uv_sensor_data.message, sizeof(uv_sensor_data.message), "UV Sensor: flame not detected. Voltage = %f V",UV_A0);
                uv_sensor_data.sensor_triggered = false;
                uv_sensor_data.sensor_confirmed = false;
                                    //printf("Here3\r]n");
                //printf("Flame not detected: Insufficient time.\n");
                uv_flame_detected = false;
            }

                         // Debug print to check the duration calculation
                        //printf("IR_Comparison_Time = %u\r\n",IR_Comparison_Time);
                        //printf("IR Comparison Duration2: %d ms\r\n", IR_Comparison_Duration);
            
        } 
        else if (UV_Logic_Mode == 1) { // Logic based on D0 (digital output)
            // Read the current IR_D0 value (digital read from the D0 pin)
            UV_D0 = IRSensor_calculate_A0(UV_D0_CalibrationOutcome, TestingParameters.adc1_handle, UV_D0_Handle_Channel, UV_D0_Channel, 1 + TestingParameters.init_config1.unit_id);

            // Check if IR_D0 value falls within the expected range to detect flame
            if (UV_D0 > (UV_VCC - UV_VCC * 0.01) && UV_D0 < (UV_VCC + UV_VCC * 0.01)) {
                //printf("Flame detected: IR wavelength detected (D0 mode).\n");
                snprintf(uv_sensor_data.message,sizeof(uv_sensor_data.message),"UV Sensor:wavelength de tected, D0 is in logical high. Voltage = %u V", UV_D0);
                uv_sensor_data.sensor_triggered = true;
            } else {
                //printf("Flame not detected: UV wavelength not detected (D0 mode).\n");
                snprintf(uv_sensor_data.message,sizeof(uv_sensor_data.message),"UV Sensor: flame not detected, D0 is in logical low. Voltage = %u V", UV_D0);
                uv_sensor_data.sensor_triggered = false;
            }
        } else {
            printf("Invalid UV Logic Mode.\n");
        }






        //printf("about to send to queue infos\r\n");
            // Send data to queue for app_main from gas sensor logic
        if (xQueueSend(gas_sensor_queue, &gas_sensor_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc1", "Failed to send gas data to queue");
            }

        if (xQueueSend(ir_sensor_queue, &ir_sensor_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc1", "Failed to send ir data to queue");
            }

        if (xQueueSend(uv_sensor_queue, &uv_sensor_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc1", "Failed to send uv data to queue");
            }
}

}


