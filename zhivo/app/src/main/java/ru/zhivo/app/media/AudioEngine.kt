package ru.zhivo.app.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.Locale

// Audio remains local to a visible activity. No microphone or playback in the background.
data class AudioState(val status:String="Проверяем русский голос…",val busy:Boolean=false,val playing:Boolean=false,val paused:Boolean=false,val recording:Boolean=false,val error:String?=null,val rate:Float=1f,val russian:Boolean=false,val lastRecording:String?=null)
class AudioEngine(private val context:Context){
    private val mutable=MutableStateFlow(AudioState());val state=mutable.asStateFlow()
    private val handler=Handler(Looper.getMainLooper())
    private val manager=context.getSystemService(AudioManager::class.java)
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener{change->if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)stop()}.build()
    private var tts:TextToSpeech?=null;private var player:MediaPlayer?=null;private var recorder:MediaRecorder?=null
    private var generation=0;private var pending:File?=null;private var recordingFile:File?=null;private var allowNetwork=false
    private val audioDir=File(context.cacheDir,"speech").apply{mkdirs()}
    private val recordDir=File(context.filesDir,"recordings").apply{mkdirs()}
    init{
        tts=TextToSpeech(context){result->handler.post{
            if(result==TextToSpeech.SUCCESS)configureVoice() else update{it.copy(status="Русский голос недоступен",error="Служба синтеза речи не запустилась. Проверьте настройки синтеза речи Android.")}
        }}
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(utteranceId:String?){}
            override fun onDone(utteranceId:String?){handler.post{if(utteranceId==generation.toString())pending?.let{file->pending=null;playFile(file)}}}
            @Deprecated("Platform callback") override fun onError(utteranceId:String?){handler.post{if(utteranceId==generation.toString()){pending?.delete();pending=null;update{it.copy(busy=false,error="Не удалось создать аудио. Проверьте русский голос и доступность его данных.",status="Ошибка озвучивания")}}}}
        })
        recordDir.listFiles()?.filter{it.extension=="m4a"}?.maxByOrNull{it.lastModified()}?.let{f->update{it.copy(lastRecording=f.absolutePath)}}
    }
    private fun update(f:(AudioState)->AudioState){mutable.value=f(mutable.value)}
    fun configureNetwork(value:Boolean){if(allowNetwork!=value){allowNetwork=value;stop();configureVoice()}}
    private fun configureVoice(){
        val engine=tts?:return
        runCatching{
            val language=engine.setLanguage(Locale.forLanguageTag("ru-RU"))
            if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED)error("Русский голос не установлен. Установите русские голосовые данные в настройках синтеза речи.")
            val voices=engine.voices.orEmpty().filter{it.locale.language=="ru"&&(allowNetwork||!it.isNetworkConnectionRequired)}
                .sortedWith(compareBy<android.speech.tts.Voice>{it.isNetworkConnectionRequired}.thenByDescending{it.quality})
            if(voices.isEmpty())error(if(allowNetwork)"Русский голос не найден." else "Не найден офлайн-голос русского языка. Установите его или отдельно разрешите сетевой голос в профиле.")
            check(engine.setVoice(voices.first())==TextToSpeech.SUCCESS){"Не удалось выбрать русский голос"}
            update{it.copy(russian=true,error=null,status=if(voices.first().isNetworkConnectionRequired)"Синтез речи · сетевой голос" else "Синтез речи · офлайн-голос")}
        }.onFailure{e->update{it.copy(russian=false,busy=false,error=e.message,status="Аудио недоступно")}}
    }
    fun speak(text:String){
        if(!state.value.russian){configureVoice();return}
        if(recorder!=null)stopRecording()
        stopPlayback();generation++;val token=generation
        val clean=text.replace("\u0301","").take(3900)
        val key=MessageDigest.getInstance("SHA-256").digest((tts?.voice?.name+"|"+state.value.rate+"|"+clean).toByteArray()).joinToString(""){"%02x".format(it)}
        val file=File(audioDir,"$key.wav")
        if(file.exists()&&file.length()>44){playFile(file);return}
        trimCache();pending=file
        update{it.copy(busy=true,error=null,status="Готовим озвучивание…")}
        tts?.setSpeechRate(state.value.rate)
        if(tts?.synthesizeToFile(clean,null,file,token.toString())!=TextToSpeech.SUCCESS){pending=null;file.delete();update{it.copy(busy=false,error="Служба синтеза отклонила запрос.",status="Ошибка аудио")}}
        handler.postDelayed({if(generation==token&&pending!=null){generation++;tts?.stop();pending?.delete();pending=null;update{it.copy(busy=false,error="Голос не ответил за 30 секунд. Проверьте данные русского голоса; текстовые упражнения доступны.",status="Время ожидания аудио истекло")}}},30_000)
    }
    private fun playFile(file:File){
        runCatching{
            player?.release()
            player=MediaPlayer().apply{
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.absolutePath);prepare()
                setOnCompletionListener{update{it.copy(playing=false,paused=false,status="Прослушано")}}
                setOnErrorListener{_,_,_->update{it.copy(playing=false,paused=false,busy=false,error="Не удалось воспроизвести файл.")};true}
                check(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Другое приложение использует звук. Попробуйте после его остановки."}
                start()
            }
            update{it.copy(playing=true,paused=false,busy=false,error=null,status="Воспроизведение")}
        }.onFailure{e->file.takeIf{it.parentFile==audioDir}?.delete();update{it.copy(playing=false,busy=false,error="Ошибка воспроизведения: ${e.message}")}}
    }
    fun pauseResume(){runCatching{player?.let{p->if(p.isPlaying){p.pause();update{it.copy(playing=false,paused=true,status="Пауза")}}else if(state.value.paused){p.start();update{it.copy(playing=true,paused=false,status="Воспроизведение")}}}}.onFailure{e->update{it.copy(error=e.message)}}}
    fun rate(value:Float){stopPlayback();generation++;tts?.stop();pending?.delete();pending=null;update{it.copy(rate=value,busy=false)}}
    private fun stopPlayback(){runCatching{player?.stop()};player?.release();player=null;manager.abandonAudioFocusRequest(focus);update{it.copy(playing=false,paused=false)}}
    fun stop(){generation++;tts?.stop();pending?.delete();pending=null;stopPlayback();update{it.copy(busy=false)};if(recorder!=null)stopRecording()}
    @Suppress("DEPRECATION")
    fun startRecording(){
        stop();val file=File(recordDir,"speech-${System.currentTimeMillis()}.m4a")
        runCatching{
            val r=if(Build.VERSION.SDK_INT>=31)MediaRecorder(context) else MediaRecorder()
            recorder=r;recordingFile=file
            r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioSamplingRate(44100);r.setAudioEncodingBitRate(96000)
            r.setOutputFile(file.absolutePath);r.setMaxDuration(120_000)
            r.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)stopRecording()}
            r.setOnErrorListener{_,_,_->stopRecording();update{it.copy(error="Запись прервана системой. Проверьте доступ к микрофону.")}}
            r.prepare();r.start();update{it.copy(recording=true,error=null,status="Идёт запись · максимум 2 минуты")}
        }.onFailure{e->recorder?.release();recorder=null;file.delete();update{it.copy(recording=false,error="Не удалось включить микрофон: ${e.message}")}}
    }
    fun stopRecording(){
        val r=recorder?:return;recorder=null
        val good=runCatching{r.stop()}.isSuccess;r.release()
        val f=recordingFile;recordingFile=null
        if(!good)f?.delete()
        if(good&&f!=null){recordDir.listFiles()?.sortedByDescending{it.lastModified()}?.drop(20)?.forEach{it.delete()}}
        update{it.copy(recording=false,lastRecording=if(good)f?.absolutePath else it.lastRecording,status=if(good)"Запись сохранена на телефоне" else "Запись слишком короткая",error=if(good)null else "Запишите фразу немного дольше.")}
    }
    fun playRecording(path:String?=state.value.lastRecording){val f=path?.let{File(it)};if(f!=null&&f.exists()){stop();playFile(f)}else update{it.copy(error="Запись не найдена")}}
    fun recordings():List<File> = recordDir.listFiles()?.sortedByDescending{it.lastModified()}.orEmpty()
    fun deleteRecording(file:File){if(file.parentFile==recordDir){stop();file.delete();update{it.copy(lastRecording=recordings().firstOrNull()?.absolutePath)}}}
    fun bytes()=(audioDir.listFiles().orEmpty().toList()+recordDir.listFiles().orEmpty().toList()).sumOf{it.length()}
    private fun trimCache(){var size=audioDir.listFiles().orEmpty().sumOf{it.length()};audioDir.listFiles()?.sortedBy{it.lastModified()}?.forEach{if(size>64L*1024*1024){size-=it.length();it.delete()}}}
    fun clearCache(){stop();audioDir.listFiles()?.forEach{it.delete()};update{it.copy(status="Кэш аудио очищен")}}
    fun deleteRecordings(){stop();recordDir.listFiles()?.forEach{it.delete()};update{it.copy(lastRecording=null)}}
    fun close(){stop();tts?.shutdown();tts=null}
}
