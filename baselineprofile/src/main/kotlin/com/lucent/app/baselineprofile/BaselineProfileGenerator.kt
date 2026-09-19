package com.lucent.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val targetPackageName = "com.jiaying.yuan.lucentapp"

    @Test
    fun coldStart() = baselineProfileRule.collect(
        packageName = targetPackageName,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

}
