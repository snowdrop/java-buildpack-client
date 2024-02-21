package dev.snowdrop.buildpack.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.docker.ContainerUtils;

@ExtendWith(MockitoExtension.class)
public class BuildpackMetadataTest {
    @Test
    void checkRunImage(){
        String json = "{\"stack\":{\"runImage\":{\"image\":\"patent:stilettos\"}}}}";
        String image = BuildpackMetadata.getRunImageFromMetadataJSON(json);
        assertNotNull(image);
        assertEquals("patent:stilettos", image);


        String dockerJson = "{\"stack\":{\"runImage\":{\"image\":\"index.docker.io/patent:stilettos\"}}}}"; 
        String dockerImage = BuildpackMetadata.getRunImageFromMetadataJSON(dockerJson);
        assertNotNull(dockerImage);
        assertEquals("docker.io/patent:stilettos", dockerImage);

        try{
            String badJson = "{\"stack\":{\"runImage\":{\"notimage\":\"index.docker.io/patent:stilettos\"}}}}"; 
            BuildpackMetadata.getRunImageFromMetadataJSON(badJson);
            fail();
        }catch(BuildpackException be){

        }

        byte[] runTomlData = "[[images]]\n  image = \"docker.io/paketocommunity/run-java-8-ubi-base\"\n[[images]]\n  image = \"docker.io/paketocommunity/run-java-11-ubi-base\"\n[[images]]\n  image = \"docker.io/paketocommunity/run-java-17-ubi-base\"\n".getBytes();
        try (MockedStatic<? extends ContainerUtils> containerUtils = mockStatic(ContainerUtils.class)) {
            containerUtils.when(() -> ContainerUtils.getFileFromContainer(any(), any(), any())).thenReturn(runTomlData);
            List<String> runImages = BuildpackMetadata.getRunImageFromRunTOML("dummy");
            assertNotNull(runImages);
            assertEquals(3, runImages.size());
            assertTrue(runImages.contains("docker.io/paketocommunity/run-java-8-ubi-base"));
            assertTrue(runImages.contains("docker.io/paketocommunity/run-java-11-ubi-base"));
            assertTrue(runImages.contains("docker.io/paketocommunity/run-java-17-ubi-base"));
        }

    }

    @Test
    void checkSupportedPlatforms(){
        String json = "{\"lifecycle\":{\"apis\":{\"platform\":{\"supported\":[\"0.1\",\"0.2\",\"0.3\"]}}}}}";
        List<String> platforms = BuildpackMetadata.getSupportedPlatformsFromMetadata(json);
        assertNotNull(platforms);
        assertEquals(3, platforms.size());

        try{
            String badJson = "{\"lifecycle\":{\"apis\":{\"platform\":{\"tobeornottobe\":[\"0.1\",\"0.2\",\"0.3\"]}}}}}";
            BuildpackMetadata.getSupportedPlatformsFromMetadata(badJson);
            fail();            
        }catch(BuildpackException be){

        }
    }
}
