package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.InkStrokeDoc
import com.example.data.model.MathEntry
import com.example.data.model.MathNotebook
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotebookRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeNotebooks(userId: String): Flow<List<MathNotebook>> = flow {
        val path = "users/$userId/notebooks"
        emitAll(
            db.collection("users").document(userId).collection("notebooks")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            MathNotebook::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeEntries(userId: String, notebookId: String): Flow<List<MathEntry>> = flow {
        val path = "users/$userId/notebooks/$notebookId/entries"
        emitAll(
            db.collection("users").document(userId)
                .collection("notebooks").document(notebookId)
                .collection("entries")
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            MathEntry::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStrokes(userId: String, notebookId: String): Flow<List<InkStrokeDoc>> = flow {
        val path = "users/$userId/notebooks/$notebookId/strokes"
        emitAll(
            db.collection("users").document(userId)
                .collection("notebooks").document(notebookId)
                .collection("strokes")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            InkStrokeDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createNotebook(title: String): Result<String> {
        val uid = requireUserId()
        val cleanTitle = title.trim().ifBlank { "Untitled Sheet" }.take(120)
        val docRef = db.collection("users").document(uid).collection("notebooks").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to cleanTitle,
            "entryCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun renameNotebook(notebook: MathNotebook, newTitle: String): Result<Unit> {
        val uid = requireUserId()
        require(notebook.id.isNotBlank()) { "Notebook ID must not be blank" }
        val cleanTitle = newTitle.trim().ifBlank { "Untitled Sheet" }.take(120)
        val docRef = db.collection("users").document(uid).collection("notebooks").document(notebook.id)
        val updates = mapOf(
            "title" to cleanTitle,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotebook(notebookId: String): Result<Unit> {
        val uid = requireUserId()
        require(notebookId.isNotBlank()) { "Notebook ID must not be blank" }
        val nbRef = db.collection("users").document(uid).collection("notebooks").document(notebookId)
        return try {
            val entries = nbRef.collection("entries").get().await()
            val strokes = nbRef.collection("strokes").get().await()
            val batch = db.batch()
            entries.documents.forEach { batch.delete(it.reference) }
            strokes.documents.forEach { batch.delete(it.reference) }
            batch.delete(nbRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, nbRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveEntry(notebookId: String, entry: MathEntry): Result<String> {
        val uid = requireUserId()
        require(notebookId.isNotBlank()) { "Notebook ID must not be blank" }
        val nbRef = db.collection("users").document(uid).collection("notebooks").document(notebookId)
        val entriesCol = nbRef.collection("entries")
        val docRef = if (entry.id.isNotBlank()) entriesCol.document(entry.id) else entriesCol.document()
        val isNew = entry.id.isBlank()
        return try {
            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "notebookId" to notebookId,
                    "expression" to entry.expression.take(1000),
                    "result" to entry.result.take(4000),
                    "steps" to entry.steps.take(30),
                    "domainTag" to entry.domainTag.take(60),
                    "graphType" to entry.graphType,
                    "anchorX" to entry.anchorX,
                    "anchorY" to entry.anchorY,
                    "orderIndex" to entry.orderIndex,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val batch = db.batch()
                batch.set(docRef, payload)
                batch.update(
                    nbRef,
                    mapOf(
                        "entryCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            } else {
                val updates = mapOf(
                    "expression" to entry.expression.take(1000),
                    "result" to entry.result.take(4000),
                    "steps" to entry.steps.take(30),
                    "domainTag" to entry.domainTag.take(60),
                    "graphType" to entry.graphType,
                    "anchorX" to entry.anchorX,
                    "anchorY" to entry.anchorY,
                    "orderIndex" to entry.orderIndex,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(notebookId: String, entryId: String): Result<Unit> {
        val uid = requireUserId()
        require(notebookId.isNotBlank() && entryId.isNotBlank())
        val nbRef = db.collection("users").document(uid).collection("notebooks").document(notebookId)
        val entryRef = nbRef.collection("entries").document(entryId)
        return try {
            entryRef.delete().await()
            nbRef.update(
                mapOf(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, entryRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveStroke(notebookId: String, stroke: InkStrokeDoc): Result<String> {
        val uid = requireUserId()
        require(notebookId.isNotBlank()) { "Notebook ID must not be blank" }
        require(stroke.points.isNotEmpty()) { "Stroke must have at least one point" }
        val nbRef = db.collection("users").document(uid).collection("notebooks").document(notebookId)
        val docRef = if (stroke.id.isNotBlank()) {
            nbRef.collection("strokes").document(stroke.id)
        } else {
            nbRef.collection("strokes").document()
        }
        val payload = mapOf(
            "userId" to uid,
            "notebookId" to notebookId,
            "colorArgb" to stroke.colorArgb,
            "strokeWidth" to stroke.strokeWidth,
            "toolType" to stroke.toolType,
            "points" to stroke.points.take(4000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStroke(notebookId: String, strokeId: String): Result<Unit> {
        val uid = requireUserId()
        val strokeRef = db.collection("users").document(uid)
            .collection("notebooks").document(notebookId)
            .collection("strokes").document(strokeId)
        return try {
            strokeRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, strokeRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearStrokes(notebookId: String): Result<Unit> {
        val uid = requireUserId()
        val strokesCol = db.collection("users").document(uid)
            .collection("notebooks").document(notebookId)
            .collection("strokes")
        return try {
            val snapshot = strokesCol.get().await()
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, strokesCol.path)
            Result.failure(e)
        }
    }
}
