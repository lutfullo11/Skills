package ru.zhivo.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import ru.zhivo.app.data.StateCodec
import ru.zhivo.app.media.AudioEngine
import ru.zhivo.app.ui.ZhivoRoot
import ru.zhivo.app.core.StudyState

class MainActivity:ComponentActivity(){
    lateinit var audio:AudioEngine;private set
    private var notice:(String)->Unit={}
    private var preview:(StudyState,String)->Unit={_,_->}
    private var transcript:(String)->Unit={}
    private val microphone=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)audio.startRecording() else notice("Микрофон не разрешён. Можно выполнять остальные задания и сравнивать текст самостоятельно.")}
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(!granted)notice("Уведомления не разрешены. Учебный курс доступен без них.")}
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)lifecycleScope.launch{
        runCatching{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write((application as ZhivoApplication).store.export())}?:error("Не удалось открыть файл")}}
            .onSuccess{notice("Резервная копия сохранена. Голосовые записи в неё не входят.")}.onFailure{notice("Ошибка сохранения: ${it.message}")}
    }}
    private val restore=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)lifecycleScope.launch{
        runCatching{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
            val bytes=contentResolver.openInputStream(uri)?.use{stream-> val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); var count=stream.read(buffer); while(count!=-1 && output.size()<=5_000_000){output.write(buffer,0,count);count=stream.read(buffer)}; output.toByteArray()}?:error("Файл не открыт")
            require(bytes.size<=5_000_000){"Файл больше 5 МБ"};val text=bytes.toString(Charsets.UTF_8)
            StateCodec.decode(text,(application as ZhivoApplication).course) to text
        }}.onSuccess{preview(it.first,it.second)}.onFailure{notice("Копия не восстановлена: ${it.message}. Текущие данные сохранены.")}
    }}
    private val recognition=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==RESULT_OK){val text=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull();if(!text.isNullOrBlank())transcript(text) else notice("Речь не распознана. Это не оценка произношения.")}
        else notice("Распознавание отменено или недоступно. Запись своей речи работает отдельно.")
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();audio=AudioEngine(this)
        val app=application as ZhivoApplication
        Reminders.schedule(this,app.store.state.value.profile)
        setContent{ZhivoRoot(app.store,audio,this,
            registerNotice={notice=it},registerPreview={preview=it},registerTranscript={transcript=it})}
    }
    fun record(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)audio.startRecording() else microphone.launch(Manifest.permission.RECORD_AUDIO)}
    fun requestNotifications(){if(android.os.Build.VERSION.SDK_INT>=33)notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}
    fun exportBackup(){export.launch("Zhivo-backup-${java.time.LocalDate.now()}.json")}
    fun importBackup(){restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
    fun recognize(){audio.stop();runCatching{recognition.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU").putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_PROMPT,"Русская речь · не оценка произношения"))}.onFailure{notice("На телефоне нет подходящего сервиса распознавания речи.")}}
    fun ttsSettings(){runCatching{startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.recoverCatching{startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))}.onFailure{notice("Откройте настройки Android → Язык и ввод → Синтез речи. Названия пунктов в HyperOS могут отличаться.")}}
    override fun onStop(){audio.stop();super.onStop()}
    override fun onDestroy(){audio.close();super.onDestroy()}
}
