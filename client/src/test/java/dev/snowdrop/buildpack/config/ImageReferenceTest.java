package dev.snowdrop.buildpack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ImageReferenceTest {

    private static class P {
        public String val;
        public String expected;
        public P(String a, String b){
            this.val = a;
            this.expected = b;
        }
    }

    @Test
    void checkNullImageReference(){
        IllegalStateException is = assertThrows(IllegalStateException.class, () -> new ImageReference(null));
    }
    
    @Test
    void checkImageReference(){
        ImageReference ir2 = new ImageReference("wibble");
        assertEquals("docker.io/wibble:latest", ir2.getCanonicalReference());
    }

    @Test
    void checkAll(){

        //validate permutations/combinations of docker image reference elements

        // [[host/]|[host:port/]]repo[:tag][@digest]

        //slighly complicated by needing to differentiate between stiletto/fish and localhost/fish, where the 2nd has a host
        //also, no host, means docker.io, and index.docker.io means docker.io 

        List<P> hosts = listOf( new P("","docker.io"), new P("docker.io", "docker.io"), new P("localhost", "localhost"), new P("index.docker.io", "docker.io"), new P("quay.io", "quay.io"));
        List<P> ports = listOf( new P("", null), new P("9999", "9999") );
        List<String> repos = Arrays.asList(new String[]{"fish", "fish/wibble", "fish/wibble/stiletto", "fish/wibble/stiletto/kitten"});
        List<P> tags = listOf( new P("", "latest"), new P("tag", "tag"));
        List<P> digests = listOf( new P("", null), new P("sha256:d51bd558b181b918fe759c3166bc2d7c6e1c6b4b695a1a0bd7abfbc6bb2f89e4", "sha256:d51bd558b181b918fe759c3166bc2d7c6e1c6b4b695a1a0bd7abfbc6bb2f89e4"));

        for(P host: hosts){
            for(P port: ports){
                for(String repo : repos){
                    for(P tag : tags){
                        for(P digest : digests){

                            //we can't do port, if we don't have host!
                            if(host.val.equals("") && !port.val.equals("")) continue;

                            //assemble the test ref according to docker reference rules.
                            String testRef = host.val + (host.val.equals("")?"":(!port.val.equals("")?":"+port.val+"/":"/")) + repo + (tag.val.equals("")?"":":"+tag.val) + (digest.val.equals("")?"":"@"+digest.val);

                            //assemble the expected ref (easier, as host is now mandatory)
                            String expected = host.expected + (port.expected!=null?":"+port.expected+"/":"/") + repo + ((tag.val.equals("")&&!digest.val.equals(""))?"":":"+tag.expected) + (digest.expected!=null?"@"+digest.expected:"");

                            //generate reference from test ref string.
                            ImageReference ref = new ImageReference(testRef);

                            //validate properties.
                            assertEquals(expected, ref.getCanonicalReference(), "Test: "+testRef+" Expected: "+expected);
                            assertEquals(host.expected, ref.getHost());
                            assertEquals(port.expected, ref.getPort());
                            assertEquals(repo, ref.getRepo());
                            if(tag.val.equals("")){
                                if(digest.val.equals("")){
                                    assertEquals(tag.expected, ref.getTag());
                                }else{
                                    assertEquals(null, ref.getTag());
                                }
                            } else {
                                assertEquals(tag.expected, ref.getTag());
                            }                              
                            assertEquals(digest.expected, ref.getDigest());
                        }
                    }
                }
            }
        }
    }

    private List<P> listOf(P... p){
        return Arrays.asList(p);
    }
}
