package ru.zhivo.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zhivo.app.MainActivity
import ru.zhivo.app.Reminders
import ru.zhivo.app.core.*
import ru.zhivo.app.data.StudyStore
import ru.zhivo.app.media.AudioEngine
import java.time.LocalDate
import java.time.ZoneId

@Composable fun ZhivoRoot(store:StudyStore,audio:AudioEngine,activity:MainActivity,
    registerNotice:((String)->Unit)->Unit,registerPreview:((StudyState,String)->Unit)->Unit,registerTranscript:((String)->Unit)->Unit){
    val state by store.state.collectAsStateWithLifecycle();val scope=rememberCoroutineScope();val snackbar=remember{SnackbarHostState()}
    var tab by rememberSaveable{mutableIntStateOf(0)};var screen by rememberSaveable{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var importPreview by remember{mutableStateOf<Pair<StudyState,String>?>(null)};var recognized by remember{mutableStateOf<String?>(null)}
    fun notify(text:String){scope.launch{snackbar.showSnackbar(text,duration=SnackbarDuration.Long)}}
    fun run(block:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{block()}catch(e:Exception){notify(e.message?:"Не удалось выполнить действие")}finally{busy=false}}}
    SideEffect{registerNotice(::notify);registerPreview{data,text->importPreview=data to text};registerTranscript{recognized=it}}
    LaunchedEffect(state.profile.networkVoice){audio.configureNetwork(state.profile.networkVoice)}
    val owner=LocalLifecycleOwner.current;var foreground by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,_->foreground=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    LaunchedEffect(screen,foreground){
        if(foreground&&screen.isNotEmpty()&&(screen.startsWith("lesson:")||screen.startsWith("review")||screen.startsWith("dialogue:")||screen=="diagnostic")){
            var last=SystemClock.elapsedRealtime()
            try{while(true){delay(10_000);val now=SystemClock.elapsedRealtime();try{store.time(now-last,screen.substringBefore(':'))}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){notify("Не удалось сохранить время занятия: ${e.message}");break};last=now}}
            finally{val now=SystemClock.elapsedRealtime();withContext(NonCancellable){runCatching{store.time((now-last).coerceAtLeast(0),screen.substringBefore(':'))}}}
        }
    }
    fun openLesson(id:String){run{audio.stop();store.start(id);screen="lesson:$id"}}
    fun leave(){audio.stop();screen=""}
    BackHandler(enabled=screen.isNotEmpty()){leave()}
    ZhivoTheme(state.profile.theme){
        val darkBars=MaterialTheme.colorScheme.background.luminance()<0.5f
        SideEffect{val style=if(darkBars)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT);activity.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)}
        Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={if(state.profile.onboarded&&screen.isEmpty())NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
            val labels=listOf("Сегодня","Курс","Практика","Слова","Профиль");val icons=listOf(Icons.Default.Home,Icons.Default.Star,Icons.Default.PlayArrow,Icons.Default.Search,Icons.Default.Settings)
            labels.forEachIndexed{i,label->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],contentDescription=null)},label={Text(label,fontSize=10.sp)},alwaysShowLabel=true)}
        }}){padding->Box(Modifier.padding(padding).fillMaxSize().imePadding()){
            when{
                !state.profile.onboarded->Onboarding(state.profile,busy){p,diagnostic->run{store.profile(p.copy(onboarded=true));tab=0;if(diagnostic)screen="diagnostic"}}
                screen.startsWith("lesson:")->store.course.lessonsById[screen.substringAfter(':')]?.let{lesson->LessonScreen(lesson,state,store,audio,activity,busy,::run,::leave)}?:EmptyState("Урок не найден","Вернитесь в курс и выберите другой урок.")
                screen=="diagnostic"->Diagnostic(store.course,state.profile,busy,::leave){p->run{store.profile(p);leave()}}
                screen.startsWith("dialogue:")->store.course.dialogues.firstOrNull{it.id==screen.substringAfter(':')}?.let{d->DialogueScreen(d,state,audio,::leave){node,success->run{store.dialogue(d.id,node,success)}}}
                screen.startsWith("review")->ReviewScreen(store,state,audio,busy,screen=="review:errors",::run,::leave)
                screen=="alphabet"->AlphabetScreen(audio,::leave)
                screen=="recordings"->RecordingsScreen(audio,::leave)
                else->when(tab){
                    0->HomeScreen(store.course,state,::openLesson,{screen="review"},{tab=1})
                    1->CourseScreen(store.course,state,::openLesson){screen="alphabet"}
                    2->PracticeScreen(store.course,state,{screen="review"},{screen="review:errors"},{screen="dialogue:$it"},{screen="diagnostic"},{screen="recordings"},{screen="alphabet"})
                    3->VocabularyScreen(store,state,audio,busy,::run)
                    else->ProfileScreen(store,state,audio,activity,busy,::run,{screen="diagnostic"},::notify)
                }
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }}
        importPreview?.let{(preview,text)->AlertDialog(onDismissRequest={importPreview=null},title={Text("Заменить текущий прогресс?")},text={Text("В копии: ${preview.progress.count{it.value.completedAt>0}} завершённых уроков, ${preview.customWords.size} личных слов. Будут заменены настройки, ответы и расписание повторений. Сначала сохраните текущую копию, если она нужна. Голосовые записи не изменятся.")},confirmButton={TextButton(enabled=!busy,onClick={run{store.import(text);Reminders.schedule(activity,store.state.value.profile);importPreview=null;leave();notify("Резервная копия восстановлена")}}){Text("Восстановить")}},dismissButton={TextButton(onClick={importPreview=null}){Text("Отмена")}})}
        recognized?.let{text->AlertDialog(onDismissRequest={recognized=null},title={Text("Распознанный текст")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(text);Small("Это результат распознавания слов, а не оценка произношения. Сервис может ошибаться. Сравните со своей записью и учебным образцом.")}},confirmButton={TextButton(onClick={recognized=null}){Text("Понятно")}})}
        store.loadError?.let{error->AlertDialog(onDismissRequest={},title={Text("Данные требуют восстановления")},text={Text(error)},confirmButton={TextButton(onClick={activity.importBackup()}){Text("Выбрать копию")}},dismissButton={TextButton(onClick={run{store.reset()}}){Text("Начать заново")}})}
    }
}
@Composable fun HomeScreen(course:Course,state:StudyState,lesson:(String)->Unit,review:()->Unit,route:()->Unit){
    val next=Learning.recommend(course,state);val due=Learning.due(state,System.currentTimeMillis()).size
    val completed=state.progress.count{it.value.completedAt>0};val verified=state.progress.count{it.value.verifiedAt>0}
    val today=LocalDate.now();val zone=ZoneId.systemDefault();val minutes=Learning.minutes(state.events,today,zone)
    Page("Живо","Русский, который становится вашим"){
        Panel(tonal=true){Label(if(state.session?.finished==false)"ПРОДОЛЖИМ С ТОГО ЖЕ МЕСТА" else "ВАШ СЛЕДУЮЩИЙ ШАГ")
            Text(next?.title?:"Вы прошли все уроки",fontFamily=FontFamily.Serif,fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Medium)
            Text(next?.goal?:"Теперь укрепляйте навыки в повторении и диалогах.",style=MaterialTheme.typography.bodyLarge)
            Small(if(next!=null)"${course.stages.first{it.id==next.stage}.subtitle} · 9 заданий · около ${next.minutes} минут" else "В курсе ${course.lessons.size} урока")
            Action(if(state.session?.finished==false)"Продолжить занятие" else if(next!=null)"Начать занятие" else "К повторению"){if(next!=null)lesson(next.id) else review()}
        }
        Panel{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Head("Немного каждый день");Label("${minutes.toInt()} / ${state.profile.dailyMinutes} мин")}
            LinearProgressIndicator(progress={(minutes/state.profile.dailyMinutes).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(6 downTo 0).forEach{offset->val day=today.minusDays(offset.toLong());val m=Learning.minutes(state.events,day,zone);Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){Text(if(m>=state.profile.dailyMinutes)"●" else if(m>0)"◐" else "○",fontSize=23.sp,color=MaterialTheme.colorScheme.primary);Small(listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")[day.dayOfWeek.value-1])}}}
            Small("Время учитывается в открытом занятии. Освоение оценивается отдельно по ответам и повторению.")
        }
        Line(if(due>0)"Пора повторить: $due" else "Повторение по расписанию",if(due>0)"Верните в память слова и формы" else "Новые карточки появятся после занятия",if(due>0)"Начать" else "Открыть",review)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){Stat("$completed","Уроков выполнено",Modifier.weight(1f));Stat("$verified","Подтверждены повторно",Modifier.weight(1f))}
        Panel{Label("СЕГОДНЯ ВАЖНО");Text(when(state.profile.nativeLanguage){"Таджикский"->"В русском существительные имеют род, а форма слова показывает его роль. Сравнивайте целые фразы, а не только отдельные слова.";"Узбекский"->"Не переносите окончания из родного языка буквально. Учите русские предлоги вместе с падежной формой: к другу, с другом, о друге.";"Английский"->"В русском окончания помогают понять роль слова. Сначала закрепляйте нейтральный порядок слов, затем изучайте смысловые изменения.";else->"Проговорите пример, закройте его и восстановите по памяти. Затем проверьте себя и повторите позже."});TextButton(onClick=route){Text("Посмотреть учебный маршрут →")}}
    }
}
@Composable fun CourseScreen(course:Course,state:StudyState,lesson:(String)->Unit,alphabet:()->Unit){
    var selected by rememberSaveable{mutableStateOf(state.profile.startStage)}
    Page("Ваш маршрут","От первых букв к точной и связной речи"){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){course.stages.forEach{s->FilterChip(selected=selected==s.id,onClick={selected=s.id},label={Text(if(s.id=="start")"АБВ" else s.id.uppercase())},modifier=Modifier.weight(1f))}}
        if(selected=="start")Line("Все 33 буквы и трудные звуки","Слушайте, сравнивайте и проверяйте себя","Открыть",alphabet)
        val stage=course.stages.first{it.id==selected};Panel(tonal=true){Label(stage.subtitle);Head(stage.title);Text(stage.description);Small("Вы можете открыть любой урок. Буквенные ориентиры не являются сертификатом CEFR или ТРКИ.")}
        course.lessons.filter{it.stage==selected}.forEachIndexed{i,l->val p=state.progress[l.id];Line("${i+1}. ${l.title}",l.goal,when{p?.verifiedAt?.let{it>0}==true->"Закреплён";p?.completedAt?.let{it>0}==true->"Пройден";state.session?.lessonId==l.id&&state.session.finished.not()->"Продолжить";p?.viewedAt?.let{it>0}==true->"Начат";else->"→"}){lesson(l.id)}}
    }
}
@Composable fun PracticeScreen(course:Course,state:StudyState,review:()->Unit,errors:()->Unit,dialogue:(String)->Unit,diagnostic:()->Unit,recordings:()->Unit,alphabet:()->Unit){
    Page("Практика","Вспоминайте, слушайте и пробуйте говорить"){
        Line("Кириллица и произношение","33 буквы, мягкость, ударение, Ы/И и Ш/Щ","Открыть",alphabet)
        Panel(tonal=true){Label("ПОВТОРЕНИЕ");Head("Сначала вспомнить — потом проверить");Text("Расписание учитывает ответы. После ошибки карточка вернётся раньше; устойчивые ответы увеличивают интервал.");Action("Повторить по расписанию (${Learning.due(state,System.currentTimeMillis()).size})",onClick=review)}
        Line("Мои трудности","${state.errors.count{it.value>0}} заданий с ошибками","Разобрать",errors)
        Head("Разговорные ситуации");Small("Локальные диалоги с выбором и разбором реплик. Это подготовленные сценарии, без свободного ИИ-общения.")
        course.dialogues.forEach{d->Line(d.title,"${d.level} · ${d.goal}",if(d.id in state.dialogueWins)"✓" else "→"){dialogue(d.id)}}
        Line("Мои записи","Прослушать и удалить собственную речь","Открыть",recordings)
        Line("Настроить маршрут","Короткая ориентировочная диагностика","Начать",diagnostic)
    }
}
