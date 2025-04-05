#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"

#define BUZZER_GPIO GPIO_NUM_2

static bool alarm_active = false;

void setup_pwm()
{
    gpio_reset_pin(BUZZER_GPIO);
    gpio_set_direction(BUZZER_GPIO, GPIO_MODE_OUTPUT);
}

void start_alarm()
{
    gpio_set_level(BUZZER_GPIO, 1);  // Turn buzzer ON
    //ESP_LOGI("BUZZER", "Buzzer ON");
}

void stop_alarm()
{
    gpio_set_level(BUZZER_GPIO, 0);  // Turn buzzer OFF
    //ESP_LOGI("BUZZER", "Buzzer OFF");
}

void soundSpeaker(void *param)
{
    while (alarm_active) {
        start_alarm();
        vTaskDelay(pdMS_TO_TICKS(500));  // 500ms ON
        stop_alarm();
        vTaskDelay(pdMS_TO_TICKS(500));  // 500ms OFF


        //printf("Remaining stack: %d\r\n",uxTaskGetStackHighWaterMark(NULL));
    }
    stop_alarm(); // Ensure it turns off when loop exits
    vTaskDelete(NULL); // Delete task when done
}

// Function to start the buzzer task
void trigger_alarm()
{
    if (!alarm_active) {
        alarm_active = true;
        xTaskCreate(soundSpeaker, "soundSpeaker", 3072, NULL, 5, NULL);
    }
}

// Function to stop the buzzer task
void disable_alarm()
{
    alarm_active = false;
} 