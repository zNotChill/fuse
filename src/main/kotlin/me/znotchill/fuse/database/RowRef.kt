package me.znotchill.fuse.database

import org.jetbrains.exposed.v1.core.dao.id.IdTable

data class RowRef<ID : Comparable<ID>>(
    val table: IdTable<ID>,
    val id: ID
)