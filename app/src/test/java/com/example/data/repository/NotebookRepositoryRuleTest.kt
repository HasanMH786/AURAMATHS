package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.InkStrokeDoc
import com.example.data.model.MathEntry
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ownerCanCreateNotebookEntryAndStroke_andObserveRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = NotebookRepository(firestore, auth)

        val nbId = withTimeout(TIMEOUT_MS) {
            repo.createNotebook("Signals & Systems").getOrThrow()
        }
        assertTrue(nbId.isNotBlank())

        val notebooks = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeNotebooks(uid).first { list -> list.any { it.id == nbId } }
        }
        assertEquals("Signals & Systems", notebooks.first { it.id == nbId }.title)

        val entryId = withTimeout(TIMEOUT_MS) {
            repo.saveEntry(
                nbId,
                MathEntry(
                    expression = "d/dx(x^3)",
                    result = "3x^2",
                    steps = listOf("Power rule"),
                    domainTag = "Calculus",
                    graphType = "2D",
                    anchorX = 100.0,
                    anchorY = 200.0,
                    orderIndex = 0L
                )
            ).getOrThrow()
        }
        assertTrue(entryId.isNotBlank())

        val strokeId = withTimeout(TIMEOUT_MS) {
            repo.saveStroke(
                nbId,
                InkStrokeDoc(
                    colorArgb = 0xFF38BDF8L,
                    strokeWidth = 4.0,
                    toolType = "PEN",
                    points = listOf(mapOf("x" to 12.0, "y" to 24.0, "p" to 0.75))
                )
            ).getOrThrow()
        }
        assertTrue(strokeId.isNotBlank())

        val entries = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeEntries(uid, nbId).first { it.any { e -> e.id == entryId } }
        }
        assertEquals("3x^2", entries.first { it.id == entryId }.result)
    }

    @Test
    fun crossUserReadIsRejectedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NotebookRepository(firestore, auth)
        val nbId = withTimeout(TIMEOUT_MS) {
            aliceRepo.createNotebook("Alice Confidential").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NotebookRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeNotebooks(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)

        // Bob also cannot read entries inside Alice's notebook
        var entryCaught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeEntries(aliceUid, nbId).first()
            }
        } catch (t: Throwable) {
            entryCaught = t
        }
        val entryEx = checkNotNull(entryCaught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $entryCaught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, entryEx.code)
    }

    @Test
    fun unauthenticatedObserveIsRejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = NotebookRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeNotebooks("some_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_auramath@test.com"
        const val BOB_EMAIL = "bob_auramath@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
