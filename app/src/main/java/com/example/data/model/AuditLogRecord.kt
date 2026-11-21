package com.example.data.model

data class AuditLogRecord(
    val id: String,
    val userEmail: String,
    val action: String,
    val details: String,
    val createdAt: String
)
