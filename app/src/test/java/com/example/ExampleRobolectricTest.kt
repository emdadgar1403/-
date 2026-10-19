package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GemmaAsrManager
import com.example.audio.AudioRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AvaNegar", appName)
  }

  @Test
  fun `verify audio recorder 16kHz and 30s chunk limit constants`() {
    assertEquals(16000, AudioRecorder.SAMPLE_RATE)
    assertEquals(32000, AudioRecorder.BYTES_PER_SECOND)
    assertTrue("Chunk duration must be <= 30 seconds for Gemma 4", AudioRecorder.MAX_CHUNK_DURATION_SEC <= 30)
  }

  @Test
  fun `verify official gemma persian asr prompt`() {
    val expectedPrompt = "Transcribe the following speech segment in Persian (Farsi) into Persian text.\n" +
            "Only output the transcription, with no newlines. Write digits as numbers."
    assertEquals(expectedPrompt, GemmaAsrManager.OFFICIAL_PERSIAN_PROMPT)
  }
}
