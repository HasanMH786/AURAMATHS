package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class MathNotebook(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Untitled Sheet",
    val entryCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MathEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "",
    val expression: String = "",
    val result: String = "",
    val steps: List<String> = emptyList(),
    val domainTag: String = "Algebra",
    val graphType: String = "NONE",
    val anchorX: Double = 0.0,
    val anchorY: Double = 0.0,
    val orderIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InkStrokeDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "",
    val colorArgb: Long = 0xFF38BDF8L,
    val strokeWidth: Double = 4.5,
    val toolType: String = "PEN",
    val points: List<Map<String, Double>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
