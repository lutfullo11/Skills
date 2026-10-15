package ru.zhivo.app.ui

import androidx.activity.compose.BackHandler
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import ru.zhivo.app.MainActivity
import ru.zhivo.app.Reminders
import ru.zhivo.app.core.*
import ru.zhivo.app.data.StudyStore
import ru.zhivo.app.media.AudioEngine

private val languages=listOf("Таджикский","Узбекский","Английский","Другой")
private val goals=listOf("Жизнь и общение","Работа","Учёба","Путешествия")
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Chips(items:List<String>,selected:String,select:(String)->Unit){FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items.forEach{FilterChip(selected=it==selected,onClick={select(it)},label={Text(it)})}}}
@Composable fun Onboarding(profile:Profile,busy:Boolean,finish:(Profile,Boolean)->Unit){
    var page by rememberSaveable{mutableIntStateOf(0)};var native by rememberSaveable{mutableStateOf("")};var goal by rememberSaveable{mutableStateOf(goals.first())};var minutes by rememberSaveable{mutableIntStateOf(10)};var stage by rememberSaveable{mutableStateOf("start")};var name by rememberSaveable{mutableStateOf("")}
    BackHandler(enabled=page>0){page--}
    Page(if(page==0)"Живо" else if(page==1)"Ваш ритм" else "С чего начнём",if(page==0)"Русский для вашей жизни" else "${page+1} из 3",if(page>0){{page--}} else null){
        when(page){
            0->{Panel(tonal=true){Label("ПОНЯТЬ · ВСПОМНИТЬ · СКАЗАТЬ");Head("Не просто знать слова. Уметь ими пользоваться.");Text("Короткие объяснения, 72 урока, практика и повторение по памяти. Учитесь в своём темпе, без регистрации.")};Panel{Head("Всё начинается с понятной цели");Text("От кириллицы и первых просьб — к связному рассказу и обсуждению. Темы B1–B2 помогают практиковаться; короткий курс не подтверждает полный уровень.")};Action("Настроить обучение"){page=1}}
            1->{OutlinedTextField(name,{name=it.take(60)},label={Text("Как вас называть? Необязательно")},modifier=Modifier.fillMaxWidth());Head("Ваш родной язык");Chips(languages,native,{native=it});Small("Объяснения на русском. Для английского есть переводы слов; выбор языка также влияет на учебные подсказки.");Head("Зачем вам русский?");Chips(goals,goal,{goal=it});Head("Сколько минут в день?");Chips(listOf("5","10","15","20"),minutes.toString(),{minutes=it.toInt()});Action("Дальше",native.isNotEmpty()){page=2}}
            else->{Head("Текущий ориентир");listOf("start" to "Начинаю с кириллицы","a1" to "Знаю буквы и простые фразы","a2" to "Общаюсь в бытовых ситуациях","b1" to "Могу связно рассказать о событиях","b2" to "Хочу точнее обсуждать сложные темы").forEach{(id,text)->Choice(text,stage==id,{stage=id})};Small("Это старт маршрута, а не подтверждённый уровень. Все уроки доступны независимо от выбора.");Action("Пройти короткую диагностику",!busy){finish(profile.copy(name=name,nativeLanguage=native,goal=goal,dailyMinutes=minutes,startStage=stage),true)};Secondary("Начать без диагностики",!busy){finish(profile.copy(name=name,nativeLanguage=native,goal=goal,dailyMinutes=minutes,startStage=stage),false)}}
        }
    }
}
@Composable fun VocabularyScreen(store:StudyStore,state:StudyState,audio:AudioEngine,busy:Boolean,run:(suspend()->Unit)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("Изученные")};var adding by rememberSaveable{mutableStateOf(false)};var deleting by remember{mutableStateOf<String?>(null)}
    val words=store.course.words+state.customWords.map{Vocabulary(it.id,it.word,it.meaning,it.example)}
    var limit by remember(query,filter){mutableIntStateOf(100)}
    val visible=words.filter{v->(filter=="Все"||(filter=="Избранные"&&v.id in state.favorites)||(filter=="Изученные"&&v.id in state.reviews))&&(query.isBlank()||listOf(v.word.replace("\u0301",""),v.meaning,v.english,v.example.replace("\u0301","")).any{it.contains(query,true)})}
    Page("Ваши слова","Не отдельно от жизни, а в понятном контексте"){
        OutlinedTextField(query,{query=it},label={Text("Найти слово или значение")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Chips(listOf("Изученные","Избранные","Все"),filter,{filter=it});Secondary("Добавить своё слово"){adding=true};AudioBar(audio)
        if(visible.isEmpty())EmptyState("Пока пусто","Пройдите урок, добавьте слово или измените фильтр.")
        visible.take(limit).forEach{v->Panel{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Head(v.word);TextButton(onClick={run{store.favorite(v.id)}},enabled=!busy){Text(if(v.id in state.favorites)"★ Убра́ть" else "☆ Сохранить")}}
            Text(v.meaning);if(v.example.isNotBlank())Text(v.example);if(state.profile.nativeLanguage=="Английский"&&v.english.isNotBlank())Small(v.english)
            Row{TextButton(onClick={audio.speak(v.example.ifBlank{v.word})}){Text("Послушать")};if(v.id.startsWith("custom_"))TextButton(onClick={deleting=v.id}){Text("Удалить")}}
            state.reviews[v.id]?.let{card->Small(if(card.due<=System.currentTimeMillis())"Пора повторить" else "Следующий интервал: ${card.intervalDays} дн.")}}
        }
        if(visible.size>limit)Secondary("Показать ещё (${visible.size-limit})"){limit+=100}
    }
    if(adding){var word by rememberSaveable{mutableStateOf("")};var meaning by rememberSaveable{mutableStateOf("")};var example by rememberSaveable{mutableStateOf("")}
        AlertDialog(onDismissRequest={adding=false},title={Text("Личное слово")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(word,{word=it.take(200)},label={Text("Слово или выражение")});OutlinedTextField(meaning,{meaning=it.take(500)},label={Text("Значение или перевод")});OutlinedTextField(example,{example=it.take(1000)},label={Text("Пример, необязательно")});Small("Слово сразу попадёт в повторение. Проверьте написание по словарю.")}},confirmButton={TextButton(enabled=!busy&&word.isNotBlank()&&meaning.isNotBlank(),onClick={run{store.custom(word,meaning,example);adding=false}}){Text("Добавить")}},dismissButton={TextButton(onClick={adding=false}){Text("Отмена")}})
    }
    deleting?.let{id->AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить личное слово?")},text={Text("Будут удалены это слово, его избранное и расписание повторений.")},confirmButton={TextButton(onClick={run{store.deleteWord(id);deleting=null}}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})}
}
@Composable fun ProfileScreen(store:StudyStore,state:StudyState,audio:AudioEngine,activity:MainActivity,busy:Boolean,run:(suspend()->Unit)->Unit,diagnostic:()->Unit,notify:(String)->Unit){
    val p=state.profile;var native by rememberSaveable(p.nativeLanguage){mutableStateOf(p.nativeLanguage)};var goal by rememberSaveable(p.goal){mutableStateOf(p.goal)};var minutes by rememberSaveable(p.dailyMinutes){mutableStateOf(p.dailyMinutes)}
    var reset by rememberSaveable{mutableStateOf(false)};var permissionInfo by rememberSaveable{mutableStateOf(false)};val context=LocalContext.current
    Page(p.name.ifBlank{"Ваш профиль"},"Свой темп. Видимый прогресс."){
        Panel(tonal=true){Head("Что уже получается");listOf("Чтение" to setOf(TaskType.READING),"Аудирование" to setOf(TaskType.LISTEN,TaskType.DICTATION),"Формы и грамматика" to setOf(TaskType.FILL,TaskType.CORRECT),"Фраза и смысл" to setOf(TaskType.ORDER,TaskType.CHOICE)).forEach{(label,types)->
            val tasks=store.course.tasksById.values.filter{it.type in types};val practiced=tasks.count{it.id in state.reviews};val verified=tasks.count{state.reviews[it.id]?.verifiedAt?.let{t->t>0}==true}
            Text(label,style=MaterialTheme.typography.titleSmall);LinearProgressIndicator(progress={verified.toFloat()/tasks.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth());Small("Практиковали: $practiced · подтверждены позже: $verified из ${tasks.size}")
        };Small("Говорение оценивается самостоятельно. Урок отмечается «закреплён» после трёх самостоятельных проверок разных заданий не раньше чем через сутки. Это внутренний ориентир, не сертификат.")}
        Head("Учебная цель");Chips(languages,native,{native=it});Chips(goals,goal,{goal=it});Chips(listOf("5","10","15","20"),minutes.toString(),{minutes=it.toInt()})
        Secondary("Сохранить цель",!busy&&(native!=p.nativeLanguage||goal!=p.goal||minutes!=p.dailyMinutes)){run{store.profile(p.copy(nativeLanguage=native,goal=goal,dailyMinutes=minutes));notify("Учебная цель сохранена")}}
        Line("Диагностика и маршрут",p.diagnostic.ifBlank{"Ориентировочная проверка отдельных тем"},"Открыть",diagnostic)
        Head("Оформление");Chips(listOf("По системе","Светлая","Тёмная"),when(p.theme){"dark"->"Тёмная";"light"->"Светлая";else->"По системе"}){value->run{store.profile(p.copy(theme=when(value){"Тёмная"->"dark";"Светлая"->"light";else->"system"}))}}
        Head("Напоминания")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Напомнить о занятии",Modifier.weight(1f));Switch(checked=p.reminder,enabled=!busy,onCheckedChange={value->if(value)activity.requestNotifications();run{val next=p.copy(reminder=value);store.profile(next);Reminders.schedule(activity,next)}})}
        Secondary("Время: %02d:%02d".format(p.reminderHour,p.reminderMinute),p.reminder&&!busy){TimePickerDialog(activity,{_,h,m->run{val next=p.copy(reminderHour=h,reminderMinute=m);store.profile(next);Reminders.schedule(activity,next)}},p.reminderHour,p.reminderMinute,true).show()}
        Small("Время местное. Android и энергосбережение HyperOS могут задерживать уведомление. После перезагрузки откройте «Живо», чтобы восстановить расписание.")
        if(p.reminder&&!context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()){
            Text("Android блокирует уведомления «Живо».",color=MaterialTheme.colorScheme.error);TextButton(onClick={runCatching{activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName))}.onFailure{notify("Откройте системные настройки уведомлений приложения.")}}){Text("Открыть настройки уведомлений")}
        }
        Head("Голос и данные")
        Secondary("Настроить русский голос",onClick=activity::ttsSettings)
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Разрешить сетевой голос");Small("Текст может передаваться службе синтеза речи. По умолчанию используется офлайн-голос.")};Switch(checked=p.networkVoice,enabled=!busy,onCheckedChange={value->run{store.profile(p.copy(networkVoice=value))}})}
        Secondary("Очистить кэш озвучивания (${audio.bytes()/1024/1024} МБ с записями)"){audio.clearCache();notify("Кэш синтезированной речи очищен. Ваши записи сохранены.")}
        Small("Кэш озвучивания ограничен примерно 64 МБ; записи — последние 20. Курс и тексты входят в APK, загружать уроки не требуется.")
        Head("Резервная копия")
        Secondary("Экспортировать прогресс и словарь",onClick=activity::exportBackup);Secondary("Восстановить из файла",onClick=activity::importBackup)
        Small("JSON содержит профиль, ответы, ошибки, словарь и повторения. Храните файл в доверенном месте. Голосовые записи не экспортируются. Облачной синхронизации и аккаунта здесь нет.")
        TextButton(onClick={permissionInfo=true}){Text("Разрешения и ограничения")}
        Panel{Head("О курсе");Text("72 авторских урока · 648 заданий · 216 словарных карточек · 14 диалогов. 8 стартовых, 18 A1, 18 A2, 16 по темам B1 и 12 по темам B2.");Small("Это учебный маршрут с ограниченным объёмом. Профессионального рецензирования преподавателем РКИ не было. Ударение в примерах — подсказка; качество синтеза зависит от установленного голоса. Произношение и свободное письмо требуют дополнительной практики с человеком.")}
        TextButton(enabled=!busy,onClick={reset=true}){Text("Удалить все учебные данные",color=MaterialTheme.colorScheme.error)}
        Small("Живо 1.0 · локальное обучение · Android 8–16")
    }
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Удалить все данные «Живо»?")},text={Text("Будут удалены профиль, прогресс, ответы, словарь, ошибки, расписание и голосовые записи на этом телефоне. Экспортированные файлы останутся в выбранном вами месте. Сначала сохраните резервную копию, если она нужна.")},confirmButton={TextButton(onClick={run{audio.deleteRecordings();audio.clearCache();store.reset();Reminders.schedule(activity,Profile());reset=false}}){Text("Удалить данные")}},dismissButton={TextButton(onClick={reset=false}){Text("Отмена")}})
    if(permissionInfo)AlertDialog(onDismissRequest={permissionInfo=false},title={Text("Вы управляете доступом")},text={Text("Микрофон запрашивается только при записи речи. Уведомления — при включении напоминаний. Файлы выбираются системным диалогом; широкого доступа к хранилищу нет. Локальные уроки работают без сети и микрофона. Сетевой голос и внешнее распознавание могут передавать текст или голос своим поставщикам. «Живо» не оценивает произношение по совпадению распознанного текста.")},confirmButton={TextButton(onClick={permissionInfo=false}){Text("Понятно")}})
}
