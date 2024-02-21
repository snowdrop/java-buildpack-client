package dev.snowdrop.buildpack.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtilsTest {
    @Test
    void testJsonUtils() {

        String json = "{\"heels\":[\"kitten\",\"stiletto\",\"wedge\"], \"aNumber\":1337, \"aWord\":\"wibble\", \"sizes\":[11,12], \"models\":{\"patent\":{\"color\":\"red\"}}}}";
        
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            JsonNode root = om.readTree(json);

            String word = JsonUtils.getValue(root, "aWord");
            assertEquals("wibble", word);

            String nestedWord = JsonUtils.getValue(root, "models/patent/color");
            assertEquals("red",nestedWord);

            String number = JsonUtils.getValue(root, "aNumber");
            assertEquals("1337", number);

            List<String> wordList = JsonUtils.getArray(root, "heels");
            assertNotNull(wordList);
            assertEquals(3, wordList.size());

            List<String> numberList = JsonUtils.getArray(root, "sizes");
            assertNotNull(numberList);
            assertEquals(2, numberList.size());
        } catch (JsonMappingException e) {
            fail(e);
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }
}
