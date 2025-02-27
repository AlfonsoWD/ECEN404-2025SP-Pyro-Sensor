#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "string.h"
#include "driver/ledc.h"
#include "esp_err.h"
#include "esp_log.h"
#include "Speaker.h"

#define BUZZER_GPIO GPIO_NUM_2

void setup_pwm()
{
    gpio_reset_pin(BUZZER_GPIO);
    gpio_set_direction(BUZZER_GPIO, GPIO_MODE_OUTPUT);
}

void start_alarm()
{
    gpio_set_level(BUZZER_GPIO, 1);  // Turn buzzer ON
    ESP_LOGI("start_alarm", "Buzzer ON");
}

void stop_alarm()
{
    gpio_set_level(BUZZER_GPIO, 0);  // Turn buzzer OFF
    ESP_LOGI("stop_alarm", "Buzzer OFF");
}

void soundSpeaker(bool do_soundspeaker) { 
    if (do_soundspeaker) {
        for (int i = 0; i < 3; i++) {  // Beep 3 times
            start_alarm();
            vTaskDelay(pdMS_TO_TICKS(500));  // 500ms ON
            stop_alarm();
            vTaskDelay(pdMS_TO_TICKS(500));  // 500ms OFF
        }
        vTaskDelay(pdMS_TO_TICKS(1500));  // Wait 1.5 seconds before repeating
    } else {
        stop_alarm();
}
}