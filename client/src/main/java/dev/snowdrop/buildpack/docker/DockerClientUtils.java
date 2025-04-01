package dev.snowdrop.buildpack.docker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.snowdrop.buildpack.config.RegistryAuthConfig;
import dev.snowdrop.buildpack.utils.OperatingSytem;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

public class DockerClientUtils {
  private static final Logger log = LoggerFactory.getLogger(DockerClientUtils.class);

  private final static String[] MAC_PODMAN_HOST = {"podman","machine","inspect","--format" ,"unix://{{.ConnectionInfo.PodmanSocket.Path}}"};
  private final static String[] LIN_PODMAN_HOST = {"podman", "info", "--format", "unix://{{.Host.RemoteSocket.Path}}"};
  private final static String[] WIN_PODMAN_HOST = {"podman","machine","inspect","--format" ,"npipe://{{.ConnectionInfo.PodmanPipe.Path}}"};
  private final static String[] PODMAN_SOCKET = {"podman", "info", "--format", "{{.Host.RemoteSocket.Path}}"};

  public static DockerClient getDockerClient() {
    return getDockerClient(probeContainerRuntime(null));
  }

  public static DockerClient getDockerClient(HostAndSocket runtimeInfo) {
    return getDockerClient(runtimeInfo, new ArrayList<RegistryAuthConfig>(){});
  }

  /**
   * Simple util to get a DockerClient for the platform. probably needs more work
   * for other platforms, and we may want a way to configure authentication etc.
   */
  public static DockerClient getDockerClient(HostAndSocket runtimeInfo, List<RegistryAuthConfig> authConfigs) {
    if (runtimeInfo == null || runtimeInfo.host == null || runtimeInfo.host.isEmpty() ||
                               runtimeInfo.socket == null || runtimeInfo.socket.isEmpty()) {
      log.warn("Supplied host/socket was null, attempting to use auto-configured defaults");
      return getDockerClient(probeContainerRuntime(runtimeInfo), authConfigs);
    }

    log.debug("Using dockerhost " + runtimeInfo.host);
    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(runtimeInfo.host)
        .build();

    AuthDelegatingDockerClientConfig addcc = new AuthDelegatingDockerClientConfig(config);
    addcc.setRegistryAuthConfigs(authConfigs);

    DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
        .dockerHost(config.getDockerHost())
        .sslConfig(config.getSSLConfig())
        .build();

    DockerClient dockerClient = DockerClientImpl.getInstance(addcc, httpClient);

    return dockerClient;
  }

  public static class HostAndSocket {
    public String host;
    public String socket;
    public HostAndSocket(String host, String socket){
      this.host = host; this.socket = socket;
    }
    public HostAndSocket(String host){
      this.host = host;
      this.socket = null;
    }
  }

  public static HostAndSocket probeContainerRuntime(HostAndSocket overrides) {
    if(overrides!=null){
      //if user has supplied both host & socket, we don't need to probe at all, use their values.
      //(using isEmpty as isBlank is jdk11 onwards)
      if(overrides.host!=null && overrides.socket!=null && !overrides.host.isEmpty() && !overrides.socket.isEmpty()){
        return overrides;
      }

      //sanitize blank/empty values to null. (also using isEmpty because isBlank is only from jdk11 onwards)
      if(overrides.host!=null && overrides.host.isEmpty()){
        overrides.host=null;
      }
      if(overrides.socket!=null && overrides.socket.isEmpty()){
        overrides.socket=null;
      }
    }else{
      //no overrides at all? make an empty one.
      overrides = new HostAndSocket(null, null);
    }

    try{
      //configure the override values as Optionals.
      Optional<String> dockerHost = Optional.ofNullable(overrides.host);
      //for dockerhost, if user override was null, try to honor the env var
      if(!dockerHost.isPresent()){
        dockerHost = Optional.ofNullable(System.getenv("DOCKER_HOST"));
      }
      Optional<String> dockerSocket = Optional.ofNullable(overrides.socket);

      //if dockerhost is specified, but docker socket is not, test if dockerhost is podman rootful,
      //and autoconfigure dockersocket.. otherwise invoking podman as the user may result in the 
      //user socket being selected for use with the rootful host, leading to failure.
      if ( dockerHost.isPresent() && !dockerSocket.isPresent() && 
         ( "unix:///var/run/podman/podman.sock".equals(dockerHost.get()) || "unix:///run/podman/podman.sock".equals(dockerHost.get()) )){
        return new HostAndSocket(dockerHost.get(), dockerHost.get().substring("unix://".length()));
      }

      //try to obtain podman socket path.. 
      log.info("Testing for podman/docker...");
      DockerClientUtils.CmdResult cr = DockerClientUtils.start(PODMAN_SOCKET);
      if(cr.rc==0){
        log.info("Podman detected, configuring.");
        String socket = cr.output.get(0);
        if(socket.startsWith("unix://")){
          socket = socket.substring("unix://".length());
        }        
        //podman was present, use podman to retrieve dockerhost value
        switch (OperatingSytem.getOperationSystem()) {
          case WIN:{
            DockerClientUtils.CmdResult scmd = DockerClientUtils.start(WIN_PODMAN_HOST);
            if(scmd.rc==0){
              String fixedhost = scmd.output.get(0).replaceAll("\\", "/");
              return new HostAndSocket(dockerHost.orElse(fixedhost), dockerSocket.orElse(cr.output.get(0)));
            }else{
              log.warn("Unable to obtain podman socket path from podman, using internal default");
              return new HostAndSocket(dockerHost.orElse("npipe:////./pipe/docker_engine"),dockerSocket.orElse("/var/run/docker.sock"));
            }
          }
          case LINUX:{
            DockerClientUtils.CmdResult scmd = DockerClientUtils.start(LIN_PODMAN_HOST);
            if(scmd.rc==0){
              return new HostAndSocket(dockerHost.orElse(scmd.output.get(0)), dockerSocket.orElse(socket));
            }else{
              log.warn("Unable to obtain podman socket path from podman, using internal default");
              return new HostAndSocket(dockerHost.orElse("unix:///var/run/podman.sock"), dockerSocket.orElse("/var/run/podman.sock"));
            }
          }
          case MAC:{
            DockerClientUtils.CmdResult scmd = DockerClientUtils.start(MAC_PODMAN_HOST);
            if(scmd.rc==0){
              return new HostAndSocket(dockerHost.orElse(scmd.output.get(0)), dockerSocket.orElse(socket));
            }else{
              log.warn("Unable to obtain podman socket path from podman, using internal default");
              return new HostAndSocket(dockerHost.orElse("unix:///var/run/podman.sock"), dockerSocket.orElse("/var/run/podman.sock"));
            }
          }
          case UNKNOWN:{
            log.warn("Unable to identify Operating System, you may need to specify docker host / docker socket manually");
            return new HostAndSocket(dockerHost.orElse("unix:///var/run/podman.sock"), dockerSocket.orElse("/var/run/podman.sock"));
          }
        }
      }else{
        log.info("Assuming docker, configuring.");
        //failed to obtain podman socket path, assuming docker.. 
        switch (OperatingSytem.getOperationSystem()) {
          case WIN:{
            return new HostAndSocket(dockerHost.orElse("npipe:////./pipe/docker_engine"),dockerSocket.orElse("/var/run/docker.sock"));
          }
          case LINUX:{
            return new HostAndSocket(dockerHost.orElse("unix:///var/run/docker.sock"), dockerSocket.orElse("/var/run/docker.sock"));
          }
          case MAC:{
            return new HostAndSocket(dockerHost.orElse("unix:///var/run/docker.sock"), dockerSocket.orElse("/var/run/docker.sock"));
          }
          case UNKNOWN:{
            log.warn("Unable to identify Operating System, you may need to specify docker host / docker socket manually");
            return new HostAndSocket(dockerHost.orElse("unix:///var/run/docker.sock"), dockerSocket.orElse("/var/run/docker.sock"));
          }
        }
      }
    }catch(Exception e){
      log.error("Error during Container Runtime Probe, verify podman/docker, or set Docker Host and Docker Socket explicitly", e);
    }
    log.error("Failed to determine docker host and docker socket path.");
    throw new IllegalStateException("Container Runtime detection failure");
  }

  private static class CmdResult {
    public final int rc;
    public final List<String> output;
    public CmdResult(int rc, List<String> out){ this.rc=rc; this.output = out;}
  }

  private static CmdResult start(String[] cmd)
  {
      try{
        log.debug("Process start "+Arrays.toString(cmd));

        // Launch and wait:
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);//merge to stderr->stdout
        Process p = pb.start();

        List<String> output = null;
        try(InputStream stdo = p.getInputStream()) {
            BufferedReader lineReader = new BufferedReader(new InputStreamReader(stdo));
            output = lineReader.lines().collect(Collectors.toList());
        }

        int rc = p.waitFor();

        log.debug("Process exit rc:"+rc +" response:"+output);
        return new CmdResult(rc,output);
      }catch(Exception e){
        List<String> failReason = new ArrayList<>();
        failReason.add("Process failed: ... ");
        for (StackTraceElement ste : e.getStackTrace()) {
          failReason.add(ste.toString());
        }
        return new CmdResult(255, failReason);
      }
  }  
}
