package dev.snowdrop.buildpack.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtils {
    public static String getValue(JsonNode root, String path) {
        String[] parts = path.split("/");
        JsonNode next = root.get(parts[0]);
        if (next != null && parts.length > 1) {
          return getValue(next, path.substring(path.indexOf("/") + 1));
        }
        if (next == null) {
          return null;
        }
        return next.asText();
      }    
}
