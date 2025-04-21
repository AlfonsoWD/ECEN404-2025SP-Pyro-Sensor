package com.example.pyrosensor

data class Sensor( //defines a data class for sensors
    val name: String, //stores the name of the sensor
    val status: String, //stores the status of the sensor
    val battery: String, //stores the status of the sensor
    val state: String = "Safe", // Default to Safe if not specified
    val lastTriggered: Long = 0, // Timestamp of when the sensor was last triggered (alarm or warning)
    val lastDeactivated: Long = 0, // Timestamp of when the sensor was last deactivated (returned to Safe)
    val alarmStartTime: String? = null, // Timestamp of when the alarm started
) {
    companion object { //companion object for static-like behavior
        fun getDummySensors(): List<Sensor> { //returns a list of dummy sensors
            return listOf(
                Sensor("Temperature Sensor", "Active", "Good", "Safe"), //dummy sensor with name and status
                Sensor("Smoke Detector", "Inactive", "Bad", "Warning"), //dummy sensor with name and status
                Sensor("Motion Sensor", "Active", "Low", "Alert") //dummy sensor with name and status
            )
        }
    }
}

