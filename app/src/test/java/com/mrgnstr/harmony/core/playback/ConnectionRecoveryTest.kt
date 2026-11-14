package com.mrgnstr.harmony.core.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRecoveryTest {
    @Test fun connectionProblemsAreRetried() {
        assertTrue(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, 0))
        assertTrue(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, 0))
        assertTrue(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, 0))
        assertTrue(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503))
    }

    @Test fun otherErrorsAreLeftAlone() {
        // A wrong password or a missing song will not be fixed by waiting.
        assertFalse(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 401))
        assertFalse(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404))
        assertFalse(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, 0))
        assertFalse(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, 0))
        assertFalse(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_DECODING_FAILED, 0))
        assertFalse(ConnectionRecovery.isConnectionError(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED, 0))
    }
}
