package dev.snowdrop.buildpack.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class ContainerEntryTest {
    //simple test to catch if we change the public API by mistake.
    @Test
    void apiRegressionTest() throws Exception{
        ContainerEntry ce = new ContainerEntry(){

            @Override
            public String getPath() {
                return "fish";
            }

            @Override
            public long getSize() {
                return 1337;
            }

            @Override
            public Integer getMode() {
                return 0755;
            }

            @Override
            public DataSupplier getDataSupplier() {
                return new DataSupplier(){
                    @Override
                    public InputStream getData() {
                        return new ByteArrayInputStream("FISH".getBytes());
                    }
                };
            }
            
        };

        assertEquals("fish", ce.getPath());
        assertEquals(1337, ce.getSize());
        assertEquals(0755, ce.getMode());


        assertNotNull(ce.getDataSupplier().getData());
        BufferedReader br = new BufferedReader(new InputStreamReader(ce.getDataSupplier().getData(), StandardCharsets.UTF_8));
        assertEquals("FISH",br.readLine());
    
    }
}
