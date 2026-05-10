# Marueat 😼 - Android Technical Research Project

An Android native client implementation for campus cafeteria QR code functionality. **For learning and research purposes only.**

## Overview

Marueat 😼 analyzes the network request flow of campus comprehensive apps, studies modern Android authentication mechanisms and multi-system session management, and decouples the cafeteria QR code feature into a standalone lightweight client.

> **Important Notice**: This project is for technical learning and research only. Any form of unauthorized usage is discouraged. Please ensure compliance with local laws and terms of service before use.

### Core Features

- Unified authentication login (WebView login & Token import)
- Cross-system session bridging
- Cafeteria QR code retrieval & display
- User profile display
- QR code refresh anytime

## Project Structure

```
├── app/                            # Android Client
│   └── src/main/
│       ├── assets/                 # WebView login page
│       ├── java/com/marueat/app/  # Kotlin source code
│       │   ├── data/              # Data layer (Repository, Model, Storage)
│       │   ├── network/           # Network layer (API, AuthService)
│       │   └── util/              # Utilities (JWT parser, QR generator)
│       └── res/                   # Resources
│
└── gradle/                         # Gradle build config
```

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel + LiveData)
- **Network**: OkHttp
- **QR Code**: ZXing
- **Storage**: EncryptedSharedPreferences
- **UI**: Material Design 3, ViewBinding

## Configuration

Configure the following parameters before use:

### 1. Authentication Service

**File: `app/src/main/java/com/marueat/app/network/PortalAuthService.kt`**
```kotlin
companion object {
    private const val PORTAL_APP_ID = "YOUR_AUTHING_APP_ID"
    private val CANDIDATE_URLS = listOf(
        "https://YOUR_AUTH_HOST/api/v2/login/account",
        // ...
    )
}
```

### 2. Business API

**File: `app/src/main/java/com/marueat/app/network/CafeteriaApi.kt`**
```kotlin
companion object {
    private const val APP_API_BASE = "https://YOUR_APP_API_HOST"
    private const val MKB_BASE = "https://YOUR_CAFETERIA_HOST"
    private const val CLIENT_ID = "YOUR_CLIENT_ID"
}
```

### 3. WebView Login

**File: `app/src/main/assets/authing_login.js`**
```javascript
const PORTAL_APP_ID = 'YOUR_AUTHING_APP_ID';
const PORTAL_APP_HOST = 'https://YOUR_AUTH_HOST';
const SCRIPT_URL = 'https://YOUR_PORTAL_HOST/static/lib/index.min.js';
```

## Authentication Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Step 1: User Login                                         │
│  Obtain main token via WebView + Authing SDK                │
└───────────────────────────┬─────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 2: Exchange for Module Code                           │
│  GET /ext/redirect/getCode?clientId=xxx                     │
│  Main token → 302 redirect → code                           │
└───────────────────────────┬─────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 3: Login to Cafeteria Subsystem                       │
│  POST /base/api/v1/wkb/loginNew                             │
│  tUserId + code → mkbToken                                  │
└───────────────────────────┬─────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 4: Fetch Business Data                                │
│  Use mkbToken to get user info and QR code                  │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### JWT Parser (JwtParser.kt)
Manual JWT token parsing without third-party dependencies. Extracts user ID from token payload.

### Session Bridging (CafeteriaApi.kt)
Implements cross-system token conversion:
- Main token → Exchange for code
- authUuid + code → Get mkbToken
- mkbToken → Fetch business data

### WebView Login (WebViewLoginActivity.kt)
Communicates with Authing SDK in WebView via JavascriptInterface for native login flow.

### Secure Storage (SessionStore.kt)
Uses EncryptedSharedPreferences for encrypted session storage.

## Dependencies

- [OkHttp](https://square.github.io/okhttp/) - HTTP client
- [ZXing](https://github.com/zxing/zxing) - QR code generation
- [AndroidX Security](https://developer.android.com/jetpack/androidx/releases/security) - Encrypted storage
- [Material Components](https://github.com/material-components/material-components-android) - UI components

---

## Legal Disclaimer

### Important Notice

**This project is for learning, research, and educational purposes only. Please read the following carefully.**

### 1. Project Nature

This is a **technical research project** aimed at:
- Learning and understanding modern Android authentication mechanisms
- Researching multi-system session management and token bridging
- Demonstrating Android native app and WebView interaction
- Serving as a reference for mobile development learning

### 2. Usage Restrictions

**This project provides no warranty. Users assume all risks:**

- **No Commercial Use**: Not for any commercial purpose
- **No Illegal Use**: Not for any purpose violating laws or regulations
- **No Malicious Use**: Not for attacking, damaging, or abusing any systems
- **Learning Only**: For personal learning and technical research only

### 3. Disclaimer

**This project is provided "as is". The author is NOT liable for:**

1. Any direct or indirect damages caused by using this project
2. Legal disputes arising from using this project
3. Account bans or penalties from violating terms of service
4. System failures or data loss caused by using this project

---

**By using this project, you acknowledge that you have read, understood, and agreed to all the above terms.**
