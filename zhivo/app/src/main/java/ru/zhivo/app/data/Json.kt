package ru.zhivo.app.data

import org.json.JSONArray
import org.json.JSONObject
import ru.zhivo.app.core.*

private fun JSONArray.strings()=(0 until length()).map{getString(it)}
private fun JSONObject.strings(k:String)=optJSONArray(k)?.strings().orEmpty()
private fun <T> JSONObject.list(k:String,f:(JSONObject)->T):List<T> = optJSONArray(k)?.let{a->(0 until a.length()).map{f(a.getJSONObject(it))}}.orEmpty()
private fun <T> JSONObject.map(k:String,f:(JSONObject)->T):Map<String,T> = optJSONObject(k)?.let{o->o.keys().asSequence().associateWith{f(o.getJSONObject(it))}}.orEmpty()
private fun obj(vararg p:Pair<String,Any?>)=JSONObject().apply{p.forEach{(k,v)->put(k,v?:JSONObject.NULL)}}
private fun <T> mapped(m:Map<String,T>,f:(T)->JSONObject)=JSONObject().apply{m.forEach{(k,v)->put(k,f(v))}}

object CourseCodec {
    fun decode(text:String):Course {
        val o=JSONObject(text);require(o.getInt("schema")==1){"Неизвестная версия курса"}
        val course=Course(o.list("stages"){Stage(it.getString("id"),it.getString("title"),it.getString("subtitle"),it.getString("description"))},
            o.list("lessons"){l->Lesson(l.getString("id"),l.getString("stage"),l.getString("title"),l.getString("goal"),l.getString("skill"),l.getString("topic"),l.getString("theory"),l.strings("examples"),l.strings("words"),l.list("tasks"){t->
                Task(t.getString("id"),TaskType.valueOf(t.getString("type")),t.getString("prompt"),t.strings("answers"),t.strings("options"),t.getString("explanation"),t.optString("audio"),t.optString("passage"),t.optJSONArray("pairs")?.let{a->(0 until a.length()).map{a.getJSONArray(it).let{p->p.getString(0) to p.getString(1)}}}.orEmpty(),t.optBoolean("strictYo"),t.optBoolean("stress"))
            },l.optInt("minutes",8))},
            o.list("words"){Vocabulary(it.getString("id"),it.getString("word"),it.getString("meaning"),it.getString("example"),it.optString("english"),it.optString("lessonId"))},
            o.list("dialogues"){d->Dialogue(d.getString("id"),d.getString("title"),d.getString("level"),d.getString("goal"),d.getString("tip"),d.list("nodes"){n->DialogueNode(n.getString("id"),n.getString("speaker"),n.getString("line"),n.list("replies"){r->Reply(r.getString("text"),r.getString("feedback"),r.getString("next"),r.getBoolean("success"))})})})
        require(course.errors().isEmpty()){course.errors().joinToString("\n")}
        return course
    }
}
object StateCodec {
    private fun answer(a:Answer)=obj("attempts" to a.attempts,"firstCorrect" to a.firstCorrect,"solved" to a.solved,"revealed" to a.revealed,"unassessed" to a.unassessed,"transcript" to a.transcript,"draft" to a.draft)
    private fun card(c:ReviewCard)=obj("due" to c.due,"intervalDays" to c.intervalDays,"repetitions" to c.repetitions,"ease" to c.ease,"lapses" to c.lapses,"introducedAt" to c.introducedAt,"verifiedAt" to c.verifiedAt,"lastReviewedAt" to c.lastReviewedAt)
    fun encode(s:StudyState):String {
        val p=s.profile
        return obj("format" to "zhivo-backup","version" to 2,"profile" to obj("name" to p.name,"nativeLanguage" to p.nativeLanguage,"goal" to p.goal,"dailyMinutes" to p.dailyMinutes,"startStage" to p.startStage,"theme" to p.theme,"onboarded" to p.onboarded,"reminder" to p.reminder,"reminderHour" to p.reminderHour,"reminderMinute" to p.reminderMinute,"networkVoice" to p.networkVoice,"diagnostic" to p.diagnostic),
            "progress" to mapped(s.progress){v->obj("viewedAt" to v.viewedAt,"completedAt" to v.completedAt,"bestCorrect" to v.bestCorrect,"bestAssessed" to v.bestAssessed,"verifiedAt" to v.verifiedAt)},
            "session" to s.session?.let{v->obj("lessonId" to v.lessonId,"index" to v.index,"answers" to mapped(v.answers,::answer),"startedAt" to v.startedAt,"finished" to v.finished)},
            "reviews" to mapped(s.reviews,::card),"favorites" to JSONArray(s.favorites.toList()),"errors" to JSONObject(s.errors),
            "customWords" to JSONArray(s.customWords.map{obj("id" to it.id,"word" to it.word,"meaning" to it.meaning,"example" to it.example)}),
            "events" to JSONArray(s.events.map{obj("id" to it.id,"at" to it.at,"milliseconds" to it.milliseconds,"kind" to it.kind)}),
            "dialogueWins" to JSONArray(s.dialogueWins.toList()),"dialogueNodes" to JSONObject(s.dialogueNodes)).toString()
    }
    fun decode(text:String,course:Course):StudyState {
        require(text.toByteArray(Charsets.UTF_8).size<=5_000_000){"Файл больше 5 МБ"}
        val o=JSONObject(text)
        require(o.optString("format")=="zhivo-backup"){"Это не резервная копия «Живо»"}
        require(o.getInt("version") in 1..2){"Эта копия создана другой версией приложения"}
        val p=o.getJSONObject("profile")
        val profile=Profile(p.optString("name").take(80),p.optString("nativeLanguage","Другой").take(80),p.optString("goal","Каждый день").take(100),p.optInt("dailyMinutes",10),p.optString("startStage","start"),p.optString("theme","system"),p.optBoolean("onboarded"),p.optBoolean("reminder"),p.optInt("reminderHour",19),p.optInt("reminderMinute"),p.optBoolean("networkVoice"),p.optString("diagnostic").take(300))
        require(profile.dailyMinutes in 5..120 && profile.reminderHour in 0..23 && profile.reminderMinute in 0..59){"Некорректные настройки времени"}
        require(profile.startStage in course.stages.map{it.id}&&profile.theme in setOf("light","dark","system")){"Неизвестный раздел или тема"}
        val progress=o.map("progress"){v->LessonProgress(v.optLong("viewedAt"),v.optLong("completedAt"),v.optInt("bestCorrect"),v.optInt("bestAssessed"),v.optLong("verifiedAt"))}
        require(progress.keys.all{it in course.lessonsById}){"В копии есть неизвестные уроки"}
        progress.forEach{(id,v)->require(v.bestCorrect in 0..v.bestAssessed&&v.bestAssessed<=course.lessonsById.getValue(id).tasks.count{it.graded}){"Некорректный результат урока"}}
        val session=o.optJSONObject("session")?.let{v->
            val id=v.getString("lessonId");val lesson=course.lessonsById[id]?:error("Урок из копии не найден")
            val index=v.optInt("index",-1);require(index in -1..lesson.tasks.size){"Некорректный шаг урока"}
            val answers=v.map("answers"){a->Answer(a.optInt("attempts"),a.optBoolean("firstCorrect"),a.optBoolean("solved"),a.optBoolean("revealed"),a.optBoolean("unassessed"),a.optBoolean("transcript"),a.optString("draft"))}
            require(answers.keys.all{key->lesson.tasks.any{it.id==key}}&&answers.values.all{it.attempts in 0..10_000&&it.draft.length<=10_000}){"Некорректные ответы в копии"}
            Session(id,index,answers,v.optLong("startedAt"),v.optBoolean("finished"))
        }
        val custom=o.list("customWords"){CustomWord(it.getString("id"),it.getString("word"),it.getString("meaning"),it.optString("example"))}
        require(custom.size<=5000&&custom.map{it.id}.distinct().size==custom.size&&custom.all{it.id.startsWith("custom_")&&it.word.isNotBlank()&&it.word.length<=200&&it.meaning.isNotBlank()&&it.meaning.length<=500&&it.example.length<=1000}){"Некорректный личный словарь"}
        val reviewIds=course.tasksById.keys+course.wordsById.keys+custom.map{it.id}
        val reviews=o.map("reviews"){v->ReviewCard(v.getLong("due"),v.optInt("intervalDays"),v.optInt("repetitions"),v.optDouble("ease",2.5),v.optInt("lapses"),v.optLong("introducedAt"),v.optLong("verifiedAt"),v.optLong("lastReviewedAt"))}
        require(reviews.keys.all{it in reviewIds}&&reviews.values.all{it.due>=0&&it.intervalDays in 0..365&&it.repetitions in 0..10000&&it.ease in 1.3..3.0&&it.lapses>=0}){"Некорректное расписание"}
        val errors=o.optJSONObject("errors")?.let{v->v.keys().asSequence().associateWith{v.getInt(it)}}.orEmpty()
        require(errors.keys.all{it in course.tasksById}&&errors.values.all{it in 0..100000}){"Некорректная история ошибок"}
        val events=o.list("events"){StudyEvent(it.getString("id"),it.getLong("at"),it.getLong("milliseconds"),it.getString("kind"))}
        require(events.size<=30000&&events.all{it.at>=0&&it.milliseconds in 0..3_600_000}){"Некорректная история занятий"}
        val wins=o.strings("dialogueWins").toSet()
        val nodes=o.optJSONObject("dialogueNodes")?.let{v->v.keys().asSequence().associateWith{v.getString(it)}}.orEmpty()
        require(wins.all{id->course.dialogues.any{it.id==id}}&&nodes.all{(id,node)->course.dialogues.any{it.id==id&&it.nodes.any{n->n.id==node}}}){"Неизвестный диалог"}
        val favorites=o.strings("favorites").toSet();require(favorites.all{it in course.wordsById||it in custom.map{w->w.id}}){"Неизвестные слова"}
        return StudyState(profile=profile,progress=progress,session=session,reviews=reviews,favorites=favorites,errors=errors,customWords=custom,events=events,dialogueWins=wins,dialogueNodes=nodes)
    }
}
