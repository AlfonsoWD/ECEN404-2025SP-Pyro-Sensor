/* Includes ------------------------------------------------------------------*/
#include "Smoke_Sensor.h"

#include "freertos/FreeRTOS.h"
#include "esp_err.h"
#include "esp_log.h"
#include "esp_timer.h"

#define SCL_GPIO_PIN 47
#define SDA_GPIO_PIN 21
#define INTERRUPT_GPIO_PIN 48
#define PORT_NUMBER -1

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
		uint8_t dev_addr, int int_gpio) {
	/* Print initializing message */
	ESP_LOGI(TAG, "Initializing instance...");

	/* Variable to return error code */
	esp_err_t ret = ESP_OK;

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
			.intr_type = GPIO_INTR_DISABLE
	};

	ret = gpio_config(&gpio_conf);

	if (ret != ESP_OK) {
		ESP_LOGE(TAG, "Failed to configure int GPIO");
		return ret;
	}

		/*  */
		uint16_t mode_status_read;

		i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);
		printf("Register 0x10 before software reset = %u\r\n",mode_status_read);

	adpd188bi_soft_reset(me);

		i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);
		printf("Register 0x10 after software reset = %u\r\n",mode_status_read);
	

	adpd188bi_set_mode(me, ADPD188BI_MODE_IDLE);


		i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);
		printf("Register 0x10 after mode change1 = %u\r\n",mode_status_read);	

	uint16_t devid;

	i2c_read(ADPD188BI_REG_DEVID, &devid, me->i2c_dev);
	printf("ID: 0x%.2X\r\n", (uint16_t)(devid  & 0xFF));
	printf("Rev: 0x%.2X\r\n", (uint16_t)((devid >> 8) & 0xFF));

	
  adpd188bi_set_bit(me, ADPD188BI_REG_SAMPLE_CLK, 7, 1);


  adpd188bi_set_mode(me, ADPD188BI_MODE_PROGRAM);//set device to programming mode

	
	i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);
	printf("Register 0x10 after mode change2 = %u\r\n",mode_status_read);	

  i2c_write(ADPD188BI_REG_SLOT_EN, 0x30A9, me->i2c_dev);
  i2c_write(ADPD188BI_REG_FSAMPLE, 0x0200, me->i2c_dev);
  i2c_write(ADPD188BI_REG_PD_LED_SELECT, 0x011D, me->i2c_dev);
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

//////////////////////////////////////////////////////////////////////////////////////////////////////
/*
  i2c_write(ADPD188BI_REG_DATA_ACCESS_CTL, 0x0007, me->i2c_dev);
  adpd188bi_set_bit(me, ADPD188BI_REG_DATA_ACCESS_CTL, 0, 1);
  adpd188bi_set_bit(me, ADPD188BI_REG_INT_MASK, 5, 0);
  adpd188bi_set_bit(me, ADPD188BI_REG_INT_MASK, 6, 1);
  adpd188bi_set_bit(me, ADPD188BI_REG_INT_MASK, 8, 1);
  adpd188bi_set_bit(me, ADPD188BI_REG_GPIO_DRV, 0, 1);
  adpd188bi_set_bit(me, ADPD188BI_REG_GPIO_DRV, 1, 1);
  adpd188bi_set_bit(me, ADPD188BI_REG_GPIO_DRV, 2, 1);
  i2c_write(ADPD188BI_REG_SLOT_EN, 0x3001, me->i2c_dev);
  */
/////////////////////////////////////////////////////////////////REVISE FROM "READING DATA" FROM DATA SHEET

  adpd188bi_set_mode(me, ADPD188BI_MODE_NORMAL);
	i2c_read(ADPD188BI_REG_MODE, &mode_status_read, me->i2c_dev);
	printf("Register 0x10 after mode change3 = %u\r\n",mode_status_read);	
	/* Return ESP_OK */
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

	/* Read data register, set the specified bit number and write the new value */
	i2c_read(reg_addr, &reg_data, me->i2c_dev);
	set_n_bits(&reg_data, 1, bit_val, bit_num);
	i2c_write(reg_addr, reg_data, me->i2c_dev);

	/* Return ESP_OK */
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

		printf("The adpd188bi is reading data: %u\n", reg_data);

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

	if (i2c_master_transmit_receive(i2c_dev, &reg_addr, 1, buffer, 2, -1) != ESP_OK) {
		return -1;
	}

	*reg_data = (uint16_t)((buffer[0] << 8) | buffer[1]);

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

void check_smoke_task(void)
{ 
    // Initialize the ADPD188BI instance
    adpd188bi_t device;  // Create a device instance

    i2c_master_bus_config_t i2c_bus_config = {
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .i2c_port = PORT_NUMBER,        
        .scl_io_num = SCL_GPIO_PIN,
        .sda_io_num = SDA_GPIO_PIN,
        .glitch_ignore_cnt = 7,
    };

    i2c_master_bus_handle_t i2c_handle; // Initialize your I2C bus handle (setup this variable accordingly)
    
    ESP_ERROR_CHECK(i2c_new_master_bus(&i2c_bus_config,&i2c_handle));

    uint8_t dev_addr = ADPD188BI_I2C_ADDR;  // Set the I2C device address for ADPD188BI

    // Call the initialization function with proper arguments
    esp_err_t ret = adpd188bi_init(&device, i2c_handle, dev_addr, INTERRUPT_GPIO_PIN);
    if (ret != ESP_OK) {
        printf("ADPD188BI initialization failed!\n");
    }


    uint16_t sens_data = 0;
    uint8_t fifo_int = 0;
    uint8_t slot_a_int = 0;
    uint8_t slot_b_int = 0;
    esp_err_t Reading_Check;

    while (true) {
        //if ( 0 == adpd188bi_get_int_gpio(&device)) {
            //adpd188bi_get_int(&device,&fifo_int,&slot_a_int,&slot_b_int);
            //if (0 != slot_a_int) {
                Reading_Check = adpd188bi_read_sens_data(&device, ADPD188BI_SLOT_A, ADPD188BI_CH_1, &sens_data);
                printf("DATA from slot A: %d\r\n",sens_data);
                slot_a_int = 0;
                vTaskDelay(pdMS_TO_TICKS(1000));
            //}
           // if (0 != slot_b_int) {
                Reading_Check = adpd188bi_read_sens_data(&device, ADPD188BI_SLOT_B, ADPD188BI_CH_1, &sens_data);
                printf("DATA from slot B: %d\r\n",sens_data);
                slot_b_int = 0;
                vTaskDelay(pdMS_TO_TICKS(1000));
           // }
            //if (0 != fifo_int) {
                printf("FIFO interrupt count: %d\r\n", fifo_int);
                for ( uint8_t fifo_cnt = 0; fifo_cnt < fifo_int; fifo_cnt++) {
                    i2c_read(ADPD188BI_REG_FIFO_ACCESS, &sens_data, device.i2c_dev); //read data from the fifo register
                    printf("Data from fifo: %d\r\n",sens_data);
                }
                vTaskDelay(pdMS_TO_TICKS(1000));
            //}
       // }
        sens_data = 0;
    }
}