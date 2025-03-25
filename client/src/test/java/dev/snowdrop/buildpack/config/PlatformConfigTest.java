package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class PlatformConfigTest {
    @Test
    void checkPlatformLevel(){
        PlatformConfig pc1 = new PlatformConfig(null, null, null, null, null);
        assertNotNull(pc1.getPlatformLevel());

        PlatformConfig pc2 = new PlatformConfig("0.7", null, null, null, null);
        assertNotNull(pc2.getPlatformLevel());
        assertEquals("0.7", pc2.getPlatformLevel());
    }

    @Test
    void checkEnv() {
        PlatformConfig pc1 = new PlatformConfig(null, null, null, null, null);
        assertNotNull(pc1.getEnvironment());

        Map<String,String> m = new HashMap<>();
        PlatformConfig pc2 = new PlatformConfig(null, null, m, null, null);
    }

    @Test
    void checkLifecycleImage() {
        PlatformConfig pc1 = new PlatformConfig(null, null, null, null, null);
        assertNull(pc1.getLifecycleImage());

        PlatformConfig pc2 = new PlatformConfig(null, new ImageReference("fish"), null, null, null);
        assertNotNull(pc2.getLifecycleImage());
        assertEquals(new ImageReference("fish").getCanonicalReference(), pc2.getLifecycleImage().getCanonicalReference());
    }

    @Test
    void checkTrustBuilder() {
        PlatformConfig pc1 = new PlatformConfig(null, null, null, null, null);
        assertNull(pc1.getTrustBuilder());

        PlatformConfig pc2 = new PlatformConfig(null, null, null, true, null);
        assertTrue(pc2.getTrustBuilder());

        PlatformConfig pc3 = new PlatformConfig(null, null, null, false, null);
        assertFalse(pc3.getTrustBuilder());
    }

    @Test
    void checkDebugScript() {
        PlatformConfig pc1 = new PlatformConfig(null, null, null, null, null);
        assertNull(pc1.getPhaseDebugScript());

        PlatformConfig pc2 = new PlatformConfig(null, null, null, true, "echo 'hello world'");
        assertNotNull(pc2.getPhaseDebugScript());
    }
    
}
