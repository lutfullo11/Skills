package ru.zhivo.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class InterfaceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<ZhivoApplication>()
    @Before fun clean() {runBlocking{app.store.reset()};rule.waitForIdle()}
    private fun click(text:String){val node=rule.onNodeWithText(text);runCatching{node.performScrollTo()};node.performClick();rule.waitForIdle()}
    private fun screenshot(name:String){
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?:error("Screenshot unavailable")
        val filename="$name-${(app.resources.configuration.fontScale*100).toInt()}.png"
        try{
            if(Build.VERSION.SDK_INT>=29){
                val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,filename);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Zhivo-tests")}
                val uri=app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("Cannot save screenshot")
                app.contentResolver.openOutputStream(uri)!!.use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            }else File(app.getExternalFilesDir(null),filename).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }finally{bitmap.recycle()}
    }
    @Test fun onboardingLessonWrongRetryAndRecreation(){
        click("Настроить обучение");click("Таджикский");click("Дальше");click("Начать без диагностики")
        rule.waitUntil(5000){app.store.state.value.profile.onboarded};rule.waitForIdle();screenshot("01-home")
        click("Начать занятие");click("Попробовать · 9 заданий");click("кот");click("Проверить")
        rule.onNodeWithText("Попробуйте ещё раз").assertExists();click("мама");click("Проверить");rule.onNodeWithText("Получилось").assertExists()
        click("Дальше");rule.onNodeWithText("Ваш ответ").performTextInput("а");click("Проверить");rule.onNodeWithText("Верно").assertExists();screenshot("02-lesson")
        rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithText("Верно").assertExists()
        assertEquals(1,app.store.state.value.session?.index)
        assertEquals(false,app.store.state.value.session?.answers?.values?.first()?.firstCorrect)
    }
    @Test fun navigationDarkThemeAndBackupState(){
        runBlocking{app.store.profile(app.store.state.value.profile.copy(onboarded=true,nativeLanguage="Английский"))};rule.waitForIdle()
        click("Курс");rule.onNodeWithText("Ваш маршрут").assertExists();screenshot("03-course")
        click("Практика");rule.onNodeWithText("Разговорные ситуации").assertExists()
        click("Слова");click("Добавить своё слово");rule.onNodeWithText("Слово или выражение").performTextInput("проверка");rule.onNodeWithText("Значение или перевод").performTextInput("test");click("Добавить")
        rule.waitUntil(5000){app.store.state.value.customWords.isNotEmpty()};click("Профиль");click("Тёмная")
        rule.waitUntil(5000){app.store.state.value.profile.theme=="dark"};screenshot("04-dark-profile")
        click("Сегодня");rule.onNodeWithText("Живо").assertExists();screenshot("05-dark-home")
        val copy=ru.zhivo.app.data.StateCodec.decode(app.store.export(),app.course);assertEquals("проверка",copy.customWords.single().word)
    }
}
