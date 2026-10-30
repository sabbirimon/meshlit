package com.meshlit.openclaw

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.meshlit.settings.SettingsRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33])
class AndroidControlPersistenceTest {
    @Test fun failedCommitCannotExposeAnUncommittedGrant() {
        val context=RuntimeEnvironment.getApplication()
        val actual=context.getSharedPreferences("android-control-failed-write-test",Context.MODE_PRIVATE)
        actual.edit().clear().commit()
        val failure=object:SharedPreferences by actual {
            override fun edit():SharedPreferences.Editor {
                val edit=actual.edit()
                return object:SharedPreferences.Editor by edit {
                    // Simulate Android updating its in-memory map before reporting disk failure.
                    override fun putBoolean(key:String?,value:Boolean):SharedPreferences.Editor {edit.putBoolean(key,value);return this}
                    override fun putStringSet(key:String?,values:MutableSet<String>?):SharedPreferences.Editor {edit.putStringSet(key,values);return this}
                    override fun commit():Boolean {edit.commit();return false}
                }
            }
        }
        val control=AndroidControl(context,SettingsRepository(context),failure)
        assertThrows(IllegalStateException::class.java){control.setEnabled(true)}
        assertFalse(control.enabled());assertFalse(control.allApps())
        assertThrows(IllegalStateException::class.java){control.setAllApps(true)}
        assertFalse(control.enabled())
        actual.edit().clear().commit()
    }
    @Test fun successfulSettingsAreVisibleToANewControllerAndRevocationPersists() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=context.getSharedPreferences("android-control-success-test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val control=AndroidControl(context,SettingsRepository(context),prefs)
        control.setEnabled(true);control.setAllApps(true);control.savePackages(setOf("com.example.allowed"))
        val reopened=AndroidControl(context,SettingsRepository(context),prefs)
        assertEquals(!com.meshlit.BuildConfig.PLAY_REVIEW,reopened.enabled())
        assertTrue(reopened.allApps());assertEquals(setOf("com.example.allowed"),reopened.packages())
        reopened.setEnabled(false);assertFalse(control.enabled())
        prefs.edit().clear().commit()
    }
}
