package ru.zhivo.app.core

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

object AnswerChecker {
    fun normalize(raw:String,strictYo:Boolean=false,stress:Boolean=false):String {
        var s=raw.lowercase(Locale.ROOT).replace('ё',if(strictYo)'ё' else 'е')
        if(!stress)s=s.replace("\u0301","")
        // Restore composed letters after canonicalizing decomposed input, without dropping й/ё diacritics.
        s=Normalizer.normalize(s,Normalizer.Form.NFC)
        if(!strictYo)s=s.replace('ё','е')
        return s.replace(Regex("[.,!?;:\"«»“”()—–]")," ").replace(Regex("\\s+")," ").trim()
    }
    fun distinguishesYo(t:Task):Boolean = t.strictYo||t.answers.any{a->a.lowercase(Locale.ROOT).split(Regex("[^\\p{L}]+" )).any{it=="все"||it=="всё"}}
    fun matches(t:Task,input:String):Boolean {
        if(t.type==TaskType.MATCH){
            val lines=input.split("\n")
            val expected=t.pairs.map{(a,b)->"$a\t$b"}
            return lines.size==expected.size && lines.toSet()==expected.toSet()
        }
        val distinguishYo=distinguishesYo(t)
        return t.answers.any{normalize(input,distinguishYo,t.stress)==normalize(it,distinguishYo,t.stress)}
    }
}

object ReviewScheduler {
    const val DAY=86_400_000L
    /** Transparent SM-2-derived scheduler, not a claim to implement FSRS. */
    fun rate(old:ReviewCard,quality:Int,now:Long,objective:Boolean=true):ReviewCard {
        require(quality in 0..5)
        val q=quality.toDouble()
        val ease=(old.ease+0.1-(5-q)*(0.08+(5-q)*0.02)).coerceIn(1.3,3.0)
        val passed=quality>=3
        val interval=if(!passed)0 else when(old.repetitions){0->1;1->3;else->(old.intervalDays*ease).toInt().coerceIn(1,365)}
        val verified=if(!passed)0L else if(objective&&quality>=4&&old.introducedAt>0&&now-old.introducedAt>=DAY)now else old.verifiedAt
        return old.copy(due=now+(if(passed)interval*DAY else 10*60_000L),intervalDays=interval,
            repetitions=if(passed)old.repetitions+1 else 0,ease=ease,lapses=old.lapses+if(passed)0 else 1,
            introducedAt=old.introducedAt.takeIf{it>0}?:now,verifiedAt=verified,lastReviewedAt=now)
    }
}
object Learning {
    fun finish(state:StudyState,lesson:Lesson,now:Long):StudyState {
        val session=state.session?:return state
        if(session.lessonId!=lesson.id||session.finished)return state
        if(lesson.tasks.any{session.answers[it.id]?.let{a->a.solved||a.unassessed}!=true})return state
        val assessed=lesson.tasks.filter{it.graded&&session.answers[it.id]?.unassessed==false}
        val correct=assessed.count{session.answers[it.id]?.firstCorrect==true}
        val old=state.progress[lesson.id]?:LessonProgress()
        val improved=correct.toDouble()/assessed.size.coerceAtLeast(1) > old.bestCorrect.toDouble()/old.bestAssessed.coerceAtLeast(1)
        val progress=old.copy(completedAt=old.completedAt.takeIf{it>0}?:now,
            bestCorrect=if(improved||old.bestAssessed==0)correct else old.bestCorrect,
            bestAssessed=if(improved||old.bestAssessed==0)assessed.size else old.bestAssessed)
        val reviews=state.reviews.toMutableMap()
        for(id in lesson.words)if(id !in reviews)reviews[id]=ReviewCard(due=now+ReviewScheduler.DAY,introducedAt=now)
        for(t in lesson.tasks.filter{it.graded&&it.type!=TaskType.MATCH}){
            val a=session.answers[t.id]?:continue
            if(a.unassessed)continue
            if(t.id !in reviews)reviews[t.id]=ReviewScheduler.rate(ReviewCard(introducedAt=now),if(a.firstCorrect)4 else 1,now)
        }
        return state.copy(progress=state.progress+(lesson.id to progress),reviews=reviews,session=session.copy(finished=true))
    }
    fun due(state:StudyState,now:Long):List<String> = state.reviews.filterValues{it.due<=now}.toList().sortedBy{it.second.due}.map{it.first}
    fun recommend(course:Course,state:StudyState):Lesson? {
        state.session?.takeIf{!it.finished}?.let{s->course.lessonsById[s.lessonId]?.let{return it}}
        val stageIndex=course.stages.indexOfFirst{it.id==state.profile.startStage}.coerceAtLeast(0)
        val pool=course.lessons.filter{course.stages.indexOfFirst{s->s.id==it.stage}>=stageIndex&&state.progress[it.id]?.completedAt?.let{t->t>0}!=true}
        val first=pool.firstOrNull()?:return course.lessons.firstOrNull{state.progress[it.id]?.completedAt?.let{t->t>0}!=true}
        val stageLessons=course.lessons.filter{it.stage==first.stage}
        val foundationCount=when(first.stage){"start"->8;"a1"->8;"a2"->14;else->6}
        val incomplete=pool.filter{it.stage==first.stage}
        incomplete.firstOrNull{stageLessons.indexOf(it)<foundationCount}?.let{return it}
        val topics=when(state.profile.goal){
            "Работа"->listOf("Работа","Телефон","Услуги")
            "Учёба"->listOf("Образование","Знакомство","Услуги")
            "Путешествия"->listOf("Транспорт","Жильё","Покупки")
            else->listOf("Каждый день","Знакомство","Покупки","Жильё")
        }
        return incomplete.minByOrNull{topics.indexOf(it.topic).takeIf{i->i>=0}?:99}?:first
    }
    fun day(at:Long,zone:ZoneId)=Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    fun minutes(events:List<StudyEvent>,date:LocalDate,zone:ZoneId)=events.filter{day(it.at,zone)==date}.sumOf{it.milliseconds}/60_000.0
    fun nextReminder(now:Long,zone:ZoneId,hour:Int,minute:Int):Long {
        require(hour in 0..23&&minute in 0..59)
        val current=Instant.ofEpochMilli(now).atZone(zone)
        var target=current.toLocalDate().atTime(LocalTime.of(hour,minute)).atZone(zone)
        if(!target.isAfter(current))target=current.toLocalDate().plusDays(1).atTime(LocalTime.of(hour,minute)).atZone(zone)
        return target.toInstant().toEpochMilli()
    }
    fun diagnostic(correctByStage:Map<String,Pair<Int,Int>>):String = when {
        (correctByStage["a1"]?.let{it.second>=3&&it.first.toDouble()/it.second>=0.75}!=true)->"a1"
        (correctByStage["a2"]?.let{it.second>=3&&it.first.toDouble()/it.second>=0.75}!=true)->"a2"
        (correctByStage["b1"]?.let{it.second>=3&&it.first.toDouble()/it.second>=0.75}!=true)->"b1"
        else->"b2"
    }
}
