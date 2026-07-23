package dev.snowdrop.buildpack.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtils {
    public static String getValue(JsonNode root, String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String[] parts = path.split("/");
        JsonNode next = null;
        if(!root.isArray()){
          next = root.get(parts[0]);
        }else{
          try{
            next = root.get(Integer.parseInt(parts[0]));  
          }catch(NumberFormatException nfe){
            throw new IllegalArgumentException("Invalid path, expected array index but got: "+parts[0]+" for path: "+path);
          }
        }
        if (next != null && parts.length > 1) {
          return getValue(next, path.substring(path.indexOf("/") + 1));
        }
        if (next == null) {
          return null;
        }   
        return next.asText();       
      }
   public static List<String> getArray(JsonNode root, String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
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
