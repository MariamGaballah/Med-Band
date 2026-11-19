const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("medical_profiles").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("measurements").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("emergencies").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("alerts").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("nfc_tokens").get());
});

test("User account create, partial update, and validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = pastTime(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Omar Mohamed",
      email: "omar@example.com",
      preferredLanguage: "ar",
      hrMinThreshold: 50,
      hrMaxThreshold: 130,
      spo2MinThreshold: 92,
      fallDetectionEnabled: true,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Partial update allowed fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "en",
      hrMaxThreshold: 140,
      updatedAt: pastTime(1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );

  // Reject invalid threshold inversion (hrMin >= hrMax)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hrMinThreshold: 150,
      hrMaxThreshold: 120,
      updatedAt: pastTime(500),
    })
  );

  // Reject cross-user read
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Medical profile CRUD and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = pastTime(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medical_profiles").doc("primary").set({
      userId: ALICE_UID,
      fullName: "Omar Mohamed",
      emergencyContactName: "Ahmed Mohamed",
      emergencyContactPhone: "+201001234567",
      bloodGroup: "O+",
      allergies: "Penicillin",
      chronicDiseases: "Diabetes",
      currentMedications: "Metformin",
      medicalNotes: "Carry glucose tablets",
      nfcTokenId: "tok_9f8e7d",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medical_profiles").doc("primary").update({
      bloodGroup: "A+",
      updatedAt: pastTime(1000),
    })
  );

  // Reject injecting @DocumentId 'id' inside document body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("medical_profiles").doc("secondary").set({
      id: "secondary",
      userId: ALICE_UID,
      fullName: "Test",
      emergencyContactName: "Contact",
      emergencyContactPhone: "12345",
      bloodGroup: "O+",
      allergies: "",
      chronicDiseases: "",
      currentMedications: "",
      medicalNotes: "",
      nfcTokenId: "",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("medical_profiles").get()
  );
});

test("Measurements create, optional spo2/battery omission, and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t0 = pastTime(5000);

  // Valid measurement with spo2Valid=false and spo2Percent omitted
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("measurements").doc("m_1").set({
      userId: ALICE_UID,
      heartRateBpm: 78,
      spo2Valid: false,
      sensorQuality: "FAIR",
      movementStatus: "RESTING",
      accelMagnitudeG: 1.01,
      gyroMagnitudeDps: 2.3,
      source: "BLE_ESP32C3",
      recordedAt: t0,
    })
  );

  // Valid measurement with spo2Percent and batteryPercent
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("measurements").doc("m_2").set({
      userId: ALICE_UID,
      heartRateBpm: 82,
      spo2Percent: 97,
      spo2Valid: true,
      sensorQuality: "GOOD",
      movementStatus: "WALKING",
      accelMagnitudeG: 1.35,
      gyroMagnitudeDps: 42.1,
      batteryPercent: 86,
      source: "BLE_ESP32C3",
      recordedAt: t0,
    })
  );

  // Reject out-of-range heart rate
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("measurements").doc("m_bad").set({
      userId: ALICE_UID,
      heartRateBpm: 400,
      spo2Valid: false,
      sensorQuality: "POOR",
      movementStatus: "RESTING",
      accelMagnitudeG: 1.0,
      gyroMagnitudeDps: 0.0,
      source: "BLE_ESP32C3",
      recordedAt: t0,
    })
  );

  // Reject updates on immutable measurement log
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("measurements").doc("m_1").update({
      heartRateBpm: 99,
    })
  );

  // List query succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("measurements").get()
  );
});

test("Emergency event lifecycle and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t0 = pastTime(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("emergencies").doc("ev_1").set({
      userId: ALICE_UID,
      triggerType: "POSSIBLE_FALL",
      status: "PENDING_CONFIRMATION",
      heartRateAtEvent: 118,
      spo2AtEvent: 96,
      latitude: 31.2001,
      longitude: 29.9187,
      locationLabel: "Alexandria, Egypt",
      notificationStatus: "NOT_SENT",
      notes: "Unusual impact + stillness detected",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Confirm then resolve -> terminal state
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("emergencies").doc("ev_1").update({
      status: "CANCELLED",
      notificationStatus: "CANCELLED",
      updatedAt: pastTime(2000),
    })
  );

  // Terminal state locking: cannot re-open a CANCELLED emergency
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("emergencies").doc("ev_1").update({
      status: "CONFIRMED",
      updatedAt: pastTime(1000),
    })
  );
});

test("Alerts, devices, and NFC tokens CRUD + bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t0 = pastTime(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("alerts").doc("al_1").set({
      userId: ALICE_UID,
      category: "HIGH_HR",
      severity: "WARNING",
      title: "High Heart Rate",
      message: "Heart rate reached 138 BPM",
      acknowledged: false,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("alerts").doc("al_1").update({
      acknowledged: true,
      updatedAt: pastTime(1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_esp32").set({
      userId: ALICE_UID,
      deviceName: "MedBand-ESP32C3-A4B2",
      macAddress: "AA:BB:CC:DD:EE:FF",
      firmwareVersion: "1.0.0",
      batteryPercent: 88,
      lastSyncAt: t0,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("nfc_tokens").doc("tok_9f8e7d").set({
      userId: ALICE_UID,
      tokenId: "tok_9f8e7d",
      profileId: "primary",
      tagUidHex: "04A2B3C4D5E680",
      label: "Primary Bracelet Tag",
      active: true,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Reject oversized document ID
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("alerts").doc("a".repeat(200)).get()
  );
});
