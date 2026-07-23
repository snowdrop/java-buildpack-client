package dev.snowdrop.buildpack.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

            // Test with leading slash
            String wordWithSlash = JsonUtils.getValue(root, "/aWord");
            assertEquals("wibble", wordWithSlash);

            String nestedWord = JsonUtils.getValue(root, "models/patent/color");
            assertEquals("red",nestedWord);

            // Test nested with leading slash
            String nestedWordWithSlash = JsonUtils.getValue(root, "/models/patent/color");
            assertEquals("red", nestedWordWithSlash);

            String number = JsonUtils.getValue(root, "aNumber");
            assertEquals("1337", number);

            List<String> wordList = JsonUtils.getArray(root, "heels");
            assertNotNull(wordList);
            assertEquals(3, wordList.size());

            // Test array with leading slash
            List<String> wordListWithSlash = JsonUtils.getArray(root, "/heels");
            assertNotNull(wordListWithSlash);
            assertEquals(3, wordListWithSlash.size());

            List<String> numberList = JsonUtils.getArray(root, "sizes");
            assertNotNull(numberList);
            assertEquals(2, numberList.size());
        } catch (JsonMappingException e) {
            fail(e);
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    void testGetValueArrayIndexTraversal() throws Exception {
        // JSON with nested arrays: /name/0/thing/2/name style paths
        String json = "{\"items\":[{\"name\":\"first\",\"tags\":[\"a\",\"b\",\"c\"]},{\"name\":\"second\",\"tags\":[\"x\",\"y\",\"z\"]}]}";

        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode root = om.readTree(json);

        // Access object field then array index then field
        String name = JsonUtils.getValue(root, "items/0/name");
        assertEquals("first", name);

        String secondName = JsonUtils.getValue(root, "/items/1/name");
        assertEquals("second", secondName);

        // Access object field then array index then array index (nested arrays)
        String tag = JsonUtils.getValue(root, "items/0/tags/2");
        assertEquals("c", tag);

        String lastTag = JsonUtils.getValue(root, "/items/1/tags/0");
        assertEquals("x", lastTag);
    }

    @Test
    void testGetValueArrayIndexOutOfBounds() throws Exception {
        String json = "{\"items\":[{\"name\":\"only\"}]}";

        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode root = om.readTree(json);

        // Out-of-bounds index returns null
        String val = JsonUtils.getValue(root, "items/5/name");
        assertNull(val);
    }

    @Test
    void testGetValueArrayNonNumericIndexThrows() throws Exception {
        String json = "{\"items\":[{\"name\":\"only\"}]}";

        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode root = om.readTree(json);

        // Non-numeric path segment against an array should throw
        assertThrows(IllegalArgumentException.class, () ->
            JsonUtils.getValue(root, "items/notANumber/name"));
    }
}
