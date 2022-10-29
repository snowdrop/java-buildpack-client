package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ContentTest {
    //simple test to catch if we change the public API by mistake.
    @Test
    void apiRegressionTest() throws Exception{
        Content c = new Content(){
            @Override
            public List<ContainerEntry> getContainerEntries() {
                return new StringContent("/fish", "stiletto").getContainerEntries();
            }

        };

        assertNotNull(c.getContainerEntries());
        assertEquals(1,c.getContainerEntries().size());
        ContainerEntry a = c.getContainerEntries().get(0);
        assertEquals("/fish", a.getPath());   
    }    
}
