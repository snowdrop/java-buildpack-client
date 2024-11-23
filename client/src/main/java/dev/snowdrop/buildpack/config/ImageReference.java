package dev.snowdrop.buildpack.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageReference {

    String LEGACY_HOST = "index.docker.io";
    String DEFAULT_HOST = "docker.io";
    String DEFAULT_TAG = "latest";

    // Java version of the regex used by docker Go library to parse image names.
    String regx = "^((?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])(?:(?:\\.(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]))+)?(?::[0-9]+)?/)?[a-z0-9]+(?:(?:(?:[._]|__|[-]*)[a-z0-9]+)+)?(?:(?:/[a-z0-9]+(?:(?:(?:[._]|__|[-]*)[a-z0-9]+)+)?)+)?)(?::([\\w][\\w.-]{0,127}))?(?:@([A-Za-z][A-Za-z0-9]*(?:[-_+.][A-Za-z][A-Za-z0-9]*)*[:]\\p{XDigit}{32,}))?$";
    Pattern p = Pattern.compile(regx);

    private final String hostname;
    private final boolean hostPresent;
    private final String port;
    private final boolean portPresent;
    private final String repository;
    private final boolean repositoryPresent;
    private final String tag;
    private final boolean tagPresent;
    private final String digest;
    private final boolean digestPresent;

    private final String reference;

    public ImageReference(String s) {
        if (s == null) {
            throw new IllegalStateException("ImageReference cannot be built from null");
        }
        this.reference = s;
        Matcher m = p.matcher(s);
        if (m.find()) {
            String hostportrepo = useHost(m.group(1));
            if (!hostportrepo.contains(":") && !hostportrepo.contains("/")) {
                // simple case, no port, no paths..
                hostname = DEFAULT_HOST;
                hostPresent = false;
                port = null;
                portPresent = false;
                repository = hostportrepo;
                repositoryPresent = true;
            } else if (!hostportrepo.contains(":") && hostportrepo.contains("/")) {
                // no port, but has paths..
                port = null;
                portPresent = false;
                String firstpart = hostportrepo.substring(0, hostportrepo.indexOf("/"));
                if (firstpart.startsWith("localhost") || firstpart.contains(".")) {
                    hostname = useHost(firstpart);
                    hostPresent = firstpart != null;
                    repository = hostportrepo.substring(firstpart.length() + 1);
                } else {
                    hostname = DEFAULT_HOST;
                    hostPresent = false;
                    repository = hostportrepo;
                }
                repositoryPresent = true;
            } else if (hostportrepo.contains(":") && hostportrepo.contains("/")) {
                // has port.. easy to determine host.
                hostname = useHost(hostportrepo.substring(0, hostportrepo.indexOf(":")));
                hostPresent = hostportrepo.substring(0, hostportrepo.indexOf(":")) != null;
                port = hostportrepo.substring(hostportrepo.indexOf(":") + 1, hostportrepo.indexOf("/"));
                portPresent = port!=null;
                repository = hostportrepo.substring(hostportrepo.indexOf("/") + 1, hostportrepo.length());
                repositoryPresent = true;
            } else {
                throw new IllegalStateException("Bad imageref " + s);
            }

            digest = m.group(3);
            digestPresent = m.group(3) != null;

            tag = useTag(m.group(2), digestPresent);
            tagPresent = m.group(2) != null;


        } else {
            throw new IllegalStateException("Bad imageref " + s);
        }
    }

    private String useHost(String host) {
        if (host == null || host.equals(LEGACY_HOST)) {
            return DEFAULT_HOST;
        } else {
            return host;
        }
    }

    private String useTag(String tag, boolean digestPresent) {
        if (tag == null && !digestPresent) {
            return DEFAULT_TAG;
        } else{
            return tag;
        }
    }

    @Override
    public int hashCode() {
        String ref = this.getCanonicalReference();
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ref == null) ? 0 : ref.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ImageReference other = (ImageReference) obj;
        String thisRef = this.getCanonicalReference();
        String otherRef = other.getCanonicalReference();
        if (thisRef == null) {
            if (otherRef != null)
                return false;
        } else if (!thisRef.equals(otherRef))
            return false;
        return true;
    }

    public String toString() {
        return this.getCanonicalReference();
    }

    public String getReference() {
        return reference;
    }
    public String getReferenceWithLatest() {
        if(!tagPresent() && !digestPresent()){
            return reference+":latest";
        }else{
            return reference;
        }
    }
    public String getCanonicalReference() {
        return hostname + (port != null ? ":" + port : "") + "/" + repository + (tag != null? ":" + tag : "") + (digest != null ? "@" + digest : "");
    }

    public String getHost(){
        return hostname;
    }
    public boolean hostPresent(){
        return hostPresent;
    }
    public String getPort(){
        return port;
    }
    public boolean portPresent(){
        return portPresent;
    }
    public String getRepo(){
        return repository;
    }
    public boolean repoPresent(){
        return repositoryPresent;
    }
    public String getTag(){
        return tag;
    }
    public boolean tagPresent(){
        return tagPresent;
    }
    public String getDigest(){
        return digest;
    }
    public boolean digestPresent(){
        return digestPresent;
    }
}
