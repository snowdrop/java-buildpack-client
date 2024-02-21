package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.snowdrop.buildpack.Logger;

@ExtendWith(MockitoExtension.class)
public class LogConfigTest {
    @Test
    void checkLogLevel() {
        LogConfig lc1 = new LogConfig(null, null, null);
        assertEquals("info", lc1.getLogLevel());
        LogConfig lc2 = new LogConfig("debug", null, null);
        assertEquals("debug", lc2.getLogLevel());
    }

    @Test
    void checkUseTimestamps() {
        LogConfig lc1 = new LogConfig(null, null, null);
        assertTrue(lc1.getUseTimestamps());
        LogConfig lc2 = new LogConfig(null, true, null);
        assertTrue(lc2.getUseTimestamps());
        LogConfig lc3 = new LogConfig(null, false, null);
        assertFalse(lc3.getUseTimestamps());
    }

    @Test
    void checkLogger(@Mock Logger logger) {
        LogConfig lc1 = new LogConfig(null, null, null);
        assertNotNull(lc1.getLogger());
        LogConfig lc2 = new LogConfig(null, null, logger);
        assertEquals(logger, lc2.getLogger());
    }
}
