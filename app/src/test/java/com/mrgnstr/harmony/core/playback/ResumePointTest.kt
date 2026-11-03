package com.mrgnstr.harmony.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePointTest {
    @Test fun playingSongSurvivesAndKeepsItsPosition() {
        assertEquals(ResumePoint(2, true), resumePoint(5, listOf(0, 1, 5, 6)))
        assertEquals(ResumePoint(0, true), resumePoint(0, listOf(0, 1)))
    }

    @Test fun missingSongResumesAtTheNextSurvivorFromTheStart() {
        assertEquals(ResumePoint(1, false), resumePoint(3, listOf(0, 4, 5)))
        assertEquals(ResumePoint(0, false), resumePoint(0, listOf(2, 3)))
    }

    @Test fun nothingAfterTheMissingSongFallsBackToTheLast() {
        assertEquals(ResumePoint(2, false), resumePoint(9, listOf(0, 1, 2)))
    }

    @Test fun emptyQueueRestoresNothing() {
        assertNull(resumePoint(0, emptyList()))
    }
}
