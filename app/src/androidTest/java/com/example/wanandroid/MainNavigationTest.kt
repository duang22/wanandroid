package com.example.wanandroid

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.wanandroid.ui.home.HomeFragment
import com.example.wanandroid.ui.home.HomeViewModel
import com.example.wanandroid.ui.knowledge.KnowledgeFragment
import com.example.wanandroid.ui.mine.MineFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    @Test
    fun repeatedSwitchingKeepsHomeViewAndViewModel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                val home = manager.fragments.filterIsInstance<HomeFragment>().single()
                val homeView = home.requireView()
                val model = ViewModelProvider(home)[HomeViewModel::class.java]
                val navigation = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                repeat(3) {
                    navigation.selectedItemId = R.id.nav_knowledge
                    navigation.selectedItemId = R.id.nav_mine
                    navigation.selectedItemId = R.id.nav_home
                }
                assertEquals(3, manager.fragments.size)
                assertEquals(1, manager.fragments.filterIsInstance<KnowledgeFragment>().size)
                assertEquals(1, manager.fragments.filterIsInstance<MineFragment>().size)
                assertSame(home, manager.fragments.filterIsInstance<HomeFragment>().single())
                assertSame(homeView, home.requireView())
                assertSame(model, ViewModelProvider(home)[HomeViewModel::class.java])
                assertTrue(home.isVisible)
            }
        }
    }

    @Test
    fun selectedTabAndFragmentCountSurviveActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                    .selectedItemId = R.id.nav_mine
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                assertEquals(R.id.nav_mine, navigation.selectedItemId)
                assertEquals(2, activity.supportFragmentManager.fragments.size)
                assertTrue(activity.supportFragmentManager.fragments.filterIsInstance<MineFragment>().single().isVisible)
                assertFalse(activity.supportFragmentManager.fragments.filterIsInstance<HomeFragment>().single().isVisible)
            }
        }
    }

    @Test
    fun backFromAnotherTabReturnsHomeWithoutFinishingActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                navigation.selectedItemId = R.id.nav_knowledge
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(R.id.nav_home, navigation.selectedItemId)
                assertFalse(activity.isFinishing)
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
            }
        }
    }
}
