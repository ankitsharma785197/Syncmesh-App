# 🧩 SyncMesh

<p align="center">
  <b>Seamless Offline Clipboard Sync Across Devices</b><br>
  <sub>Android • Windows • macOS — No Cloud, No Tracking</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Windows%20%7C%20macOS-blue">
  <img src="https://img.shields.io/badge/Version-v1.0.0-green">
  <img src="https://img.shields.io/badge/License-MIT%20%2B%20GPL--3.0-orange">
  <img src="https://img.shields.io/badge/Status-Stable-success">
</p>

---

## 🚀 Overview

**SyncMesh** is a lightweight, privacy-first tool that lets you sync clipboard and data between your devices **instantly over local WiFi**.

No accounts. No internet dependency. No cloud storage.  
Just fast, secure, direct device-to-device sync.

---

## ✨ Features

- 🔄 Real-time Clipboard Sync (Android ↔ Desktop)
- 📡 Works on Local WiFi / Hotspot
- 🔗 Easy Pairing (QR / IP / Nearby)
- 🧠 Clipboard History Support
- 💻 Cross Platform (Windows + macOS)
- 🔒 100% Offline & Private
- ⚡ Low Latency Communication (TCP + UDP)

---

## 📥 Download

| Platform   | File                          | Download |
| ---------- | ----------------------------- | -------- |
| 🤖 Android | `syncmesh-android-v1.0.0.apk` | [Download](https://github.com/ankitsharma785197/Syncmesh-App/releases/download/v1.0.0/syncmesh-android-v1.0.0.apk) |
| 🍎 macOS   | `syncmesh-mac-v1.0.0.dmg`     | [Download](https://github.com/ankitsharma785197/Syncmesh-App/releases/download/v1.0.0/syncmesh-mac-v1.0.0.dmg) |
| 🪟 Windows | `syncmesh-windows-v1.0.0.exe` | [Download](https://github.com/ankitsharma785197/Syncmesh-App/releases/download/v1.0.0/syncmesh-windows-v1.0.0.exe) |

---

## ⚙️ Quick Setup

### 1️⃣ Install
- Install Android APK  
- Install Desktop app  

### 2️⃣ Connect
- Same WiFi network  
OR  
- Same mobile hotspot  

### 3️⃣ Pair Devices
- Scan QR Code  
- Enter IP + Pair Code  
- Auto-detect nearby devices  

### 4️⃣ Enable Keyboard ⚠️
Required for clipboard access

- Go to Android Settings  
- Enable **SyncMesh Keyboard**  
- Set as default  

### 5️⃣ Start Sync
- Open app  
- Tap **Start Sync**  
- Copy text → instantly synced  

---

## 🧠 How It Works

SyncMesh establishes a **peer-to-peer connection** between devices using:

- TCP (Reliable data transfer)
- UDP (Fast device discovery)
- Local network communication

No external servers are involved at any stage.

---

## 🛠️ Tech Stack

| Layer        | Technology              |
|-------------|------------------------|
| Android App | Java + XML             |
| Desktop App | Electron               |
| Networking  | TCP + UDP              |
| Storage     | SQLite                 |

---

## 📦 Project Structure
Syncmesh-App/
├── android-app/
│ ├── app/
│ ├── keyboard_heliboard/
│ ├── lib/
│ └── gradle configs
│
├── desktop-app/
│ └── Electron source
│
├── README.md
└── LICENSE

---

## 🔒 Privacy & Security

- 🚫 No cloud usage  
- 🚫 No data tracking  
- 🚫 No account required  
- ✅ Fully local communication  

Your data **never leaves your network**.

---

## 📜 License

This project is licensed under:

- **MIT License** (Project code)
- **GPL-3.0** (Keyboard component)

---

## 📜 Third-Party Components

### HeliBoard
- License: GPL-3.0  
- Additional: Apache-2.0, CC-BY-SA-4.0  
- Source: https://github.com/Helium314/HeliBoard  

Modified and integrated for clipboard functionality.

---

## 🚧 Roadmap

- 🔐 End-to-end encryption  
- 📂 File transfer support  
- 🔔 Sync notifications  
- 🌐 Cross-network sync (optional cloud bridge)  

---

## 🤝 Contributing

Contributions are welcome.  
Feel free to open issues or submit pull requests.

---

## ⭐ Support

If you like this project:

- ⭐ Star the repo  
- 🍴 Fork it  
- 📢 Share it  

---

## 👨‍💻 Developer

**Ankit Sharma**  
SyncMesh Creator  

---

<p align="center">
  <b>Built for speed. Designed for privacy.</b>
</p>
