package dev.snowdrop.buildpack.docker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientConfigDelegate;

import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.RegistryAuthConfig;

class AuthDelegatingDockerClientConfig extends DockerClientConfigDelegate {

    private static final Logger log = LoggerFactory.getLogger(AuthDelegatingDockerClientConfig.class);

    private List<RegistryAuthConfig> registryAuthInfo;

    public AuthDelegatingDockerClientConfig(DockerClientConfig delegate) {
        super(delegate);
    }

    public void setRegistryAuthConfigs(List<RegistryAuthConfig> registryAuthInfo) {
        this.registryAuthInfo = registryAuthInfo;
    }

    @Override
    public AuthConfig effectiveAuthConfig(String imageName) {
        log.debug("Resolving authentication configuration for image "+imageName);
        AuthConfig fallbackAuthConfig;
        try {
            fallbackAuthConfig = super.effectiveAuthConfig(imageName);
            log.debug("fallback config retrieved");
        } catch (Exception e) {
            fallbackAuthConfig = new AuthConfig();
            log.debug("no fallback config available");
        }

        // try and obtain more accurate auth config using our resolution
        final ImageReference parsed = new ImageReference(imageName);
        String address = parsed.getPort()!=null ? parsed.getHost()+":"+parsed.getPort() : parsed.getHost();

        log.debug("Checking configuration for auth config for address "+address);
        
        if(registryAuthInfo!=null) {
            for(RegistryAuthConfig rac : registryAuthInfo){
                if(address.equals(rac.getRegistryAddress())){
                    log.debug("found match, configuring");
                    return new AuthConfig()
                                    .withAuth(rac.getAuth())
                                    .withEmail(rac.getEmail())
                                    .withIdentityToken(rac.getIdentityToken())
                                    .withPassword(rac.getPassword())
                                    .withRegistryAddress(rac.getRegistryAddress())
                                    .withRegistrytoken(rac.getRegistryToken())
                                    .withUsername(rac.getUsername());
                }
            }
        }

        log.debug("no match, using fallback if available");
        return fallbackAuthConfig;
    }
}