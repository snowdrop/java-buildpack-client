package dev.snowdrop.buildpack.docker;

import java.util.List;

import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientConfigDelegate;

import dev.snowdrop.buildpack.config.ImageReference;
import dev.snowdrop.buildpack.config.RegistryAuthConfig;

class AuthDelegatingDockerClientConfig extends DockerClientConfigDelegate {

    private List<RegistryAuthConfig> registryAuthInfo;

    public AuthDelegatingDockerClientConfig(DockerClientConfig delegate) {
        super(delegate);
    }

    public void setRegistryAuthConfigs(List<RegistryAuthConfig> registryAuthInfo) {
        this.registryAuthInfo = registryAuthInfo;
    }

    @Override
    public AuthConfig effectiveAuthConfig(String imageName) {
        AuthConfig fallbackAuthConfig;
        try {
            fallbackAuthConfig = super.effectiveAuthConfig(imageName);
        } catch (Exception e) {
            fallbackAuthConfig = new AuthConfig();
        }

        // try and obtain more accurate auth config using our resolution
        final ImageReference parsed = new ImageReference(imageName);
        String address = parsed.getPort()!=null ? parsed.getHost()+":"+parsed.getPort() : parsed.getHost();
        
        if(registryAuthInfo!=null) {
            for(RegistryAuthConfig rac : registryAuthInfo){
                if(address.equals(rac.getRegistryAddress())){
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

        return fallbackAuthConfig;
    }
}