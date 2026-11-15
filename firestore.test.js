const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-auramath-9e860255-9d40-4a16-9604-477af66c2051";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write notebooks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notebooks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notebooks").doc("nb_1").set({
      userId: ALICE_UID,
      title: "Hacked",
      entryCount: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Owner can create, update, list, and delete their own notebook, entry, and stroke", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const nbRef = aliceDb.collection("users").doc(ALICE_UID).collection("notebooks").doc("nb_1");

  await assertSucceeds(
    nbRef.set({
      userId: ALICE_UID,
      title: "Calculus III",
      entryCount: 1,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    nbRef.update({
      title: "Linear Algebra & Calculus",
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notebooks").get());

  const entryRef = nbRef.collection("entries").doc("entry_1");
  await assertSucceeds(
    entryRef.set({
      userId: ALICE_UID,
      notebookId: "nb_1",
      expression: "d/dx(x^3)",
      result: "3x^2",
      steps: ["Apply power rule: d/dx(x^n) = n*x^(n-1)"],
      domainTag: "Calculus",
      graphType: "2D",
      anchorX: 120,
      anchorY: 240,
      orderIndex: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const strokeRef = nbRef.collection("strokes").doc("stroke_1");
  await assertSucceeds(
    strokeRef.set({
      userId: ALICE_UID,
      notebookId: "nb_1",
      colorArgb: -131072,
      strokeWidth: 4.5,
      toolType: "PEN",
      points: [{ x: 10.0, y: 20.0, p: 0.8 }],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(strokeRef.delete());
  await assertSucceeds(entryRef.delete());
  await assertSucceeds(nbRef.delete());
});

test("Cross-user access is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const nbRef = aliceDb.collection("users").doc(ALICE_UID).collection("notebooks").doc("nb_alice");
  await assertSucceeds(
    nbRef.set({
      userId: ALICE_UID,
      title: "Private Notes",
      entryCount: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notebooks").doc("nb_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notebooks").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notebooks").doc("nb_bob").set({
      userId: BOB_UID,
      title: "Spoof",
      entryCount: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Shadow update with ghost field and invalid types fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const nbRef = aliceDb.collection("users").doc(ALICE_UID).collection("notebooks").doc("nb_shadow");

  // Ghost field on create
  await assertFails(
    nbRef.set({
      userId: ALICE_UID,
      title: "Legit",
      entryCount: 0,
      isAdmin: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    nbRef.set({
      userId: ALICE_UID,
      title: "Legit",
      entryCount: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Immutable createdAt tamper
  await assertFails(
    nbRef.update({
      title: "Changed",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Orphaned entry without parent notebook
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notebooks")
      .doc("nonexistent_nb")
      .collection("entries")
      .doc("e1")
      .set({
        userId: ALICE_UID,
        notebookId: "nonexistent_nb",
        expression: "1+1",
        result: "2",
        steps: [],
        domainTag: "Algebra",
        graphType: "NONE",
        anchorX: 0,
        anchorY: 0,
        orderIndex: 0,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );
});
