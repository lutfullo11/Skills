package ru.zhivo.app.core

enum class TaskType { CHOICE, FILL, ORDER, LISTEN, DICTATION, MATCH, CORRECT, READING, SPEAK }
data class Vocabulary(val id:String,val word:String,val meaning:String,val example:String,val english:String="",val lessonId:String="")
data class Task(val id:String,val type:TaskType,val prompt:String,val answers:List<String> = emptyList(),
    val options:List<String> = emptyList(),val explanation:String,val audio:String="",val passage:String="",
    val pairs:List<Pair<String,String>> = emptyList(),val strictYo:Boolean=false,val stress:Boolean=false) {
    val graded get()=type!=TaskType.SPEAK
}
data class Lesson(val id:String,val stage:String,val title:String,val goal:String,val skill:String,val topic:String,
    val theory:String,val examples:List<String>,val words:List<String>,val tasks:List<Task>,val minutes:Int=8)
data class Stage(val id:String,val title:String,val subtitle:String,val description:String)
data class Reply(val text:String,val feedback:String,val next:String,val success:Boolean)
data class DialogueNode(val id:String,val speaker:String,val line:String,val replies:List<Reply>)
data class Dialogue(val id:String,val title:String,val level:String,val goal:String,val tip:String,val nodes:List<DialogueNode>)
data class Course(val stages:List<Stage>,val lessons:List<Lesson>,val words:List<Vocabulary>,val dialogues:List<Dialogue>) {
    val lessonsById by lazy {lessons.associateBy{it.id}}
    val wordsById by lazy {words.associateBy{it.id}}
    val tasksById by lazy {lessons.flatMap{it.tasks}.associateBy{it.id}}
    fun errors():List<String> = buildList {
        fun duplicate(ids:List<String>,name:String){if(ids.size!=ids.toSet().size)add("Повторный id: $name")}
        duplicate(lessons.map{it.id},"урок");duplicate(words.map{it.id},"слово");duplicate(lessons.flatMap{it.tasks}.map{it.id},"задание")
        for(l in lessons){
            if(l.stage !in stages.map{it.id})add("Нет раздела: ${l.id}")
            if(l.theory.isBlank()||l.goal.isBlank()||l.examples.size<2||l.tasks.size<7)add("Неполный урок: ${l.id}")
            if(l.words.any{it !in wordsById})add("Нет слова: ${l.id}")
            for(t in l.tasks){
                if(t.prompt.isBlank()||t.explanation.isBlank())add("Нет объяснения: ${t.id}")
                if(t.graded&&t.answers.isEmpty()&&t.type!=TaskType.MATCH)add("Нет ответа: ${t.id}")
                if(t.type in setOf(TaskType.CHOICE,TaskType.LISTEN,TaskType.READING)&&
                    (t.options.size<3||t.options.distinct().size!=t.options.size||t.answers.any{it !in t.options}))add("Варианты: ${t.id}")
                if(t.type in setOf(TaskType.LISTEN,TaskType.DICTATION)&&t.audio.isBlank())add("Нет аудиотекста: ${t.id}")
                if(t.type==TaskType.READING&&t.passage.isBlank())add("Нет текста: ${t.id}")
                if(t.type==TaskType.MATCH&&(t.pairs.size<3||t.pairs.map{it.second}.distinct().size!=t.pairs.size))add("Пары: ${t.id}")
            }
        }
        for(d in dialogues){
            val ids=d.nodes.map{it.id};duplicate(ids,"реплика ${d.id}")
            if("start" !in ids)add("Нет начала: ${d.id}")
            for(n in d.nodes)if(n.replies.any{it.next !in ids&&it.next!="end"})add("Нет ветви: ${d.id}/${n.id}")
        }
    }
}

data class Profile(val name:String="",val nativeLanguage:String="Таджикский",val goal:String="Каждый день",val dailyMinutes:Int=10,
    val startStage:String="start",val theme:String="system",val onboarded:Boolean=false,val reminder:Boolean=false,val reminderHour:Int=19,val reminderMinute:Int=0,
    val networkVoice:Boolean=false,val diagnostic:String="")
data class Answer(val attempts:Int=0,val firstCorrect:Boolean=false,val solved:Boolean=false,val revealed:Boolean=false,
    val unassessed:Boolean=false,val transcript:Boolean=false,val draft:String="")
data class Session(val lessonId:String,val index:Int=-1,val answers:Map<String,Answer> = emptyMap(),val startedAt:Long=0,val finished:Boolean=false) {
    fun respond(task:Task,input:String,reveal:Boolean=false,skip:Boolean=false,transcript:Boolean=false):Session {
        val old=answers[task.id]?:Answer()
        if(old.solved||old.unassessed)return this
        val correct=skip||reveal||AnswerChecker.matches(task,input)
        val textFallback=transcript&&task.type in setOf(TaskType.LISTEN,TaskType.DICTATION)
        val next=old.copy(attempts=old.attempts+1,firstCorrect=old.attempts==0&&correct&&!skip&&!reveal&&!textFallback,
            solved=correct&&!skip,unassessed=skip||!task.graded||(textFallback&&correct),revealed=old.revealed||reveal,
            transcript=transcript,draft=input)
        return copy(answers=answers+(task.id to next))
    }
}
data class LessonProgress(val viewedAt:Long=0,val completedAt:Long=0,val bestCorrect:Int=0,val bestAssessed:Int=0,val verifiedAt:Long=0)
data class ReviewCard(val due:Long=0,val intervalDays:Int=0,val repetitions:Int=0,val ease:Double=2.5,val lapses:Int=0,
    val introducedAt:Long=0,val verifiedAt:Long=0,val lastReviewedAt:Long=0)
data class StudyEvent(val id:String,val at:Long,val milliseconds:Long,val kind:String)
data class CustomWord(val id:String,val word:String,val meaning:String,val example:String)
data class StudyState(val version:Int=2,val profile:Profile=Profile(),val progress:Map<String,LessonProgress> = emptyMap(),
    val session:Session?=null,val reviews:Map<String,ReviewCard> = emptyMap(),val favorites:Set<String> = emptySet(),
    val errors:Map<String,Int> = emptyMap(),val customWords:List<CustomWord> = emptyList(),val events:List<StudyEvent> = emptyList(),
    val dialogueWins:Set<String> = emptySet(),val dialogueNodes:Map<String,String> = emptyMap())
