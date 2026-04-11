# Finance Tracker - Project Overview

A comprehensive personal finance tracking application with automatic SMS/Email transaction capture, built with Spring Boot backend and Android mobile app.

---

## 🎯 Project Goals

- **Automatic Transaction Capture**: Read SMS and emails from banks to automatically log transactions
- **Manual Entry Support**: Add transactions manually via Android app
- **Multi-Account Support**: Track multiple bank accounts, credit cards, and wallets
- **Smart Categorization**: Automatically categorize transactions based on merchant/description
- **Reports & Analytics**: Generate weekly and monthly financial reports with charts
- **Secure & Private**: All data encrypted and stored securely

---

## 🏗️ Architecture

### System Components

```
┌─────────────────┐
│  Android App    │ ← User Interface
│  (Kotlin/Java)  │ ← SMS Reading
└────────┬────────┘
         │ REST API
         ▼
┌─────────────────┐
│  Spring Boot    │ ← Business Logic
│  Backend (Java) │ ← Email Reading
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Supabase       │ ← Managed PostgreSQL
│  PostgreSQL     │ ← Data Storage
└─────────────────┘
```

### Technology Stack

**Backend:**
- Spring Boot 3.2.x (Java 17)
- Supabase PostgreSQL (Managed Database)
- JavaMail API for email reading
- Spring Security for authentication
- Maven/Gradle for build

**Android:**
- Kotlin (recommended) or Java
- MVVM Architecture
- Room Database (local storage)
- Retrofit (API client)
- MPAndroidChart (charts)
- Material Design UI

---

## 📋 Features

### Core Features
✅ Automatic SMS transaction capture  
✅ Email transaction reading (IMAP)  
✅ Manual transaction entry  
✅ Multi-account support  
✅ Transaction categorization  
✅ Weekly reports  
✅ Monthly reports  
✅ Category-wise expense breakdown  
✅ Charts and visualizations  

### Supported Transaction Sources
- Bank SMS (HDFC, ICICI, SBI, Axis, etc.)
- Bank Emails
- Credit Card alerts
- UPI apps (Paytm, PhonePe, Google Pay)
- Manual entries

---

## 📁 Project Structure

```
finance-tracker/
├── backend/                    # Spring Boot Backend
│   ├── src/main/java/
│   │   └── com/financetracker/
│   │       ├── controller/     # REST API endpoints
│   │       ├── service/        # Business logic
│   │       ├── repository/     # Database access
│   │       ├── model/          # Entity classes
│   │       └── config/         # Configuration
│   └── pom.xml                 # Maven dependencies
│
├── android/                    # Android Application
│   ├── app/src/main/
│   │   ├── java/com/financetracker/
│   │   │   ├── ui/             # UI screens
│   │   │   ├── data/           # Data layer
│   │   │   ├── service/        # Background services
│   │   │   └── receiver/       # SMS receiver
│   │   └── res/                # Resources
│   └── build.gradle            # Gradle dependencies
│
└── docs/                       # Documentation
    ├── SETUP_ROADMAP.md        # Setup guide
    ├── ARCHITECTURE.md         # System architecture
    ├── API_SPECIFICATION.md    # API documentation
    └── SMS_EMAIL_PATTERNS.md   # SMS/Email patterns
```

---

## 🚀 Getting Started

### Prerequisites

**For Backend:**
- Java JDK 17+
- Maven or Gradle
- Supabase Account (free tier available at https://supabase.com)
- IDE (IntelliJ IDEA recommended)

**For Android:**
- Android Studio (latest)
- Android SDK (API 24+)
- Physical device or emulator

## 📊 Key Capabilities

### Transaction Management
- Create, read, update, delete transactions
- Bulk import from SMS
- Automatic categorization
- Multi-account support
- Search and filter

### Reports & Analytics
- Weekly expense reports
- Monthly financial summaries
- Category-wise breakdown
- Spending trends over time
- Top expenses tracking
- Income vs. expense comparison

### Data Visualization
- Pie charts for category distribution
- Line charts for spending trends
- Bar charts for weekly/monthly comparison
- Balance history graphs
