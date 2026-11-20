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
const OWNER_UID = "owner_uid_999";
const OWNER_EMAIL = "y27985332@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function makeUserProfile(uid, username) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    username,
    displayName: "Player " + username,
    bio: "Free Fire Pro",
    verified: false,
    vip: false,
    vipFrame: "none",
    banned: false,
    followersCount: 0,
    followingCount: 0,
    likesCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("videos").get());
  await assertFails(unauthDb.collection("chats").get());
  await assertFails(unauthDb.collection("hashtags").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "ff_alice"))
  );
});

test("User profile creation: prevents privilege escalation (verified/vip/banned) and isolates private PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@gmail.com",
    email_verified: true,
  }).firestore();

  // Valid creation succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "ff_alice"))
  );

  // Self-assigning verified or vip fails
  await assertFails(
    aliceDb.collection("users").doc("spoof_uid").set({
      ...makeUserProfile("spoof_uid", "spoof"),
      verified: true,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      vip: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Private PII doc can only be read/written by owner or admin
  const ts = pastDate(3000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@gmail.com",
      blockedUserIds: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@gmail.com",
    email_verified: true,
  }).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get()
  );
});

test("Video creation, atomic like/view anti-tampering, and deletion permissions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@gmail.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "ff_alice"))
  );

  const ts = pastDate(4000);
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      authorName: "Player ff_alice",
      authorUsername: "ff_alice",
      authorVerified: false,
      authorVip: false,
      authorVipFrame: "none",
      description: "لقطة هيدشوت أسطورية #FreeFire",
      hashtags: ["#FreeFire"],
      videoUrl: "https://example.com/clip.mp4",
      durationSec: 15,
      likesCount: 0,
      commentsCount: 0,
      viewsCount: 0,
      favoritesCount: 0,
      sharesCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Manipulating likesCount without atomic like doc fails
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      likesCount: 100,
      updatedAt: pastDate(1000),
    })
  );

  // Atomic batch like (+1 with like doc) succeeds
  const batch = bobDb.batch();
  batch.set(bobDb.collection("videos").doc("vid_1").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    createdAt: pastDate(1000),
  });
  batch.update(bobDb.collection("videos").doc("vid_1"), {
    likesCount: 1,
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(batch.commit());

  // Bob cannot delete Alice's video
  await assertFails(bobDb.collection("videos").doc("vid_1").delete());
});

test("Comments & Pinning: only video author or admin can pin comments", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = pastDate(10000);
    await db.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "ff_alice"));
    await db.collection("users").doc(BOB_UID).set(makeUserProfile(BOB_UID, "ff_bob"));
    await db.collection("videos").doc("vid_pin").set({
      userId: ALICE_UID,
      authorName: "Player ff_alice",
      authorUsername: "ff_alice",
      authorVerified: false,
      authorVip: false,
      authorVipFrame: "none",
      description: "Clip",
      hashtags: ["#FF"],
      videoUrl: "https://example.com/v.mp4",
      durationSec: 10,
      likesCount: 0,
      commentsCount: 1,
      viewsCount: 0,
      favoritesCount: 0,
      sharesCount: 0,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("videos").doc("vid_pin").collection("comments").doc("c_1").set({
      videoId: "vid_pin",
      userId: BOB_UID,
      authorName: "Player ff_bob",
      authorUsername: "ff_bob",
      authorVerified: false,
      authorVip: false,
      text: "Great shot!",
      likesCount: 0,
      pinned: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob (not video owner) cannot pin his own comment
  await assertFails(
    bobDb.collection("videos").doc("vid_pin").collection("comments").doc("c_1").update({
      pinned: true,
      updatedAt: pastDate(1000),
    })
  );

  // Alice (video owner) can pin the comment
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_pin").collection("comments").doc("c_1").update({
      pinned: true,
      updatedAt: pastDate(1000),
    })
  );
});

test("Private Chats: strict participant isolation", async () => {
  const CHARLIE_UID = "charlie_uid_789";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = pastDate(8000);
    await db.collection("chats").doc("alice_bob").set({
      participantIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hey!",
      lastSenderId: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Participant query succeeds
  await assertSucceeds(
    aliceDb.collection("chats").where("participantIds", "array-contains", ALICE_UID).get()
  );

  // Non-participant cannot read or query chat
  await assertFails(charlieDb.collection("chats").doc("alice_bob").get());
  await assertFails(charlieDb.collection("chats").get());
});

test("Owner Admin protection: only verified y27985332@gmail.com can approve verification/VIP/ban", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = pastDate(9000);
    await db.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "ff_alice"));
    await db.collection("verification_requests").doc("req_1").set({
      userId: ALICE_UID,
      username: "ff_alice",
      displayName: "Player ff_alice",
      ffPlayerId: "123456789",
      reason: "صانع محتوى فري فاير",
      followersCount: 0,
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // Spoofed email (email_verified: false) MUST FAIL
  const spoofOwnerDb = testEnv.authenticatedContext("spoof_admin", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofOwnerDb.collection("verification_requests").doc("req_1").update({
      status: "approved",
      updatedAt: pastDate(1000),
    })
  );

  // Non-owner user MUST FAIL
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@gmail.com",
    email_verified: true,
  }).firestore();
  await assertFails(bobDb.collection("verification_requests").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      verified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Real verified owner y27985332@gmail.com SUCCEEDS
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(ownerDb.collection("verification_requests").get());
  await assertSucceeds(
    ownerDb.collection("verification_requests").doc("req_1").update({
      status: "approved",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    ownerDb.collection("users").doc(ALICE_UID).update({
      verified: true,
      updatedAt: pastDate(1000),
    })
  );
});
