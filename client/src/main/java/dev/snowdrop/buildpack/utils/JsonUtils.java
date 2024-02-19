package dev.snowdrop.buildpack.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
   public static List<String> getArray(JsonNode root, String path) {
        String[] parts = path.split("/");
        JsonNode next = root.get(parts[0]);
        if (next != null && parts.length > 1) {
          return getArray(next, path.substring(path.indexOf("/") + 1));
        }
        if (next == null) {
          return null;
        }
        if(next.isArray()){
          ArrayList<String> vals = new ArrayList<>();
          Iterator<JsonNode> els = next.elements();
          while(els.hasNext()){
            vals.add(els.next().asText());
          }
          return vals;
        }
        return null;
      }  
}
