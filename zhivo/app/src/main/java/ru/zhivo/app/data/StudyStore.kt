package ru.zhivo.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.zhivo.app.core.*
import java.util.UUID

class StudyDatabase(context:Context,name:String="zhivo-study.db"):SQLiteOpenHelper(context,name,null,3){
    private val chunkSize=64*1024
    private fun chunks(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE IF NOT EXISTS state_chunks (id INTEGER PRIMARY KEY, data BLOB NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS recovery_chunks (snapshot INTEGER NOT NULL, part INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(snapshot,part))")
    }
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE state (id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE recovery (id INTEGER PRIMARY KEY, saved_at INTEGER NOT NULL, json TEXT NOT NULL)")
        chunks(db)
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){
        if(oldVersion<2)db.execSQL("CREATE TABLE IF NOT EXISTS recovery (id INTEGER PRIMARY KEY, saved_at INTEGER NOT NULL, json TEXT NOT NULL)")
        if(oldVersion<3){
            chunks(db)
            // SQL substr reads the legacy value without placing a multi-megabyte row in CursorWindow.
            val size=db.rawQuery("SELECT length(CAST(json AS BLOB)) FROM state WHERE id=1",null).use{if(it.moveToFirst())it.getInt(0) else 0}
            for(offset in 0 until size step chunkSize)db.execSQL("INSERT INTO state_chunks(id,data) SELECT ?,substr(CAST(json AS BLOB),?,?) FROM state WHERE id=1",arrayOf(offset/chunkSize,offset+1,chunkSize))
            if(size>0)db.delete("state",null,null)
        }
    }
    fun read():String?{
        val out=java.io.ByteArrayOutputStream()
        readableDatabase.rawQuery("SELECT data FROM state_chunks ORDER BY id",null).use{cursor->while(cursor.moveToNext())out.write(cursor.getBlob(0))}
        return if(out.size()==0)null else out.toString(Charsets.UTF_8.name())
    }
    fun write(json:String,recovery:Boolean=false){
        val bytes=json.toByteArray(Charsets.UTF_8);require(bytes.size<=32_000_000){"Данные превышают 32 МБ"}
        val db=writableDatabase;db.beginTransaction()
        try{
            if(recovery){
                val snapshot=System.currentTimeMillis()
                db.execSQL("INSERT INTO recovery_chunks(snapshot,part,data) SELECT ?,id,data FROM state_chunks",arrayOf(snapshot))
                db.execSQL("DELETE FROM recovery_chunks WHERE snapshot NOT IN (SELECT DISTINCT snapshot FROM recovery_chunks ORDER BY snapshot DESC LIMIT 2)")
            }
            db.delete("state_chunks",null,null)
            for(offset in bytes.indices step chunkSize){
                val data=bytes.copyOfRange(offset,minOf(offset+chunkSize,bytes.size))
                check(db.insertOrThrow("state_chunks",null,ContentValues().apply{put("id",offset/chunkSize);put("data",data)})!=-1L){"Не удалось сохранить прогресс"}
            }
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    fun clearRecovery(){writableDatabase.delete("recovery",null,null);writableDatabase.delete("recovery_chunks",null,null)}
}
class StudyStore(context:Context,val course:Course){
    private val db=StudyDatabase(context.applicationContext)
    private val mutex=Mutex()
    var loadError:String?=null;private set
    private val current=MutableStateFlow(runCatching{db.read()?.let{StateCodec.decode(it,course)}?:StudyState()}.getOrElse{
        loadError="Не удалось прочитать прогресс. Старый файл сохранён; не импортируйте новую копию до проверки резервной копии. ${it.message}";StudyState()
    })
    val state=current.asStateFlow()
    suspend fun edit(f:(StudyState)->StudyState)=mutex.withLock{
        check(loadError==null){"Сначала восстановите данные из проверенной копии"}
        val next=f(current.value)
        if(next!=current.value){withContext(Dispatchers.IO){db.write(StateCodec.encode(next))};current.value=next}
    }
    suspend fun import(text:String)=mutex.withLock{
        val next=withContext(Dispatchers.Default){StateCodec.decode(text,course)}
        withContext(Dispatchers.IO){db.write(StateCodec.encode(next),true)};loadError=null;current.value=next
    }
    suspend fun reset()=mutex.withLock{withContext(Dispatchers.IO){db.write(StateCodec.encode(StudyState()));db.clearRecovery()};loadError=null;current.value=StudyState()}
    fun export()=StateCodec.encode(current.value)
    suspend fun profile(p:Profile)=edit{it.copy(profile=p)}
    suspend fun start(id:String){val lesson=course.lessonsById.getValue(id);val now=System.currentTimeMillis()
        edit{s->if(s.session?.lessonId==id&&s.session.finished.not())s else s.copy(session=Session(id,startedAt=now),progress=s.progress+(id to (s.progress[id]?:LessonProgress()).let{p->p.copy(viewedAt=p.viewedAt.takeIf{it>0}?:now)}))}
    }
    suspend fun index(value:Int)=edit{s->val v=s.session?:return@edit s;val lesson=course.lessonsById.getValue(v.lessonId);s.copy(session=v.copy(index=value.coerceIn(-1,lesson.tasks.size)))}
    suspend fun draft(id:String,text:String)=edit{s->val v=s.session?:return@edit s;val a=v.answers[id]?:Answer();if(a.solved||a.unassessed)s else s.copy(session=v.copy(answers=v.answers+(id to a.copy(draft=text.take(10000)))))}
    suspend fun answer(task:Task,input:String,reveal:Boolean=false,skip:Boolean=false,transcript:Boolean=false)=edit{s->
        val v=s.session?:return@edit s
        if(course.lessonsById[v.lessonId]?.tasks?.none{it.id==task.id}!=false||v.finished)return@edit s
        val next=v.respond(task,input,reveal,skip,transcript);val old=v.answers[task.id];val a=next.answers[task.id]
        val error=next!=v&&!reveal&&!skip&&a?.solved==false&&a?.attempts!=old?.attempts
        s.copy(session=next,errors=if(error)s.errors+(task.id to ((s.errors[task.id]?:0)+1)) else s.errors)
    }
    suspend fun complete()=edit{s->s.session?.let{Learning.finish(s,course.lessonsById.getValue(it.lessonId),System.currentTimeMillis())}?:s}
    suspend fun favorite(id:String)=edit{it.copy(favorites=if(id in it.favorites)it.favorites-id else it.favorites+id)}
    suspend fun custom(word:String,meaning:String,example:String)=edit{s->
        require(word.isNotBlank()&&word.length<=200&&meaning.isNotBlank()&&meaning.length<=500&&example.length<=1000){"Проверьте длину и содержание слова"}
        require(s.customWords.size<5000){"Достигнут предел: 5000 личных слов"}
        val id="custom_"+UUID.randomUUID().toString();val now=System.currentTimeMillis()
        s.copy(customWords=s.customWords+CustomWord(id,word.trim(),meaning.trim(),example.trim()),reviews=s.reviews+(id to ReviewCard(due=now,introducedAt=now)),favorites=s.favorites+id)
    }
    suspend fun deleteWord(id:String)=edit{s->s.copy(customWords=s.customWords.filterNot{it.id==id},reviews=s.reviews-id,favorites=s.favorites-id)}
    suspend fun review(id:String,quality:Int,objective:Boolean)=edit{s->
        val now=System.currentTimeMillis();val old=s.reviews[id]?:ReviewCard(introducedAt=now)
        val card=ReviewScheduler.rate(old,quality,now,objective)
        val reviews=s.reviews+(id to card)
        val progress=s.progress.toMutableMap()
        if(objective)for(lesson in course.lessons.filter{l->l.tasks.any{it.id==id}}){
            val verified=lesson.tasks.count{reviews[it.id]?.verifiedAt?.let{t->t>0}==true}
            val p=progress[lesson.id]?:continue
            progress[lesson.id]=p.copy(verifiedAt=if(verified>=3&&p.completedAt>0)now else 0L)
        }
        s.copy(reviews=reviews,progress=progress,errors=if(objective&&quality<3&&id in course.tasksById)s.errors+(id to ((s.errors[id]?:0)+1)) else s.errors)
    }
    suspend fun dialogue(id:String,node:String?,success:Boolean)=edit{s->s.copy(dialogueNodes=if(node==null)s.dialogueNodes-id else s.dialogueNodes+(id to node),dialogueWins=if(success)s.dialogueWins+id else s.dialogueWins)}
    suspend fun time(milliseconds:Long,kind:String)=edit{s->if(milliseconds<1000)s else s.copy(events=(s.events+StudyEvent(UUID.randomUUID().toString(),System.currentTimeMillis(),milliseconds.coerceAtMost(3_600_000),kind)).takeLast(30000))}
}
