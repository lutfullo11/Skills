package ru.zhivo.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import ru.zhivo.app.core.*
import ru.zhivo.app.data.*

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val context get()=ApplicationProvider.getApplicationContext<ZhivoApplication>()
    @Test fun sqliteMigrationPreservesInterruptedLesson(){
        val name="migration-test.db";context.deleteDatabase(name)
        val course=context.course;val lesson=course.lessons.first()
        val state=StudyState(session=Session(lesson.id,index=3,answers=mapOf(lesson.tasks.first().id to Answer(1,draft="неверно"))))
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use{db->db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY,json TEXT NOT NULL)");db.execSQL("INSERT INTO state VALUES(1,?)",arrayOf(StateCodec.encode(state)));db.version=1}
        StudyDatabase(context,name).use{db->assertEquals(state,StateCodec.decode(db.read()!!,course));db.write(StateCodec.encode(state),true)}
        StudyDatabase(context,name).use{db->assertEquals(state,StateCodec.decode(db.read()!!,course));assertEquals(2,db.readableDatabase.version)}
        context.deleteDatabase(name)
    }
    @Test fun corruptedImportLeavesPersistedStateIntact()=runBlocking{
        val store=StudyStore(context,context.course);val before=store.export()
        assertTrue(runCatching{store.import("{invalid}")}.isFailure)
        assertEquals(before,store.export())
    }
}
