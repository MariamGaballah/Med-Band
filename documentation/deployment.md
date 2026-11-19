# Deployment & Release Guide

---

## 1. Android Release Build (APK / AAB for Google Play)
1. **Generate Release Keystore** (keep private, never commit to git):
   ```bash
   keytool -genkey -v -keystore medband-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. **Register Release SHA-1 & SHA-256 in Firebase Console**:
   - Export fingerprints: `keytool -list -v -keystore medband-release.jks -alias upload`
   - Add both SHA-1 and SHA-256 to your Android app in Firebase Console (`com.aistudio.medband.qxmzpk`) so **Google Sign-In** and **Firebase App Check (Play Integrity)** work in release builds.
3. **Verify Signing Environment Variables**:
   - Provide `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in your CI or build environment.
4. **Build Signed App Bundle (`.aab`) or APK**:
   ```bash
   gradle :app:bundleRelease
   gradle :app:assembleRelease
   ```
5. **Google Play Policy Checklist**:
   - Declare clear medical/wellness disclaimer (`MedBand is not a certified medical diagnostic device`).
   - Do not add `SEND_SMS` permission unless approved for default SMS handler; MedBand intentionally uses `ACTION_SENDTO` (`smsto:`) user-confirmed handoff so Play Store compliance is maintained without invasive permissions.
   - Ensure Google Play Data Safety section discloses Firebase Authentication, Firestore cloud storage of user health telemetry/profile, and location usage during emergencies.

---

## 2. Firebase Backend Deployment
1. **Deploy Firestore Security Rules**:
   - Deploy `/firestore.rules` to database `ai-studio-android-medband-31c49146-3956-451e-ad00-21b3a7a9af77` (`DeployRules` in AI Studio or `firebase deploy --only firestore:rules`).
2. **Deploy Indexes**:
   ```bash
   firebase deploy --only firestore:indexes
   ```
3. **Enable App Check Enforcement**:
   - In Firebase Console -> **App Check**, register **Play Integrity** for release builds and confirm **Firestore** and **Firebase AI Logic** enforcement only after verifying release SHA-256.
4. **Google Maps Grounding (Firebase AI Logic)**:
   - Confirm Vertex AI / Gemini Developer API & Maps Grounding are enabled in project `gen-lang-client-0830022926`.

---

## 3. iOS App Store Release Notes (If Building iOS Client)
- Configure App ID with **Associated Domains**, **Push Notifications**, and **NFC Tag Reading**.
- Include Bluetooth (`NSBluetoothAlwaysUsageDescription`), Location (`NSLocationWhenInUseUsageDescription`), and NFC (`NFCReaderUsageDescription`) purpose strings.
- Archive in Xcode (`Product -> Archive`) and upload via **App Store Connect** / TestFlight with matching medical disclaimer.
