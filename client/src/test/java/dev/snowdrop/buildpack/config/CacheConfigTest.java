package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CacheConfigTest {
    @Test
    void constructorTest(){
        CacheConfig c1 = new CacheConfig("fred", null);
        assertTrue(c1.getDeleteCacheAfterBuild());
        assertEquals("fred", c1.getCacheVolumeName());
        
        CacheConfig c2 = new CacheConfig(null, null);
        assertTrue(c2.getDeleteCacheAfterBuild());
        assertNull(c2.getCacheVolumeName());

        CacheConfig c3 = new CacheConfig("fish", false);
        assertFalse(c3.getDeleteCacheAfterBuild());
        assertEquals("fish", c3.getCacheVolumeName());

        CacheConfig c4 = new CacheConfig("stilettos", true);
        assertTrue(c4.getDeleteCacheAfterBuild());
        assertEquals("stilettos", c4.getCacheVolumeName());
    }
}
