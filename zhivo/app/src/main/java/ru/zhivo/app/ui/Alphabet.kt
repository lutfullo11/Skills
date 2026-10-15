package ru.zhivo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.zhivo.app.media.AudioEngine

// Original examples; a letter name and its contextual sound are different concepts.
data class Letter(val glyph:String,val name:String,val word:String,val note:String)
val alphabet=listOf(
    Letter("А","а","ма́ма","Гласная. Под ударением — [а]."),Letter("Б","бэ","брат","Парная по звонкости с П. В конце слова может оглушаться."),
    Letter("В","вэ","вода́","Русская В читается как [в], а не латинская B."),Letter("Г","гэ","го́род","Парная с К; на конце слова часто слышится [к]."),
    Letter("Д","дэ","дом","Парная с Т. Сравните твёрдый Д в «дом» и мягкий в «день»."),Letter("Е","е","ель","В начале слова обозначает сочетание [йэ], после согласной обычно указывает её мягкость."),
    Letter("Ё","ё","ёлка","В начале слова — [йо], после согласной обычно смягчает её. Обычно ударная."),Letter("Ж","жэ","жук","Всегда твёрдый согласный. Не смягчайте Ж перед И."),
    Letter("З","зэ","зонт","Звонкий парный согласный; сравните З и С."),Letter("И","и","мир","Гласная. После парной согласной обычно указывает на мягкость."),
    Letter("Й","и краткое","мой","Краткий согласный звук [й]. «Мой» и «мои» имеют разное число слогов."),Letter("К","ка","кот","Глухой согласный, парный с Г."),
    Letter("Л","эль","лук","Сравните твёрдый Л в «лук» и мягкий в «люк»."),Letter("М","эм","ма́ма","Губы смыкаются, голос звучит через нос."),
    Letter("Н","эн","нос","Русская Н не читается как латинская H."),Letter("О","о","дом","Под ударением — [о]. Без ударения звучание меняется: слушайте «вода́»."),
    Letter("П","пэ","парк","Глухой парный с Б. Не добавляйте после него лишнюю гласную."),Letter("Р","эр","рука́","Кончик языка участвует в образовании русского Р. Тренируйте медленно, без оценки по распознаванию."),
    Letter("С","эс","сок","Русская С читается как [с]. Сравните «сок» и «зонт»."),Letter("Т","тэ","там","Сравните твёрдый Т в «там» и мягкий в «тень»."),
    Letter("У","у","у́тро","Гласная, губы вытянуты вперёд."),Letter("Ф","эф","фото́","Глухой парный с В; верхние зубы приближаются к нижней губе."),
    Letter("Х","ха","хлеб","Глухой согласный. Не заменяйте его гласным или звуком К."),Letter("Ц","цэ","цирк","Всегда твёрдый согласный; в русском это один согласный звук."),
    Letter("Ч","че","чай","Обычно всегда мягкий. Не добавляйте отдельный звук Й."),Letter("Ш","ша","ша́пка","Всегда твёрдый. Сравните с более мягким и долгим Щ."),
    Letter("Щ","ща","щётка","Мягкий долгий согласный; отличается от Ш, а не просто названием буквы."),Letter("Ъ","твёрдый знак","подъе́зд","Отдельного звука нет. Разделяет согласную и следующую Е/Ё/Ю/Я: подъ-езд."),
    Letter("Ы","ы","сыр","Гласная после твёрдых согласных. Сравните «был» и «бил»; не заменяйте Ы на И."),Letter("Ь","мягкий знак","день","Отдельного звука нет. Часто показывает мягкость или разделяет: семья́."),
    Letter("Э","э","э́то","Гласная [э]; в начале слова без начального Й."),Letter("Ю","ю","юг","В начале слова — [йу], после согласной обычно смягчает её."),Letter("Я","я","я́блоко","В начале слова — [йа], после согласной обычно указывает на мягкость.")
)
@OptIn(ExperimentalLayoutApi::class)
@Composable fun AlphabetScreen(audio:AudioEngine,back:()->Unit){
    var selected by rememberSaveable{mutableIntStateOf(0)};var drill by rememberSaveable{mutableStateOf(false)};var question by rememberSaveable{mutableIntStateOf(0)};var answer by rememberSaveable{mutableStateOf("")}
    val questions=listOf(Triple("Какая русская буква читается как [в]?","В",listOf("В","Б","Н")),Triple("Что обозначает Ь в слове «день»?","Мягкость Н",listOf("Мягкость Н","Отдельный звук","Ударение")),Triple("Какая буква всегда обозначает твёрдый согласный?","Ж",listOf("Ж","Ч","Щ")),Triple("В каком слове один слог?","Мой",listOf("Мой","Мои","Моя")),Triple("Какое слово содержит разделительный Ъ?","Подъезд",listOf("Подъезд","День","Сыр")))
    Page("Кириллица и звуки","33 буквы · примеры · короткая практика",back){
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){alphabet.forEachIndexed{i,l->FilterChip(selected=selected==i,onClick={selected=i},label={Text(l.glyph)},modifier=Modifier.heightIn(min=48.dp))}}
        val letter=alphabet[selected]
        Panel(tonal=true){Head("${letter.glyph} ${letter.glyph.lowercase()} · ${letter.name}");Text(letter.note);Head(letter.word);Secondary("Послушать слово"){audio.speak(letter.word)};Small("Название буквы и её звук в слове различаются. Синтезатор читает пример, а не оценивает вашу речь.")}
        AudioBar(audio)
        Panel{Head("Что стоит потренировать");Text("Ударение: за́мок — замо́к. Мягкость: лук — люк. Ы и И: был — бил. Ш и Щ: шёлк — щёлкать. Звонкость: в конце слова «дуб» звучит близко к [дуп]. Эти пометки упрощены для старта; слушайте слова в контексте.")}
        if(!drill)Action("Проверить себя"){drill=true;question=0;answer=""}
        else if(question>=questions.size){Panel{Head("Мини-практика завершена");Small("Это тренировка чтения, без автоматической оценки произношения.")};Secondary("Повторить"){question=0;answer=""}}
        else{val q=questions[question];Head(q.first);q.third.forEach{Choice(it,answer==it,{answer=it})};if(answer.isNotBlank()){
            Text(if(answer==q.second)"Верно. ${alphabet.firstOrNull{it.glyph==q.second}?.note.orEmpty()}" else "Верный вариант: ${q.second}. Вернитесь к карточке и сравните пример.")
            Action("Следующий вопрос"){question++;answer=""}
        }}
    }
}
