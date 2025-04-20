idf_component_register(SRCS "ADC2.c"
                    INCLUDE_DIRS "include"
                    REQUIRES esp_adc backup_battery_level)
