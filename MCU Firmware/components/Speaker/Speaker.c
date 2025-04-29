#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"

#include "esp_timer.h"

#define BUZZER_GPIO GPIO_NUM_2  // Define GPIO pin for buzzer

static bool alarm_active = false;  // Variable to track if alarm is active

// Function to set up the GPIO for the buzzer
void setup_pwm()
{
    gpio_reset_pin(BUZZER_GPIO);  // Reset the pin to its default state
    gpio_set_direction(BUZZER_GPIO, GPIO_MODE_OUTPUT);  // Set the pin as output
}

// Function to start the buzzer (turn it on)
void start_alarm()
{
    gpio_set_level(BUZZER_GPIO, 1);  // Set the buzzer GPIO pin high (on)
}

// Function to stop the buzzer (turn it off)
void stop_alarm()
{
    gpio_set_level(BUZZER_GPIO, 0);  // Set the buzzer GPIO pin low (off)
}

// Task to sound the speaker in a repeating on/off pattern
void soundSpeaker(void *param)
{
    while (alarm_active) {  // While alarm is active, keep buzzing
        start_alarm();  // Turn buzzer on
        vTaskDelay(pdMS_TO_TICKS(500));  // 500ms ON time
        stop_alarm();  // Turn buzzer off
        vTaskDelay(pdMS_TO_TICKS(500));  // 500ms OFF time
    }
    stop_alarm();  // Ensure the buzzer is turned off when exiting the loop
    vTaskDelete(NULL);  // Delete the task once the alarm is stopped
}

// Function to start the buzzer task
void trigger_alarm()
{
    if (!alarm_active) {  // If the alarm is not already active
        alarm_active = true;  // Set alarm active
        xTaskCreate(soundSpeaker, "soundSpeaker", 3072, NULL, 5, NULL);  // Create a FreeRTOS task to run soundSpeaker
    }
}

// Function to stop the buzzer task
void disable_alarm()
{
    alarm_active = false;  // Set alarm inactive, stopping the buzzer
}
