package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

interface AppDatabase {
    val noteDao: NoteDao
    val noteEmbeddingDao: NoteEmbeddingDao
    val noteVersionDao: NoteVersionDao
    val taskVersionDao: TaskVersionDao
    val taskDao: TaskDao
    val chatDao: ChatDao
    val chatConversationDao: ChatConversationDao
    val notebookDao: NotebookDao
}

expect fun createAppDatabase(context: PlatformContext): AppDatabase
