package dev.snowdrop.buildpack.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LifecycleArgsTest {
    @Test
    void testLifecycleArgs(){
        LifecycleArgs la1 = new LifecycleArgs("/command", null);
        assertEquals(1, la1.toList().size());
        assertEquals("/command", la1.toList().get(0));

        la1.addArg("-daemon");
        assertEquals(2, la1.toList().size());

        la1.addArg("-flag", "value");
        assertEquals(4, la1.toList().size());

        LifecycleArgs la2 = new LifecycleArgs("/command", "image-name");
        assertEquals(2, la2.toList().size());
        assertEquals("/command", la2.toList().get(0));
        assertEquals("image-name", la2.toList().get(1));

        la2.addArg("-flag","value");
        assertEquals(4, la2.toList().size());
        assertEquals("image-name", la2.toList().get(3));
    }
}
