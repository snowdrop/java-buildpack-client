package dev.snowdrop.buildpack.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ContainerStatusTest {
    @Test
    void testContainerStatus(){
        ContainerStatus cs1 = ContainerStatus.of(66, "fish");
        assertEquals(66,cs1.getRc());
        assertEquals("fish", cs1.getContainerId());
    }
}
