# Forest Animals Game
Exam project for Sapienza's "Mobile Applications And Cloud Computing" course.

## Authors

| **Name and Surname** | **Matricula** |
| :---: | :---: 
| `Martina Fortuna `    |   1986101
| `Samuele Proietti `    |    1946329



### Purpose:
The project's goal is to develop an interactive, multiplayer Android application that uses device sensors and geolocation to create an immersive gaming experience in which players collaborate to free a trapped NPC. The application demonstrates the integration of modern Android development standards (Jetpack Compose) and cloud-based real-time synchronization (Firebase).

### Key Functionalities:

  User Authentication & Profile Management:
  Secure login via Google Sign-In (Firebase Auth).
  Profile customization including avatar selection and profile picture capture via Camera Intent.
  Geolocation tracking to verify user presence.

  Real-time Multiplayer Lobby:
  Session management using Firebase Firestore to create, join, and synchronize game lobbies.
  Real-time chat functionality for players within the same session.
  
  Interactive 2D Map Engine: A custom-built 2D map rendering engine using Jetpack Compose Canvas.
  
  Sensor-Based Gameplay Mechanics (The 3 Quests involve accelerometer, tilt, magnetometer, gyroscope):
  
  "The Ancient Tree" (Accelerometer): A shake-detection mini-game where the user must physically shake the device to interact with game objects.
  
  "The Winding Path" (Accelerometer/Tilt): An obstacle avoidance game controlled by tilting the device left or right (Roll/Pitch detection).
  
  "The Magic Fog" (Magnetometer & Accelerometer): An orientation game using the device's compass (Azimuth) requiring the user to physically rotate towards specific cardinal points (North, East, West).

--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
