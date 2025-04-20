This directory contains the ESP-IDF project for the Pyro Sensor firwmare. The root of the code is located in file main/main.cpp, which handles the Wi-Fi connection via BLE with client app, and the main loop that sends sensor data to Firebase. 
main.cpp tasks use functions in /components folder that handle the operation of each peripheral/module (i.e., sensors, Wi-Fi, battery, Firebase). 
The code is written and tested for the ESP32-S3 microcontroller.

By: Oscar Hernandez
