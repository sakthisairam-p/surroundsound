# 3D Surround PRO

3D Surround PRO is a professional-grade audio enhancement application designed to elevate your listening experience. It features a rich, hardware-inspired interface with advanced 3D spatial audio controls, frequency mixing, and dynamic sound manipulation.

## Features

*   **3D Spatial Audio Engine**: Enhance your audio with multiple modes including Normal, 5.1 Surround, 7.1 Surround, 32D, and 8D audio.
*   **Professional Interface**: A sleek, hardware-inspired UI featuring digital jog dials for precise volume and bass control.
*   **Frequency Mixer**: Independently adjust Bass, Voice, and Timbre volumes to find the perfect mix for any track.
*   **Background Processing** (Android): Persistent background service using Android `AudioService` that hooks into the system audio stream to apply enhancements globally.
*   **Media Controls**: Built-in transport controls for managing playlists, skipping tracks, and controlling playback seamlessly.

## Getting Started

### Web Application

To use the web interface:

1.  Open `index.html` in a modern web browser.
2.  Click **"Open Playlist"** to select local audio files.
3.  Use the master power switch to enable enhancements.
4.  Toggle the **3D Enhancement** or **Frequency Mixer** panels to customize your audio output.

### Android Application

The project includes an Android application configured to run as a persistent background service to apply audio effects system-wide.

1.  Open the project in Android Studio (using the provided `settings.gradle` and `app` directory).
2.  Build and deploy the application to your Android device or emulator.
3.  The `AudioService` and `AudioReceiver` handle session lifecycle events to ensure the enhancements remain active while media is playing on your device.

## Technologies Used

*   **Frontend**: HTML5, CSS3, JavaScript (Web Audio API)
*   **Android**: Kotlin, Android `AudioEffect` API (Equalizer, BassBoost, Virtualizer, DynamicsProcessing)

## UI Usage Tips

*   **Master Power**: Must be toggled on to process audio through the equalizer and spatial engines.
*   **Jog Dials**: Drag vertically/horizontally or simply click on the dials to dynamically adjust Volume and Bass. Gives MIN/MAX feedback.
*   **8D/32D Audio**: These modes use specialized panning logic to rotate and position audio dynamically in the stereo field.
