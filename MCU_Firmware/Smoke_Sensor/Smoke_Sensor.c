//TO DO: SEND STATUS SIGNALS TO MAIN

/* Includes ------------------------------------------------------------------*/
#include <stdio.h>
#include "sdkconfig.h"
#include "Smoke_Sensor.h"
#include "esp_err.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "inttypes.h"
#include "freertos/FreeRTOS.h"

#define SCL_GPIO_PIN 47
#define SDA_GPIO_PIN 21
#define INTERRUPT_GPIO_PIN 48
#define PORT_NUMBER -1
#define ALARM_THRESHOLD 0.06 //ALARM_THRESHOLD = (BLUE/IR RATIO UNDER CLEAN AIR) - (BLUE/IR RATIO UNDER SMOKE)
                       //increase this value if smoke sensor triggers too many false alarms
#define WARNING_THRESHOLD 0.04 //WARNING_THRESHOLD for nuisance sources (e.g., candle smoke)
#define STANDBY_SCAN_PERIOD 500 //period (in milliseconds (ms)) between standby scannings (total would be this value + vTaskDelay()). Increase this value for watchdog errors
#define MAX_CONSECUTIVE_HIGH 1 //number of potential smoke scannings before sending confirmation of smoke to main
#define MAX_CONSECUTIVE_HIGH_EXTREME 30
/*------------------------------------------------------------------

*From Blue/IR ratio observations:
    *around 0.638-0.65 in air
    *around 0.68  for candle smoke
    *0.58 for breath
    *0.638 for lotion
    *under paper smoke it fluctuated between values of 0.7-1
    *it goes to 0.51 under steam and slowy reaches to its air value after removing steam

------------------------------------------------------------------*/


extern QueueHandle_t smoke_sensor_queue;
extern volatile bool Smoke_Reset_Request;
extern volatile bool Delete_Tasks;

/* Private macros ------------------------------------------------------------*/
#define NOP() asm volatile ("nop")

/* External variables --------------------------------------------------------*/

/* Private typedef -----------------------------------------------------------*/

/* Private variables ---------------------------------------------------------*/
static const char *TAG = "adpd188bi";


/* Private function prototypes -----------------------------------------------*/
static int8_t i2c_read(uint8_t reg_addr, uint16_t *reg_data,
		void *intf);
static int8_t i2c_write(uint8_t reg_addr, const uint16_t reg_data,
		void *intf);
static bool set_n_bits(uint16_t *target, uint8_t n, uint16_t val,
		uint8_t index);
static bool get_n_bits(uint16_t target, uint8_t n, uint16_t *val,
		uint8_t index);

static void print_binary(uint16_t val);
static void print_test(uint16_t bits_val);

/**
 * @brief Function that implements a micro seconds delay
 *
 * @param period_us: Time in us to delay
 */
static void delay_us(uint32_t period_us);

/* Exported functions definitions --------------------------------------------*/
/**
 * @brief Function to initialize a ADPD188 instance.
 */
esp_err_t adpd188bi_init(adpd188bi_t *const me, i2c_master_bus_handle_t i2c_bus_handle,
	uint8_t dev_addr, int int_gpio, bool Reset) {
	esp_err_t ret = ESP_OK;

	bool on_boot_up_setup = false;

if (Reset == false) {
	on_boot_up_setup = true;
	/* Print initializing message */
	ESP_LOGI(TAG, "Initializing instance...");

	/* Variable to return error code */
	ret = ESP_OK;

	/* Add device to I2C bus */
	i2c_device_config_t i2c_dev_conf = {
			.scl_speed_hz = 400000,
			.device_address = dev_addr
	};

	if (i2c_master_bus_add_device(i2c_bus_handle, &i2c_dev_conf, &me->i2c_dev) != ESP_OK) {
		ESP_LOGE(TAG, "Failed to add device to I2C bus");
		return ret;
	}

	/* Print successful initialization message */
	ESP_LOGI(TAG, "Instance initialized successfully");

	/* Initialize interrupt GPIO */
	me->int_gpio = int_gpio;

	gpio_config_t gpio_conf = {
			.pin_bit_mask = (1ULL << me->int_gpio),
			.mode = GPIO_MODE_INPUT,
			.pull_up_en = GPIO_PULLUP_ENABLE,
			.pull_down_en = GPIO_PULLDOWN_DISABLE,
			.intr_type = GPIO_INTR_ANYEDGE
	};

	ret = gpio_config(&gpio_conf);


	if (ret != ESP_OK) {
		ESP_LOGE(TAG, "Failed to configure int GPIO");
		return ret;
	}

}

if ((Reset == true) || (on_boot_up_setup == true)) {

	/*  */
	uint16_t mode_status_read;

	i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);

	adpd188bi_soft_reset(me);

	i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);


	adpd188bi_set_mode(me, ADPD188BI_MODE_IDLE);


	i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);

	uint16_t devid;

	i2c_read(ADPD188BI_REG_DEVID, &devid, me->i2c_dev);
	printf("ID: 0x%.2X\r\n", (uint16_t)(devid  & 0xFF));
	printf("Rev: 0x%.2X\r\n", (uint16_t)((devid >> 8) & 0xFF));


	adpd188bi_set_bit(me, ADPD188BI_REG_SAMPLE_CLK, 7, 1);


	adpd188bi_set_mode(me, ADPD188BI_MODE_PROGRAM);//set device to programming mode

		
		i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);

	adpd188bi_set_bit(me, ADPD188BI_REG_STATUS, 15, 1);//clear the fifo

	i2c_write(ADPD188BI_REG_SLOT_EN, 0x30A9, me->i2c_dev);
	i2c_write(ADPD188BI_REG_FSAMPLE, 0x0200, me->i2c_dev);
	i2c_write(ADPD188BI_REG_PD_LED_SELECT, 0x011D, me->i2c_dev); //Infrared LED scannings on time slot B, and blue LED (LED 1)scannings on time slot A
	i2c_write(ADPD188BI_REG_NUM_AVG, 0x0000, me->i2c_dev);
	i2c_write(ADPD188BI_REG_INT_SEQ_A, 0x0009, me->i2c_dev);

	i2c_write(ADPD188BI_REG_SLOTA_CH1_OFFSET, 0x0000, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_CH2_OFFSET, 0x3FFF, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_CH3_OFFSET, 0x3FFF, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_CH4_OFFSET, 0x3FFF, me->i2c_dev);
	i2c_write(ADPD188BI_REG_INT_SEQ_B, 0x0009, me->i2c_dev);

	i2c_write(ADPD188BI_REG_SLOTB_CH1_OFFSET, 0x0000, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_CH2_OFFSET, 0x3FFF, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_CH3_OFFSET, 0x3FFF, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_CH4_OFFSET, 0x3FFF, me->i2c_dev);
	i2c_write(ADPD188BI_REG_ILED3_COARSE, 0x3539, me->i2c_dev);
	i2c_write(ADPD188BI_REG_ILED1_COARSE, 0x3536, me->i2c_dev);
	i2c_write(ADPD188BI_REG_ILED2_COARSE, 0x1530, me->i2c_dev);
	i2c_write(ADPD188BI_REG_ILED_FINE, 0x630C, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_LED_PULSE, 0x0320, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_NUM_PULSES, 0x040E, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_LED_PULSE, 0x0320, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_NUM_PULSES, 0x040E, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_AFE_WINDOW, 0x22F0, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_AFE_WINDOW, 0x22F0, me->i2c_dev);
	i2c_write(ADPD188BI_REG_AFE_PWR_CFG1, 0x31C6, me->i2c_dev);

	i2c_write(ADPD188BI_REG_SLOTA_TIA_CFG, 0x1C34, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTA_AFE_CFG, 0xADA5, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_TIA_CFG, 0x1C34, me->i2c_dev);
	i2c_write(ADPD188BI_REG_SLOTB_AFE_CFG, 0xADA5, me->i2c_dev);
	i2c_write(ADPD188BI_REG_MATH, 0x0544, me->i2c_dev);
	i2c_write(ADPD188BI_REG_AFE_PWR_CFG2, 0x0AA0, me->i2c_dev);

	adpd188bi_set_mode(me, ADPD188BI_MODE_NORMAL);
	i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);
	/* Return ESP_OK */
	}
return ret;
}

/**
 * @brief Function to set the working mode of the ADPD188
 */
esp_err_t adpd188bi_set_mode(adpd188bi_t *const me, adpd188bi_mode_e mode) {
	esp_err_t ret = ESP_OK;

	adpd188bi_set_bit_mask(me, ADPD188BI_REG_MODE, 2, (uint16_t)mode);

	/* Return ESP_OK */
	return ret;
}


/**
 * @brief Function to set the working mode of the ADPD188
 */
esp_err_t adpd188bi_get_mode(adpd188bi_t *const me, adpd188bi_mode_e *mode) {
	esp_err_t ret = ESP_OK;

	i2c_read(ADPD188BI_REG_MODE, (uint16_t *)&mode, me->i2c_dev);

	/* Return ESP_OK */
	return ret;
}

/**
 * @brief Function to perform software reset
 */
esp_err_t adpd188bi_soft_reset(adpd188bi_t *const me) {
	esp_err_t ret = ESP_OK;

	i2c_write(ADPD188BI_REG_SW_RESET, 0x0001, me->i2c_dev);

	delay_us(100 * 1000);

	/* Return ESP_OK */
	return ret;
}

/**
 * @brief Function to perform a software reset todo: write correctly
 */
esp_err_t adpd188bi_get_int(adpd188bi_t *const me, uint8_t *fifo, uint8_t *slot_a,
		uint8_t *slot_b) {
	esp_err_t ret = ESP_OK;
	uint16_t reg_val = 0;

	i2c_read(ADPD188BI_REG_STATUS, &reg_val, me->i2c_dev);
	print_test(reg_val);

  *fifo = (reg_val >> 8) & 0xFF;
  *slot_a = (reg_val >> 5) & 0x01;
  *slot_b = (reg_val >> 6) & 0x01;

  i2c_write(ADPD188BI_REG_STATUS, 0xFFFF, me->i2c_dev);
  i2c_read(ADPD188BI_REG_STATUS, &reg_val, me->i2c_dev);

	/* Return ESP_OK */
	return ret;
}

/**
 * @brief Function to perform a software reset todo: write correctly
 */
esp_err_t adpd188bi_read_sens_data(adpd188bi_t *const me, uint8_t slot, uint8_t ch,
		uint16_t *data) {
	esp_err_t ret = ESP_OK;

	/**/
	adpd188bi_set_bit(me, ADPD188BI_REG_DATA_ACCESS_CTL, 1 + slot, 1);
	i2c_read(ADPD188BI_REG_SLOTA_CH1 + slot + ch, data, me->i2c_dev);
	adpd188bi_set_bit(me, ADPD188BI_REG_DATA_ACCESS_CTL, 1 + slot, 0);

	/* Return ESP_OK */
	return ret;
}

/**
 * @brief Function to perform a software reset todo: write correctly
 */
esp_err_t adpd188bi_set_bit(adpd188bi_t *const me, uint8_t reg_addr, uint8_t bit_num,
		bool bit_val) {
	esp_err_t ret = ESP_OK;
	uint16_t reg_data = 0x0;

	// Log the register address, bit number, and bit value


	// Read data register
	i2c_read(reg_addr, &reg_data, me->i2c_dev);

	// Set the specified bit number and update the value
	set_n_bits(&reg_data, 1, bit_val, bit_num);

	// Write back the updated register value
	i2c_write(reg_addr, reg_data, me->i2c_dev);

	// Return ESP_OK
	return ret;
}


/**
 * @brief Function to perform a software reset todo: write correctly
 */
esp_err_t adpd188bi_set_bit_mask(adpd188bi_t *const me, uint8_t reg_addr,
		uint8_t bits_num,	uint16_t bits_val) {
	esp_err_t ret = ESP_OK;
	uint16_t reg_data = 0x0;

	/* Read data register, set the specified bit number and write the new value */
	i2c_read(reg_addr, &reg_data, me->i2c_dev);
	set_n_bits(&reg_data, bits_num, bits_val, 0);
	i2c_write(reg_addr, reg_data, me->i2c_dev);

	/* Return ESP_OK */
	return ret;
}

/**
 * @brief Function to perform a software reset todo: write correctly
 */
esp_err_t adpd188bi_calibration(adpd188bi_t *const me, uint16_t threshold) {
	esp_err_t ret = ESP_OK;

	me->threshold_value = threshold;

	i2c_read(ADPD188BI_REG_SLOT_EN, &me->enabled_slot , me->i2c_dev);

	if ((me->enabled_slot  & 0x0001) == 0x0001) {
		me->enabled_slot = ADPD188BI_SLOT_A;
	}
	else if ((me->enabled_slot  & 0x0020) == 0x0020) {
		me->enabled_slot = ADPD188BI_SLOT_B;
	}

	/* Read data for calibration */
	uint32_t sum = 0;
	uint16_t reg_data = 0x0;
	uint8_t cnt = 0;

	while (cnt < 128) {
		if (!adpd188bi_get_int_gpio(me)) {
			adpd188bi_read_sens_data(me, me->enabled_slot, ADPD188BI_CH_1, &reg_data);
			if (reg_data != 0) {
				cnt++;
				sum += reg_data;
			}
		}
	}

	me->calib_value = sum >> 7;

	/* Return ESP_OK*/
	return ret;
}



/**
 * @brief Function to perform a software reset todo: write correctly
 */
uint16_t adpd188bi_get_calib(adpd188bi_t *const me) {
	return me->calib_value;
}

/**
 * @brief Function to perform a software reset todo: write correctly
 */
esp_err_t adpd188bi_check_smoke(adpd188bi_t *const me, adpd188bi_smoke_e *smoke) {
	esp_err_t ret = ESP_OK;

	/**/
	if (!adpd188bi_get_int_gpio(me)) {
		uint16_t reg_data = 0x0;
		adpd188bi_read_sens_data(me, me->enabled_slot, ADPD188BI_CH_1, &reg_data);


		if (reg_data > (me->calib_value + me->threshold_value)){
			*smoke = ADPD188BI_SMOKE_DETECTED;
		}
		else {
			*smoke = ADPD188BI_SMOKE_NOT_DETECTED;
		}

		return ret;
	}

	*smoke = ADPD188BI_SMOKE_ERROR;

	/* Return ESP_OK */
	return ret;
}

/**
 * @brief Function to perform a software reset todo: write correctly
 */
int adpd188bi_get_int_gpio(adpd188bi_t *const me) {
	return gpio_get_level(me->int_gpio);
}

/* Private function definitions ----------------------------------------------*/
static int8_t i2c_read(uint8_t reg_addr, uint16_t *reg_data, void *intf) {
    i2c_master_dev_handle_t i2c_dev = (i2c_master_dev_handle_t)intf;

    uint8_t buffer[2] = {0};

    // Log the I2C device handle to check its status
    if (i2c_dev == NULL) {
        return -1;  // Return error if i2c_dev is NULL
    }


    // Perform I2C transaction
    esp_err_t ret = i2c_master_transmit_receive(i2c_dev, &reg_addr, 1, buffer, 2, -1);
    if (ret != ESP_OK) {
        return -1; // Return error if transaction failed
    }

    // Combine the received bytes into a 16-bit register value
    *reg_data = (uint16_t)((buffer[0] << 8) | buffer[1]);

    // Log the received data

    return 0;
}



static int8_t i2c_write(uint8_t reg_addr, const uint16_t reg_data, void *intf) {
	i2c_master_dev_handle_t i2c_dev = (i2c_master_dev_handle_t)intf;

	uint8_t buffer[32] = {0};

	/* Copy the register address to buffer */
	uint8_t addr_len = sizeof(reg_addr);

	for (uint8_t i = 0; i < addr_len; i++) {
		buffer[i] = (reg_addr & (0xFF << ((addr_len - 1 - i) * 8))) >> ((addr_len - 1 - i) * 8);
	}

	/* Copy the data to buffer */
	uint8_t data_len = sizeof(reg_data);

	for (uint8_t i = 0; i < data_len; i++) {
		buffer[i + addr_len] = (reg_data & (0xFF << ((data_len - 1 - i) * 8))) >> ((data_len - 1 - i) * 8);
	}

	/* Transmit buffer */
	if (i2c_master_transmit(i2c_dev, buffer, addr_len + data_len, -1) != ESP_OK) {
		return -1;
	}

	return 0;
}

static bool set_n_bits(uint16_t *target, uint8_t n, uint16_t val,
		uint8_t index) {
	/* Check for a valid index value */
	if (index > (16 - n) || n < 1) {
		printf("Index out of bounds or less than 1\r\n");
		return false;
	}

	/* Create the mask */
	uint16_t mask = 0;

	if (n == 16) {
		mask = ~mask;
	}
	else {
		mask = (0x1 << n) - 1;
	}

	/* Write the bits_val */
	*target = (*target & ~(mask << index)) | ((val & mask) << index);

	return true;
}

/**
 * @brief Function to read n bits_val for an uint32_t variable in a specific index
 */
static bool get_n_bits(uint16_t target, uint8_t n, uint16_t *val,
		uint8_t index) {
	/* Check for a valid index value */
	if (index > (16 - n) || n < 1) {
		printf("Index out of bounds or less than 1\r\n");
		return false;
	}

	/* Create the mask */
	uint16_t mask = 0;

	if (n == 16) {
		mask = ~mask;
	}
	else {
		mask = (0x1 << n) - 1;
	}

	/* Read the bits_val */
	*val = (target >> index) & mask;

	return true;
}

static void print_binary(uint16_t val) {
	for (int i = 15; i >= 0; i--) {
        if (i > 9) {
            printf(" ");
        }

		printf("%d ", val & (0x1 << i) ? 1 : 0);
	}
	printf("\r\n");
}

static void print_test(uint16_t bits_val) {
    print_binary(bits_val);

    for (int i = 15; i >= 0; i--) {
        printf("%d ", i);
    }

    printf("\n");
}

/**
 * @brief Function that implements a micro seconds delay
 */
static void delay_us(uint32_t period_us) {
	uint64_t m = (uint64_t)esp_timer_get_time();

  if (period_us) {
  	uint64_t e = (m + period_us);

  	if (m > e) { /* overflow */
  		while ((uint64_t)esp_timer_get_time() > e) {
  			NOP();
  		}
  	}

  	while ((uint64_t)esp_timer_get_time() < e) {
  		NOP();
  	}
  }
}

float get_BLUE_OVER_IR_ratio_clean(adpd188bi_t *const device) {
    uint16_t Low_Holder;
    uint16_t High_Holder;
    uint16_t Data_Holder;
    uint32_t Full_HolderA;
    uint32_t Full_HolderB;

    float sum = 0.0f;
    uint8_t cnt = 0;   
    
    while (cnt < 128) {
        vTaskDelay(pdMS_TO_TICKS(125));
        adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 1, 1); // Read into Time Slots A and B to calculate ratio
        adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 2, 1);
        i2c_read(ADPD188BI_REG_SLOTA_CH1, &Data_Holder, device->i2c_dev);
        
        if (Data_Holder == 65535) {
            i2c_read(ADPD188BI_REG_A_CH1_LOW, &Low_Holder, device->i2c_dev);
            i2c_read(ADPD188BI_REG_A_CH1_HIGH, &High_Holder, device->i2c_dev);
            Full_HolderA = ((uint32_t)High_Holder << 16) | Low_Holder;
        } else {
            Full_HolderA = Data_Holder;
        }
    
        i2c_read(ADPD188BI_REG_SLOTB_CH1, &Data_Holder, device->i2c_dev);
        if (Data_Holder == 65535) {
            i2c_read(ADPD188BI_REG_B_CH1_LOW, &Low_Holder, device->i2c_dev);
            i2c_read(ADPD188BI_REG_B_CH1_HIGH, &High_Holder, device->i2c_dev);
            Full_HolderB = ((uint32_t)High_Holder << 16) | Low_Holder;
        } else {
            Full_HolderB = Data_Holder;
        }
    
        adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 1, 0);
        adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 2, 0);
    
        float ratio = (float)Full_HolderA / (float)Full_HolderB;
        if (ratio != 0) {
            sum += ratio;
            cnt++;
        }
    }
    
    float ratio_clean = (cnt > 0) ? sum / (float)cnt : 0.0f;
    printf("Final sum: %f, ratio clean: %f\r\n", sum, ratio_clean);
    
    return ratio_clean;
}

float get_BLUE_OVER_IR_ratio_standby(adpd188bi_t *const device) {
    uint16_t Low_Holder;
    uint16_t High_Holder;
    uint16_t Data_Holder;
    uint32_t Full_HolderA;
    uint32_t Full_HolderB;

    float ratio = 0;
    
    // Perform a quick scan for smoke detection
    adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 1, 1);
    adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 2, 1);

    // Collect data from slot A and B
    i2c_read(ADPD188BI_REG_SLOTA_CH1, &Data_Holder, device->i2c_dev);
    if (Data_Holder == 65535) {
        i2c_read(ADPD188BI_REG_A_CH1_LOW, &Low_Holder, device->i2c_dev);
        i2c_read(ADPD188BI_REG_A_CH1_HIGH, &High_Holder, device->i2c_dev);
        Full_HolderA = ((uint32_t)High_Holder << 16) | Low_Holder;
    } else {
        Full_HolderA = Data_Holder;
    }

    i2c_read(ADPD188BI_REG_SLOTB_CH1, &Data_Holder, device->i2c_dev);
    if (Data_Holder == 65535) {
        i2c_read(ADPD188BI_REG_B_CH1_LOW, &Low_Holder, device->i2c_dev);
        i2c_read(ADPD188BI_REG_B_CH1_HIGH, &High_Holder, device->i2c_dev);
        Full_HolderB = ((uint32_t)High_Holder << 16) | Low_Holder;
    } else {
        Full_HolderB = Data_Holder;
    }

    adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 1, 0);
    adpd188bi_set_bit(device, ADPD188BI_REG_DATA_ACCESS_CTL, 2, 0);

    // Calculate ratio
    ratio = (float)Full_HolderA / (float)Full_HolderB;
    
    //printf("Active scan ratio: %f\r\n", ratio);
    return ratio;
}


// i2c address is 0x64
void smoke_task(void* param)
{ 
	vTaskDelay(pdMS_TO_TICKS(STANDBY_SCAN_PERIOD));
	bool SMOKE_CONFIRMED = false;
	bool WARNING_CONFIRMED = false;
	SmokeSensorData smoke_sensor_data;

    // Initialize the ADPD188BI instance
    adpd188bi_t device;  // Create a device instance

    ESP_LOGI("APP", "Initializing I2C bus configuration");

    i2c_master_bus_config_t i2c_bus_config = {
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .i2c_port = PORT_NUMBER,        
        .scl_io_num = SCL_GPIO_PIN,
        .sda_io_num = SDA_GPIO_PIN,
        .glitch_ignore_cnt = 7,
    };

    i2c_master_bus_handle_t i2c_handle; // Initialize your I2C bus handle (setup this variable accordingly)
    
    ESP_ERROR_CHECK(i2c_new_master_bus(&i2c_bus_config, &i2c_handle));

    uint8_t dev_addr = ADPD188BI_I2C_ADDR;  // Set the I2C device address for ADPD188BI

    esp_err_t ret;

    bool ResetSmoke = false;
    float RATIO_CLEAN = 0;
    float RATIO_STANDBY = 0;
    int consecutive_high = 0;
	int consecutive_high_extreme = 0;

        ret = adpd188bi_init(&device, i2c_handle, dev_addr, INTERRUPT_GPIO_PIN, ResetSmoke); //device configurations: MIKROE-ESP32 I2C configurations, and set internal adpd188bi registers
        if (ret != ESP_OK) {
            ESP_LOGE("APP", "ADPD188BI initialization failed!");
            return;
        }        

        ESP_LOGI("APP", "ADPD188BI initialized successfully");

        RATIO_CLEAN = get_BLUE_OVER_IR_ratio_clean(&device);//get BLUE/IR ratio in the absence of smoke

        while (true) {//scan for smoke loop 
			vTaskDelay(pdMS_TO_TICKS(STANDBY_SCAN_PERIOD));
            RATIO_STANDBY = get_BLUE_OVER_IR_ratio_standby(&device);//scan for the active BLUE/IR ratio

			printf("RATIO_STANDBY: %f\r\n",RATIO_STANDBY);

            if (RATIO_STANDBY >= (RATIO_CLEAN + WARNING_THRESHOLD)) {
                //printf("Potential smoke in smoke sensor\r\n");//send warning signal to main
				smoke_sensor_data.sensor_warning = true;
				snprintf(smoke_sensor_data.message, sizeof(smoke_sensor_data.message),"Warning: smoke source = %.2f",RATIO_STANDBY);
			}
            else {
                smoke_sensor_data.sensor_warning = false;//remove warning signal from main
				snprintf(smoke_sensor_data.message, sizeof(smoke_sensor_data.message),"No warning from smoke sensor");
            }

            if (RATIO_STANDBY >= (RATIO_CLEAN + ALARM_THRESHOLD)) {
                consecutive_high++;
                if (consecutive_high >= MAX_CONSECUTIVE_HIGH) {
                    smoke_sensor_data.sensor_confirmed = true;//send confirmed smoke signal to main
					snprintf(smoke_sensor_data.message, sizeof(smoke_sensor_data.message),"Alert: Smoke detected ratio = %.2f above threshold",RATIO_STANDBY);
                }
            }
			
            else {
                smoke_sensor_data.sensor_confirmed = false;//send no smoke signal to main
				snprintf(smoke_sensor_data.message, sizeof(smoke_sensor_data.message),"Smoke clear");
				consecutive_high = 0;
            }

            if (RATIO_STANDBY >= (RATIO_CLEAN + ALARM_THRESHOLD)) {
                consecutive_high_extreme++;
                if (consecutive_high_extreme >= MAX_CONSECUTIVE_HIGH_EXTREME) {
                    smoke_sensor_data.sensor_confirmed_extreme = true;//send confirmed smoke signal to main
					snprintf(smoke_sensor_data.message, sizeof(smoke_sensor_data.message),"Alert: Smoke detected ratio = %.2f above threshold",RATIO_STANDBY);
                }
            }
			
            else {
                smoke_sensor_data.sensor_confirmed_extreme = false;//send no smoke signal to main
				snprintf(smoke_sensor_data.message, sizeof(smoke_sensor_data.message),"Smoke clear");
				consecutive_high_extreme = 0;
            }			
	
			if (xQueueOverwrite(smoke_sensor_queue, &smoke_sensor_data) != pdPASS) {
				ESP_LOGE("smoke_task", "Failed to overwrite smoke data in queue");
			}
			
			if (Smoke_Reset_Request == true) {
				Smoke_Reset_Request = false;
				RATIO_CLEAN = get_BLUE_OVER_IR_ratio_clean(&device);
			}
			
			if (Delete_Tasks == true) {
				break;
			}

		}   
		vTaskDelete(NULL);
}