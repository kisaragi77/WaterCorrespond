package com.example.root.ffttest2;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry; // 解决了 InstrumentationRegistry 无法解析的问题
import androidx.test.ext.junit.runners.AndroidJUnit4; // 解决了 AndroidJUnit4 无法解析的问题
//import android.support.test.InstrumentationRegistry;
//import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;


/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
//        Context appContext = InstrumentationRegistry.getTargetContext();
        Context appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.example.root.ffttest2", appContext.getPackageName());
    }
}
