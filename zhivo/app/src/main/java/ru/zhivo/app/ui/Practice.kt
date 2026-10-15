package ru.zhivo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.zhivo.app.core.*
import ru.zhivo.app.data.StudyStore
import ru.zhivo.app.media.AudioEngine
import java.text.DateFormat
import java.util.Date

@Composable fun ReviewScreen(store:StudyStore,state:StudyState,audio:AudioEngine,busy:Boolean,errorsOnly:Boolean,run:(suspend()->Unit)->Unit,back:()->Unit){
    val now=remember{System.currentTimeMillis()}
    var queue by rememberSaveable{mutableStateOf((if(errorsOnly)state.errors.filterValues{it>0}.toList().sortedByDescending{it.second}.map{it.first} else Learning.due(state,now)).take(12))}
    var index by rememberSaveable{mutableIntStateOf(0)}
    if(queue.isEmpty()||index>=queue.size){Page(if(index>0)"Повторение завершено" else "Сейчас нет карточек",back=back){
        Panel(tonal=true){Head(if(index>0)"Вы вернули материал в память" else "Всё по расписанию");Text(if(index>0)"Результаты сохранены. Карточки после ошибки вернутся раньше. Самооценка слов не считается объективной проверкой навыка." else "После уроков здесь появляются слова и задания. Сначала попробуйте вспомнить ответ без подсказки.")}
        val next=state.reviews.values.minOfOrNull{it.due};if(next!=null)Small("Ближайшее повторение: ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(next))}")
        if(state.reviews.isNotEmpty())Secondary("Потренировать изученное сейчас"){queue=state.reviews.keys.shuffled().take(12);index=0}
        Action("Готово",onClick=back)};return}
    val id=queue[index];val task=store.course.tasksById[id]
    val vocabulary=store.course.wordsById[id];val custom=state.customWords.firstOrNull{it.id==id}
    fun next(quality:Int,objective:Boolean){run{store.review(id,quality,objective);audio.stop();index++}}
    if(task==null){
        var open by rememberSaveable(id){mutableStateOf(false)}
        Page("Слово ${index+1} из ${queue.size}","Вспомните значение и пример",back){Panel(tonal=true){Head(vocabulary?.word?:custom?.word?:"Слово недоступно");Secondary("Послушать"){audio.speak(vocabulary?.word?:custom?.word.orEmpty())}}
            AudioBar(audio)
            if(!open)Action("Показать значение"){open=true} else{
                Panel{Text(vocabulary?.meaning?:custom?.meaning.orEmpty());Text(vocabulary?.example?:custom?.example.orEmpty());if(state.profile.nativeLanguage=="Английский")vocabulary?.english?.takeIf{it.isNotBlank()}?.let{Small(it)}}
                Small("Оцените себя честно. Это самооценка, без подтверждения произношения или уровня.")
                Action("Вспомнил легко · позже",!busy){next(5,false)};Secondary("Вспомнил с трудом",!busy){next(3,false)};Secondary("Не вспомнил · повторить раньше",!busy){next(1,false)}
            }}
    }else key(id,index){
        var input by rememberSaveable{mutableStateOf("")};var attempts by rememberSaveable{mutableIntStateOf(0)};var correct by rememberSaveable{mutableStateOf(false)}
        var reveal by rememberSaveable{mutableStateOf(false)};var transcript by rememberSaveable{mutableStateOf(false)}
        Page("Задание ${index+1} из ${queue.size}",if(errorsOnly)"Разбираем частые ошибки" else "Проверка по памяти",back){Label(taskName(task.type));Head(task.prompt)
            if(task.passage.isNotBlank())Panel{Text(task.passage)}
            if(task.audio.isNotBlank()){
                Secondary("Послушать"){audio.speak(task.audio)};AudioBar(audio)
                if(transcript)Panel{Text(task.audio);Small("Текстовая практика без подтверждения аудирования.")}
                else TextButton(onClick={transcript=true}){Text("Открыть текст · без оценки слуха")}
            }
            TaskEditor(task,input,{input=it},!correct&&!reveal&&!busy)
            if(attempts>0||reveal)Panel(tonal=true){Head(if(correct)"Верно" else if(reveal)"Образец и объяснение" else "Нужна ещё попытка");Text(task.explanation);if(reveal)Text(task.answers.firstOrNull().orEmpty())}
            if(correct||reveal)Action("Следующая карточка",!busy){next(if(correct&&attempts==1&&!reveal)4 else 1,!reveal&&!transcript)}
            else{Action("Проверить",!busy&&input.isNotBlank()){attempts++;correct=AnswerChecker.matches(task,input)};TextButton(onClick={reveal=true}){Text("Показать ответ · повторить раньше")}}
        }
    }
}
@Composable fun DialogueScreen(dialogue:Dialogue,state:StudyState,audio:AudioEngine,back:()->Unit,save:(String?,Boolean)->Unit){
    var nodeId by rememberSaveable(dialogue.id){mutableStateOf(state.dialogueNodes[dialogue.id]?:"start")}
    var replyIndex by rememberSaveable(dialogue.id,nodeId){mutableIntStateOf(-1)}
    var ended by rememberSaveable(dialogue.id){mutableStateOf(false)};var success by rememberSaveable(dialogue.id){mutableStateOf(false)}
    val node=dialogue.nodes.firstOrNull{it.id==nodeId}?:dialogue.nodes.first{it.id=="start"}
    Page(dialogue.title,"${dialogue.level} · ${dialogue.goal}",back){
        if(ended){Panel(tonal=true){Head(if(success)"Цель разговора достигнута" else "Диалог завершён");Text(dialogue.tip);Small("Теперь попробуйте произнести подходящую реплику без вариантов на экране.")};Action("Ещё раз"){ended=false;success=false;nodeId="start";replyIndex=-1;save("start",false)};Secondary("Вернуться",onClick=back)}
        else{
            Panel(tonal=true){Label(node.speaker);Text(node.line,style=MaterialTheme.typography.titleLarge);TextButton(onClick={audio.speak(node.line)}){Text("Послушать реплику")}}
            AudioBar(audio);Label("ВАШ ОТВЕТ")
            node.replies.forEachIndexed{i,r->Choice(r.text,replyIndex==i,{replyIndex=i},replyIndex<0)}
            if(replyIndex>=0){val reply=node.replies[replyIndex];Panel{Head("Разбор реплики");Text(reply.feedback)}
                Action("Продолжить"){audio.stop();if(reply.next=="end"){ended=true;success=reply.success;save(null,success)}else{nodeId=reply.next;replyIndex=-1;save(nodeId,false)}}
            }
            Small("Подготовленный учебный диалог. Фразы не отправляются другим людям или внешним сервисам.")
        }
    }
}
@Composable fun RecordingsScreen(audio:AudioEngine,back:()->Unit){
    var revision by remember{mutableIntStateOf(0)};var deleting by remember{mutableStateOf<java.io.File?>(null)}
    val files=remember(revision){audio.recordings()}
    Page("Мои записи","Хранятся только на этом телефоне",back){AudioBar(audio)
        if(files.isEmpty())EmptyState("Пока нет записей","Откройте задание на говорение в любом уроке и запишите свою речь.")
        files.forEach{f->Panel{Head(DateFormat.getDateTimeInstance().format(Date(f.lastModified())));Small("${f.length()/1024} КБ · AAC");Secondary("Послушать"){audio.playRecording(f.absolutePath)};TextButton(onClick={deleting=f}){Text("Удалить запись")}}}
        Small("Сохраняются последние 20 записей. Голосовые файлы не включаются в экспорт учебного прогресса.")}
    deleting?.let{file->AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить эту запись?")},text={Text("Восстановить голосовой файл после удаления не получится.")},confirmButton={TextButton(onClick={audio.deleteRecording(file);deleting=null;revision++}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})}
}
@Composable fun Diagnostic(course:Course,profile:Profile,busy:Boolean,back:()->Unit,finish:(Profile)->Unit){
    val questions=remember{listOf("start","a1","a2","b1").flatMap{s->course.lessons.filter{it.stage==s}.take(4).map{it.tasks.first()}}}
    var index by rememberSaveable{mutableIntStateOf(0)};var responses by rememberSaveable{mutableStateOf(listOf<String>())};var selected by rememberSaveable(index){mutableStateOf("")}
    if(index>=questions.size){
        val results=questions.indices.groupBy{questions[it].id.substringBefore('_')}.mapValues{(_,ids)->ids.count{AnswerChecker.matches(questions[it],responses.getOrElse(it){""})} to ids.size}
        val recommended=if((results["start"]?.first?:0)<3)"start" else Learning.diagnostic(results)
        Page("Ваш ориентир",back=back){Panel(tonal=true){Label("МАРШРУТ: ${if(recommended=="start")"КИРИЛЛИЦА" else recommended.uppercase()}");Head("Начнём с подходящих задач");Text("Это короткая ориентировочная проверка отдельных тем. Она не измеряет весь уровень речи и не является сертификатом CEFR или ТРКИ.")}
            results.forEach{(s,r)->Text("${s.uppercase()}: ${r.first} из ${r.second}")}
            Action("Выбрать этот маршрут",!busy){finish(profile.copy(startStage=recommended,diagnostic="Ориентир ${recommended.uppercase()} · ${java.time.LocalDate.now()}"))}
            Secondary("Сохранить прежний маршрут",!busy){finish(profile)}}
        return
    }
    val task=questions[index]
    Page("Диагностика ${index+1} / ${questions.size}","Без подсказок · можно пропустить",back){LinearProgressIndicator(progress={index.toFloat()/questions.size},modifier=Modifier.fillMaxWidth());Head(task.prompt)
        task.options.shuffled(kotlin.random.Random(task.id.hashCode()+19)).forEach{Choice(it,selected==it,{selected=it})}
        Action("Дальше",selected.isNotBlank()){responses=responses+selected;index++}
        TextButton(onClick=back){Text("Пропустить диагностику")}
    }
}
