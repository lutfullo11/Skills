package ru.zhivo.app

import android.app.Application
import ru.zhivo.app.data.CourseCodec
import ru.zhivo.app.data.StudyStore

class ZhivoApplication:Application(){
    val course by lazy{assets.open("course.json").bufferedReader().use{CourseCodec.decode(it.readText())}}
    val store by lazy{StudyStore(this,course)}
}
