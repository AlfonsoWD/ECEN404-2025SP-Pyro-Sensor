idf_component_register(SRCS "ADC1.c"
                    INCLUDE_DIRS "include"
                    REQUIRES esp_adc SRAQ_Gas_Sensor IR_Sensor UV_Sensor esp_timer)
