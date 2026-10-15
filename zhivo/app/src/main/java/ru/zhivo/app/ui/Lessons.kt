package ru.zhivo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.zhivo.app.MainActivity
import ru.zhivo.app.core.*
import ru.zhivo.app.data.StudyStore
import ru.zhivo.app.media.AudioEngine
import kotlin.random.Random

@Composable fun LessonScreen(lesson:Lesson,state:StudyState,store:StudyStore,audio:AudioEngine,activity:MainActivity,busy:Boolean,run:(suspend()->Unit)->Unit,back:()->Unit){
    val session=state.session?.takeIf{it.lessonId==lesson.id}
    if(session==null){Page("Занятие",back=back){Action("Открыть урок"){run{store.start(lesson.id)}}};return}
    val index=session.index
    if(session.finished||index>=lesson.tasks.size){
        val p=state.progress[lesson.id];Page("Хорошая работа",back=back){Panel(tonal=true){Label("ЗАНЯТИЕ ЗАВЕРШЕНО");Head(lesson.title)
            Text("${session.answers.values.count{it.firstCorrect&&!it.unassessed}} ответов с первой попытки. Задания с пропуском, открытым ответом или самооценкой речи не подтверждают освоение.")
            Small("Лучший результат: ${p?.bestCorrect?:0} из ${p?.bestAssessed?:0} оценённых заданий. Повторная проверка позже покажет, что сохранилось в памяти.")}
            Head("Теперь своими словами");Text(lesson.goal);Text(lesson.tasks.last().prompt)
            Action("Вернуться к маршруту",onClick=back);Secondary("Пройти урок ещё раз",!busy){run{store.start(lesson.id)}}}
        return
    }
    if(index<0){Page(lesson.title,lesson.goal,back){Label("${lesson.topic} · ${lesson.skill}");Panel(tonal=true){Head("Разберём на примерах");Text(lesson.theory,style=MaterialTheme.typography.bodyLarge);lesson.examples.forEach{Text(it,fontSize=20.sp,fontWeight=FontWeight.Medium);TextButton(onClick={audio.speak(it)}){Text("Послушать пример")}}}
            AudioBar(audio,activity)
            Head("Слова в контексте");lesson.words.mapNotNull{store.course.wordsById[it]}.forEach{v->Panel{Head(v.word);Text(v.meaning);Small(v.example);if(state.profile.nativeLanguage=="Английский"&&v.english.isNotBlank())Small(v.english)}}
            Action("Попробовать · 9 заданий",!busy){run{store.index(0)}}}
        return
    }
    val task=lesson.tasks[index];val answer=session.answers[task.id]?:Answer()
    var input by rememberSaveable(task.id){mutableStateOf(answer.draft)}
    var transcript by rememberSaveable(task.id){mutableStateOf(answer.transcript)}
    var confirmation by remember(task.id){mutableStateOf(false)}
    val done=answer.solved||answer.unassessed
    LaunchedEffect(task.id,input){if(!done){delay(400);runCatching{store.draft(task.id,input)}}}
    Page("${index+1} из ${lesson.tasks.size}",lesson.title,back){
        LinearProgressIndicator(progress={(index.toFloat()/lesson.tasks.size)},modifier=Modifier.fillMaxWidth())
        Label(taskName(task.type));Head(task.prompt)
        if(task.strictYo)Small("В этом задании Е и Ё различаются по смыслу.")
        if(task.passage.isNotBlank())Panel{Text(task.passage,style=MaterialTheme.typography.bodyLarge)}
        if(task.audio.isNotBlank()){
            Secondary(if(task.type==TaskType.SPEAK)"Послушать образец" else "Послушать ещё раз"){audio.speak(task.audio)}
            AudioBar(audio,activity)
            if(task.type in setOf(TaskType.LISTEN,TaskType.DICTATION)){
                if(transcript)Panel{Text(task.audio);Small("Текстовая версия. Аудирование в этом задании не оценивается.")}
                else TextButton(onClick={transcript=true}){Text("Открыть текст · без оценки аудирования")}
            }
        }
        if(task.type==TaskType.SPEAK){
            SpeakingPanel(audio,activity)
            Text(task.explanation)
            Action(if(done)"Завершить занятие" else "Я сравнил свою запись",!busy){run{if(!done)store.answer(task,"",skip=true);audio.stop();store.index(index+1);if(index+1>=lesson.tasks.size)store.complete()}}
            if(!done)Secondary("Продолжить без записи",!busy){run{store.answer(task,"",skip=true);audio.stop();store.index(index+1);if(index+1>=lesson.tasks.size)store.complete()}}
        }else{
            TaskEditor(task,input,{input=it},!done&&!busy)
            if(answer.attempts>0){Panel(tonal=true){Head(when{answer.unassessed->"Продолжим без оценки";answer.revealed->"Разберём образец";answer.solved->if(answer.firstCorrect)"Верно" else "Получилось";else->"Попробуйте ещё раз"});Text(task.explanation)
                if(!answer.solved&&!answer.unassessed)Small("Сравните формы слов и смысл. Вы можете исправить ответ или отдельно открыть образец.")
                if(answer.revealed)Text(task.answers.firstOrNull()?:task.pairs.joinToString("\n"){"${it.first} — ${it.second}"},fontWeight=FontWeight.SemiBold)
            }}
            if(done)Action(if(index+1==lesson.tasks.size)"Завершить занятие" else "Дальше",!busy){run{audio.stop();store.index(index+1);if(index+1>=lesson.tasks.size)store.complete()}}
            else{
                Action("Проверить",!busy&&input.isNotBlank()){run{store.answer(task,input,transcript=transcript)}}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(enabled=!busy,onClick={confirmation=true}){Text("Показать ответ")};TextButton(enabled=!busy,onClick={run{store.answer(task,input,skip=true,transcript=transcript)}}){Text("Пропустить")}}
            }
        }
    }
    if(confirmation)AlertDialog(onDismissRequest={confirmation=false},title={Text("Открыть образец?")},text={Text("После открытия ответа задание останется доступным для разбора, но не будет считаться самостоятельным правильным ответом.")},confirmButton={TextButton(onClick={confirmation=false;run{store.answer(task,input,reveal=true,transcript=transcript)}}){Text("Показать")}},dismissButton={TextButton(onClick={confirmation=false}){Text("Попробую сам")}})
}
fun taskName(type:TaskType)=when(type){TaskType.CHOICE->"ВЫБОР ОТВЕТА";TaskType.FILL->"ФОРМА СЛОВА";TaskType.ORDER->"СОБЕРИТЕ ФРАЗУ";TaskType.LISTEN->"ПОНИМАНИЕ НА СЛУХ";TaskType.DICTATION->"ДИКТАНТ";TaskType.MATCH->"СЛОВА И ЗНАЧЕНИЯ";TaskType.CORRECT->"ИСПРАВЬТЕ ОШИБКУ";TaskType.READING->"ПОНИМАНИЕ ТЕКСТА";TaskType.SPEAK->"ГОВОРЕНИЕ · САМООЦЕНКА"}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun TaskEditor(task:Task,input:String,change:(String)->Unit,enabled:Boolean){
    when(task.type){
        TaskType.CHOICE,TaskType.LISTEN,TaskType.READING->task.options.shuffled(Random(task.id.hashCode())).forEach{Choice(it,input==it,{change(it)},enabled)}
        TaskType.ORDER->{
            val tokens=task.answers.first().split(" ").filter{it.isNotBlank()};val chosen=input.split(" ").filter{it.isNotBlank()}
            val used=mutableSetOf<Int>();chosen.forEach{word->tokens.indices.firstOrNull{it !in used&&tokens[it]==word}?.let{used.add(it)}}
            Panel(tonal=true){Text(input.ifBlank{"Выберите слова по порядку"},fontSize=20.sp);Row{TextButton(onClick={change(chosen.dropLast(1).joinToString(" "))},enabled=enabled&&chosen.isNotEmpty()){Text("Отменить слово")};TextButton(onClick={change("")},enabled=enabled&&chosen.isNotEmpty()){Text("Сначала")}}}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){tokens.indices.shuffled(Random(task.id.hashCode())).forEach{i->OutlinedButton(onClick={change((chosen+tokens[i]).joinToString(" "))},enabled=enabled&&i !in used,modifier=Modifier.heightIn(min=48.dp)){Text(tokens[i])}}}
        }
        TaskType.MATCH->{
            val pairs=input.lines().mapNotNull{line->line.split('\t').takeIf{it.size==2}?.let{it[0] to it[1]}}.toMap()
            var left by rememberSaveable(task.id){mutableStateOf("")}
            Small("Выберите слово, затем его значение. Пару можно изменить.")
            task.pairs.forEach{(word,_)->Choice(word+ (pairs[word]?.let{" → $it"}?:""),left==word,{left=word},enabled)}
            Label("ЗНАЧЕНИЕ")
            task.pairs.map{it.second}.shuffled(Random(task.id.hashCode())).forEach{meaning->Choice(meaning,left.isNotEmpty()&&pairs[left]==meaning,{
                if(left.isNotEmpty()){val next=pairs.filter{it.key!=left&&it.value!=meaning}+(left to meaning);change(next.entries.joinToString("\n"){"${it.key}\t${it.value}"});left=""}
            },enabled&&left.isNotEmpty())}
        }
        else->OutlinedTextField(value=input,onValueChange={change(it.take(2000))},label={Text("Ваш ответ")},modifier=Modifier.fillMaxWidth(),minLines=2,enabled=enabled,supportingText={Text("Учитываются только допустимые по смыслу и грамматике варианты.")})
    }
}
@Composable fun AudioBar(audio:AudioEngine,activity:MainActivity?=null){
    val state by audio.state.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
        Small(state.status)
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(.75f,1f,1.25f).forEach{rate->FilterChip(selected=state.rate==rate,onClick={audio.rate(rate)},label={Text("${rate}×")})}}
        if(state.playing||state.paused)Row{TextButton(onClick={audio.pauseResume()}){Text(if(state.paused)"Продолжить" else "Пауза")};TextButton(onClick={audio.stop()}){Text("Остановить")}}
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error);if(activity!=null)TextButton(onClick={activity.ttsSettings()}){Text("Настроить русский голос")}}
    }
}
@Composable fun SpeakingPanel(audio:AudioEngine,activity:MainActivity){
    val state by audio.state.collectAsStateWithLifecycle();var disclosure by remember{mutableStateOf(false)}
    Panel{Small("Запись хранится на телефоне: до 20 файлов, каждый до 2 минут. При сворачивании запись остановится. Сравните ритм, ударение и понятность с образцом.")
        Action(if(state.recording)"Остановить запись" else "Записать свою речь"){if(state.recording)audio.stopRecording() else activity.record()}
        if(state.lastRecording!=null)Secondary("Послушать свою запись",!state.recording){audio.playRecording()}
        TextButton(onClick={disclosure=true}){Text("Посмотреть распознанные слова")}}
    if(disclosure)AlertDialog(onDismissRequest={disclosure=false},title={Text("Распознавание речи Android")},text={Text("Откроется выбранный на телефоне сервис распознавания. Он может отправлять голос поставщику, если офлайн-режим недоступен. «Живо» запрашивает русский язык и предпочтение офлайн-обработки, но не контролирует сторонний сервис. Результат — слова, а не оценка произношения.")},confirmButton={TextButton(onClick={disclosure=false;activity.recognize()}){Text("Открыть сервис")}},dismissButton={TextButton(onClick={disclosure=false}){Text("Отмена")}})
}
