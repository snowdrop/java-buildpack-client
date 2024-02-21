package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ImageReferenceTest {

    @Test
    void checkImageReference(){
        ImageReference ir1 = new ImageReference(null);
        assertNull(ir1.getReference());

        ImageReference ir2 = new ImageReference("wibble");
        assertEquals("wibble", ir2.getReference());
    }
}
