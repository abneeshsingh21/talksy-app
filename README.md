# JengaChat - Private Messaging App

A fully-featured private chat application built with Kotlin and Jetpack Compose. JengaChat provides secure messaging, voice & video calling, and group communication features similar to WhatsApp and Instagram.

## 🌟 Features

### Messaging
- ✅ Real-time 1-on-1 messaging
- ✅ Group chats with unlimited participants
- ✅ Media sharing (images, videos, audio, documents)
- ✅ Message reactions (emoji)
- ✅ Reply to messages
- ✅ Message read receipts
- ✅ Typing indicators
- ✅ Message deletion
- ✅ Chat mute/unmute

### Calling
- ✅ Voice calls (1-on-1)
- ✅ Video calls (1-on-1)
- ✅ Group voice/video calls
- ✅ Call history with duration tracking
- ✅ In-call controls (mute, speaker, camera switch)
- ✅ Background call handling
- ✅ Push notifications for incoming calls

### User Management
- ✅ Email/Password authentication
- ✅ Google Sign-In
- ✅ Phone number verification
- ✅ Profile customization
- ✅ Online/offline status
- ✅ Block/unblock users
- ✅ Contact management

### Security & Privacy
- ✅ Firebase Authentication
- ✅ Secure data storage
- ✅ Biometric authentication support
- ✅ App lock feature

## 🛠️ Tech Stack

- **Language:** Kotlin 1.9.0
- **UI:** Jetpack Compose with Material Design 3
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt (Dagger)
- **Backend:** Firebase Suite
  - Firebase Authentication
  - Cloud Firestore (database)
  - Firebase Storage (media)
  - Firebase Realtime Database (presence)
  - Firebase Cloud Messaging (push notifications)
- **Calling:** WebRTC (Stream WebRTC Android v1.0.7)
- **Image Loading:** Coil
- **Local Storage:** Room + DataStore
- **Camera:** CameraX
- **Animations:** Lottie

## 📋 Prerequisites

Before you begin, ensure you have:
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or higher
- Android SDK with API 34
- A Firebase project

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/JengaChat.git
cd JengaChat
```

### 2. Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or use an existing one
3. Add an Android app with package name: `com.jengachat`
4. Download `google-services.json` and place it in the `app/` directory

### 3. Enable Firebase Services

In Firebase Console, enable the following:

#### Authentication
- Email/Password
- Google Sign-In
- Phone (optional)

#### Cloud Firestore
Create the following collections:
- `users` - User profiles
- `chats` - Chat metadata
- `messages` - Chat messages (subcollection under chats)
- `calls` - Call history

#### Firestore Security Rules
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    match /chats/{chatId} {
      allow read, write: if request.auth != null && 
        request.auth.uid in resource.data.participants;
    }
    
    match /chats/{chatId}/messages/{messageId} {
      allow read, write: if request.auth != null;
    }
    
    match /calls/{callId} {
      allow read, write: if request.auth != null &&
        (request.auth.uid == resource.data.callerId || 
         request.auth.uid in resource.data.participants);
    }
  }
}
```

#### Firebase Storage
Enable Storage and set security rules:
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /profile_images/{userId}/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    match /chat_media/{chatId}/{fileName} {
      allow read, write: if request.auth != null;
    }
  }
}
```

#### Realtime Database
Enable for online presence:
```json
{
  "rules": {
    "status": {
      "$uid": {
        ".read": true,
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

#### Cloud Messaging
Enable FCM for push notifications.

### 4. WebRTC Signaling (Optional - For Production)

For production, set up a signaling server or use Firebase Realtime Database as shown in the code.

### 5. Build and Run

```bash
# Build the debug APK
./gradlew assembleDebug

# Or run directly on a connected device
./gradlew installDebug
```

## 📁 Project Structure

```
app/src/main/java/com/jengachat/
├── data/
│   ├── model/          # Data classes (User, Chat, Message, Call)
│   └── repository/     # Repository implementations
├── di/                 # Hilt dependency injection modules
├── service/            # Background services (FCM, Call)
├── ui/
│   ├── auth/           # Authentication screens
│   ├── call/           # Voice/Video call screens
│   ├── chat/           # Chat screens
│   ├── components/     # Reusable UI components
│   ├── navigation/     # Navigation setup
│   ├── profile/        # Profile screens
│   └── theme/          # Material theming
├── util/               # Utility classes
├── webrtc/             # WebRTC implementation
├── JengaChatApp.kt     # Application class
└── MainActivity.kt     # Main activity
```

## 📱 Screenshots

*Coming soon*

## 🔒 Security Considerations

- All communications are secured via Firebase
- User passwords are handled by Firebase Auth (never stored locally)
- Media files have access control via Firebase Storage rules
- WebRTC calls use DTLS-SRTP encryption

## 🐛 Known Issues & TODOs

- [ ] End-to-end encryption for messages
- [ ] Message search functionality
- [ ] Status/Stories feature
- [ ] Message backup/restore
- [ ] Multi-device support
- [ ] Desktop/Web version

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

Created with ❤️ for learning and portfolio purposes.

## 📞 Support

If you encounter any issues, please open an issue on GitHub.

---

**Note:** This is a portfolio/learning project. For production use, additional security measures, testing, and optimizations would be required.
