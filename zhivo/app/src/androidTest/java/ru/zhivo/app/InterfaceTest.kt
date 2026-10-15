package ru.zhivo.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Bitmap
import java.io.File

@RunWith(AndroidJUnit4::class)
class InterfaceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<ZhivoApplication>()
    @Before fun clean() {runBlocking{app.store.reset()};rule.waitForIdle()}
    private fun click(text:String){val node=rule.onNodeWithText(text);runCatching{node.performScrollTo()};node.performClick();rule.waitForIdle()}
    private fun screenshot(name:String){InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->File(app.getExternalFilesDir(null),"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
    @Test fun onboardingLessonWrongRetryAndRecreation(){
        click("Настроить обучение");click("Таджикский");click("Дальше");click("Начать без диагностики")
        rule.waitUntil(5000){app.store.state.value.profile.onboarded};rule.waitForIdle();screenshot("01-home")
        click("Начать занятие");click("Попробовать · 9 заданий");click("кот");click("Проверить")
        rule.onNodeWithText("Попробуйте ещё раз").assertExists();click("мама");click("Проверить");rule.onNodeWithText("Получилось").assertExists()
        click("Дальше");rule.onNodeWithText("Ваш ответ").performTextInput("а");click("Проверить");rule.onNodeWithText("Верно").assertExists();screenshot("02-lesson")
        rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithText("Верно").assertExists()
        assert(app.store.state.value.session?.index==1)
        assert(app.store.state.value.session?.answers?.values?.first()?.firstCorrect==false)
    }
    @Test fun navigationDarkThemeAndBackupState(){
        runBlocking{app.store.profile(app.store.state.value.profile.copy(onboarded=true,nativeLanguage="Английский"))};rule.waitForIdle()
        click("Курс");rule.onNodeWithText("Ваш маршрут").assertExists();screenshot("03-course")
        click("Практика");rule.onNodeWithText("Разговорные ситуации").assertExists()
        click("Слова");click("Добавить своё слово");rule.onNodeWithText("Слово или выражение").performTextInput("проверка");rule.onNodeWithText("Значение или перевод").performTextInput("test");click("Добавить")
        rule.waitUntil(5000){app.store.state.value.customWords.isNotEmpty()};click("Профиль");click("Тёмная")
        rule.waitUntil(5000){app.store.state.value.profile.theme=="dark"};screenshot("04-dark-profile")
        click("Сегодня");rule.onNodeWithText("Живо").assertExists();screenshot("05-dark-home")
        val copy=ru.zhivo.app.data.StateCodec.decode(app.store.export(),app.course);assert(copy.customWords.single().word=="проверка")
    }
}
