# ECEN404-2025SP-Pyro-Sensor
Senior Capstone repository for ECEN 404 PYRO SENSOR | Team 24

## Overview
Pyro Sensor is a smart fire and smoke detection system developed as part of a senior design project at Texas A&M University. The system is designed to monitor buildings for fire or smoke events, notify users in real time, and provide emergency responders with essential information to improve response time and safety outcomes.

## Project Subsystems
- **Mobile Application** – Interfaces with users, firefighters, and cloud services
- **Microcontroller (MCU)** – Handles BLE communication and sensor control logic
- **Hardware** – Custom-built fire and smoke sensors and power systems

---

## 📱 Mobile Application Subsystem

### Purpose
The mobile app allows users to:
- Set up accounts and create building "workspaces"
- Register and manage fire/smoke sensors in each room
- Receive alerts for both warning and alarm states with unique sounds
- Share building and occupant info with firefighters via a generated access code
- Send automated SMS alerts to 911 dispatchers with location and incident info

### Key Features
- **Firebase Integration**  
  - Realtime Database to monitor sensors
  - Authentication for user accounts
  - Cloud Storage for images and documents (e.g., pets or building plans)

- **BLE Setup for Sensors**  
  - App initiates sensor configuration via Bluetooth for initial setup only

- **Firefighter Access Mode**  
  - Each user is assigned a unique code firefighters can use to view room, occupant, and pet details without needing to log in

- **Real-Time Alerts & Notifications**  
  - Push notifications for fire/smoke events using Firebase Cloud Messaging
  - Notifications work even when the app is closed, with distinct sounds for different severity levels

- **Twilio SMS Integration**  
  - Automatically notifies 911 dispatcher with incident info when fire is detected

---

## 🔧 Microcontroller Subsystem (To Be Filled In)
_This section is reserved for the microcontroller team to describe BLE protocols, sensor triggering logic, and how data is sent to the app._

---

## 🔩 Hardware Subsystem (To Be Filled In)
_This section is reserved for the hardware team to describe the physical fire/smoke sensor design, power delivery, and enclosures._

---

## Getting Started (Mobile App)

### Prerequisites
- Android Studio installed
- Firebase project with Authentication, Realtime Database, and Storage configured
- A Firebase `google-services.json` file in `/app/`
- Twilio API credentials if testing emergency SMS functionality


