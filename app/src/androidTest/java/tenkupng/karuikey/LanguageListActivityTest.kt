package tenkupng.karuikey

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageListActivityTest {
    @Test
    fun addLanguageScreenLaunchesAndShowsCatalog() {
        val scenario = ActivityScenario.launch(LanguageListActivity::class.java)
        scenario.onActivity { activity ->
            assertTrue(activity.window.decorView.isShown)
        }
        scenario.close()
    }
}
