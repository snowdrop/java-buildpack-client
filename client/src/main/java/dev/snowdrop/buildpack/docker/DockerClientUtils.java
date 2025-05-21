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
import dev.snowdrop.buildpack.config.HostAndSocketConfig;
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

  public static DockerClient getDockerClient(HostAndSocketConfig runtimeInfo) {
    return getDockerClient(runtimeInfo, new ArrayList<RegistryAuthConfig>(){});
  }

  /**
   * Simple util to get a DockerClient for the platform. probably needs more work
   * for other platforms, and we may want a way to configure authentication etc.
   */
  public static DockerClient getDockerClient(HostAndSocketConfig runtimeInfo, List<RegistryAuthConfig> authConfigs) {
    if(runtimeInfo == null || !runtimeInfo.getHost().isPresent() || !runtimeInfo.getSocket().isPresent()) {
      log.warn("Supplied host/socket was null, attempting to use auto-configured defaults");
      return getDockerClient(probeContainerRuntime(runtimeInfo), authConfigs);
    }

    log.debug("Using dockerhost " + runtimeInfo.getHost().get());
    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(runtimeInfo.getHost().get())
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

  public static HostAndSocketConfig probeContainerRuntime(HostAndSocketConfig overrides) {
    log.debug("Probing for container runtime... "+(overrides!=null ? "H:"+overrides.getHost()+" S:"+overrides.getSocket() : "no overrides"));
    if(overrides!=null){
      //if user has supplied both host & socket, we don't need to probe at all, use their values.
      //(using isEmpty as isBlank is jdk11 onwards)
      if(overrides.getHost().isPresent() && overrides.getSocket().isPresent()){
        return new HostAndSocketConfig(overrides.getHost().get(),overrides.getSocket().get());  
      }
    }

    try{
      //configure the override values as Optionals.
      Optional<String> dockerHost = overrides==null ? Optional.empty() : overrides.getHost();
      //for dockerhost, if user override was null, try to honor the env var
      if(!dockerHost.isPresent()){
        dockerHost = Optional.ofNullable(System.getenv("DOCKER_HOST"));
      }
      Optional<String> dockerSocket = overrides==null ? Optional.empty() : overrides.getSocket();

      //if we now have a host & socket, we are done.
      if(dockerHost.isPresent() && dockerSocket.isPresent()){
        log.debug("Using docker host "+dockerHost.get()+" and socket "+dockerSocket.get());
        return new HostAndSocketConfig(dockerHost.get(),dockerSocket.get());
      }

      //if dockerhost is specified, but docker socket is not, test if dockerhost is podman rootful,
      //and autoconfigure dockersocket.. otherwise invoking podman as the user may result in the 
      //user socket being selected for use with the rootful host, leading to failure.
      if ( dockerHost.isPresent() && !dockerSocket.isPresent() && 
         ( "unix:///var/run/podman/podman.sock".equals(dockerHost.get()) || "unix:///run/podman/podman.sock".equals(dockerHost.get()) )){
        log.debug("Using podman rootful host "+dockerHost.get()+" and socket "+dockerHost.get().substring("unix://".length()));
        return new HostAndSocketConfig(dockerHost.get(), dockerHost.get().substring("unix://".length()));
      }

      //we are still missing a host or socket, so we need to probe for them.
      //try to obtain podman socket path.. 
      log.info("Testing for podman/docker...");
      DockerClientUtils.CmdResult cr = DockerClientUtils.start(PODMAN_SOCKET);
      if(cr.rc==0){
        log.info("Podman detected, configuring.");
        String socket = cr.output.get(0);
        if(socket.startsWith("unix://")){
          socket = socket.substring("unix://".length());
        }
        log.debug("Using derived socket path value of "+socket+" from podman cli invocation.");
        //podman was present, use podman to retrieve dockerhost value
        switch (OperatingSytem.getOperationSystem()) {
          case WIN:{
            DockerClientUtils.CmdResult scmd = DockerClientUtils.start(WIN_PODMAN_HOST);
            if(scmd.rc==0){
              String fixedhost = scmd.output.get(0).replaceAll("\\\\", "/");
              return new HostAndSocketConfig(dockerHost.orElse(fixedhost), dockerSocket.orElse(socket));
            }else{
              log.warn("Unable to obtain podman socket path from podman, using internal default");
              return new HostAndSocketConfig(dockerHost.orElse("npipe:////./pipe/docker_engine"),dockerSocket.orElse("/var/run/docker.sock"));
            }
          }
          case LINUX:{
            DockerClientUtils.CmdResult scmd = DockerClientUtils.start(LIN_PODMAN_HOST);
            if(scmd.rc==0){
              return new HostAndSocketConfig(dockerHost.orElse(scmd.output.get(0)), dockerSocket.orElse(socket));
            }else{
              log.warn("Unable to obtain podman socket path from podman, using internal default");
              return new HostAndSocketConfig(dockerHost.orElse("unix:///var/run/podman.sock"), dockerSocket.orElse("/var/run/podman.sock"));
            }
          }
          case MAC:{
            DockerClientUtils.CmdResult scmd = DockerClientUtils.start(MAC_PODMAN_HOST);
            if(scmd.rc==0){
              return new HostAndSocketConfig(dockerHost.orElse(scmd.output.get(0)), dockerSocket.orElse(socket));
            }else{
              log.warn("Unable to obtain podman socket path from podman, using internal default");
              return new HostAndSocketConfig(dockerHost.orElse("unix:///var/run/podman.sock"), dockerSocket.orElse("/var/run/podman.sock"));
            }
          }
          case UNKNOWN:{
            log.warn("Unable to identify Operating System, you may need to specify docker host / docker socket manually");
            return new HostAndSocketConfig(dockerHost.orElse("unix:///var/run/podman.sock"), dockerSocket.orElse("/var/run/podman.sock"));
          }
        }
      }else{
        log.info("Assuming docker, configuring.");
        //failed to obtain podman socket path, assuming docker.. 
        switch (OperatingSytem.getOperationSystem()) {
          case WIN:{
            return new HostAndSocketConfig(dockerHost.orElse("npipe:////./pipe/docker_engine"),dockerSocket.orElse("/var/run/docker.sock"));
          }
          case LINUX:{
            return new HostAndSocketConfig(dockerHost.orElse("unix:///var/run/docker.sock"), dockerSocket.orElse("/var/run/docker.sock"));
          }
          case MAC:{
            return new HostAndSocketConfig(dockerHost.orElse("unix:///var/run/docker.sock"), dockerSocket.orElse("/var/run/docker.sock"));
          }
          case UNKNOWN:{
            log.warn("Unable to identify Operating System, you may need to specify docker host / docker socket manually");
            return new HostAndSocketConfig(dockerHost.orElse("unix:///var/run/docker.sock"), dockerSocket.orElse("/var/run/docker.sock"));
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
