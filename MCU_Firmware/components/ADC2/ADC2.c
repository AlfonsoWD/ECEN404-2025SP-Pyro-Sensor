#include "ADC2.h"
#include "backup_battery_level.h"

#define EXAMPLE_ADC_ATTEN2 ADC_ATTEN_DB_12
//TODO: max voltage adc can read is 3.1V, change voltage divider ratio for input pin Battery_A0_Channel
#define Battery_A0_Channel ADC_CHANNEL_0 //gpio 11 
#define Battery_Wait_Duration 1000//duration time (in ms)between every battery level check
#define Battery_Standard 9.8 //Backup battery should be at 9V, 9.8 V for a brand new battery
#define Battery_threshold_voltage 7//voltage at which backup battery is considered dead

extern QueueHandle_t battery_queue;

ADC2_Ini_Parameters ADC2_Port_calibration() {
    ADC2_Ini_Parameters ADC2_start;
     ADC2_start.init_config2.unit_id = ADC_UNIT_2;
    ADC2_start.init_config2.clk_src = 0;
    ESP_ERROR_CHECK(adc_oneshot_new_unit(&ADC2_start.init_config2, &ADC2_start.adc2_handle)); //might need a pointer for address of &ADC2_start.adc1handle
    ADC2_start.config2.bitwidth = ADC_BITWIDTH_DEFAULT;
    ADC2_start.config2.atten = EXAMPLE_ADC_ATTEN2;
    return ADC2_start;
}

static bool ADC2_Channel_calibration(ADC2_Ini_Parameters Channel_Parameters, int Channel_Number, adc_cali_handle_t *out_handle) {
    ESP_ERROR_CHECK(adc_oneshot_config_channel(Channel_Parameters.adc2_handle, Channel_Number, &Channel_Parameters.config2)); //might need a pointer for address of &ADC2_start.adc2handle

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

static void ADC2_Channel_decalibration(adc_cali_handle_t handle) {
    #if ADC_CALI_SCHEME_CURVE_FITTING_SUPPORTED
        ESP_LOGI("ADC2_Channel_decalibration", "deregister %s calibration scheme", "Curve Fitting");
        ESP_ERROR_CHECK(adc_cali_delete_scheme_curve_fitting(handle));

    #elif ADC_CALI_SCHEME_LINE_FITTING_SUPPORTED
        ESP_LOGI("ADC2_Channel_decalibration", "deregister %s calibration scheme", "Line Fitting");
        ESP_ERROR_CHECK(adc_cali_delete_scheme_line_fitting(handle));
    #endif   
}

int read_voltage2(bool CalibrationStatus, adc_oneshot_unit_handle_t Port_Handle,adc_cali_handle_t Channel_Handle,int Channel_Number, int Port_number) {
    int voltage[1];
    int adc_raw[1];

    ESP_ERROR_CHECK(adc_oneshot_read(Port_Handle, Channel_Number, &adc_raw[0]));
    if (CalibrationStatus) {
        ESP_ERROR_CHECK(adc_cali_raw_to_voltage(Channel_Handle, adc_raw[0], &voltage[0]));
        //ESP_LOGI("read_voltage2()", "ADC%d Channel[%d] Cali Voltage: %d mV", Port_number, Channel_Number, voltage[0]);
    }
    else {
        //ESP_LOGE("read_voltage2()", "CalibrationStatus == False");
    }  
    //printf("voltage read = %u\r\n",voltage[0]);
    return voltage[0];
}

static void ADC2_Delete_Port(adc_oneshot_unit_handle_t Port_Handle) {
    ESP_ERROR_CHECK(adc_oneshot_del_unit(Port_Handle));
}

//Below code calculates the backup battery voltage every Battery_Wait_Duration period
void run_adc2(void* param)
{
    //initialize ADC2 Unit/Port
    ADC2_Ini_Parameters TestingParameters2 = ADC2_Port_calibration();

    //initialize ADC2 Channel0
    adc_cali_handle_t Battery_Handle_Channel2 = NULL;
    
    //calibration channel
    bool Battery_CalibrationOutcome = ADC2_Channel_calibration(TestingParameters2, Battery_A0_Channel, &Battery_Handle_Channel2);

    //initialize voltage needed by loop
    float BatteryVoltage = 0.0;
    BatteryData battery_data;


    while(true){


        BatteryVoltage = calculate_BatteryVoltage(Battery_CalibrationOutcome,TestingParameters2.adc2_handle,Battery_Handle_Channel2,Battery_A0_Channel,1+TestingParameters2.init_config2.unit_id);
       // printf("BatteryVoltage = %f V\r\n",BatteryVoltage);

        //if backup battery is below threshold, send dead battery status to queue
        if (BatteryVoltage < Battery_threshold_voltage) {//max threshold_voltage
            battery_data.battery_good = false;
            snprintf(battery_data.message, sizeof(battery_data.message), "Low battery level. Replace 9V battery.");
        }
        else if ((BatteryVoltage >= Battery_threshold_voltage) && (BatteryVoltage <= Battery_Standard)) {
            battery_data.battery_good = true;
            snprintf(battery_data.message, sizeof(battery_data.message), "Battery level within acceptable range.");
        }
        else {
            battery_data.battery_good = false;
            snprintf(battery_data.message, sizeof(battery_data.message), "Battery level is over-limit. Replace with a 9V battery.");
        }



        // Send data to queue for app_main from battery level sensor logic
        if (xQueueSend(battery_queue, &battery_data, (TickType_t)0) != pdPASS) {
            ESP_LOGE("run_adc2", "Failed to send backup battery data to queue");
        }



        vTaskDelay(pdMS_TO_TICKS(Battery_Wait_Duration));
    }



    //delete channels
    if (Battery_CalibrationOutcome) {
        ADC2_Channel_decalibration(Battery_Handle_Channel2);
    }

    //delete ADC1: this delets hardware and software ONLY USE AT VERY END OF PROGRAM if hard reset is guaranteed; WILL CRASH PROGRAM; 
    ADC2_Delete_Port(TestingParameters2.adc2_handle);
}