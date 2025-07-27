package me.znotchill.fuse.database

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

data class RowRef(
    val table: UUIDTable,
    val id: UUID
)