package dev.snowdrop.buildpack.config;

import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class RegistryAuthConfig {
    public static RegistryAuthConfigBuilder builder() {
        return new RegistryAuthConfigBuilder();
    }

    private String registryAddress;
    private String registryToken;
    private String username;
    private String auth;
    private String email;
    private String identityToken;
    private String password;

    public RegistryAuthConfig(
        String registryAddress,
        String registryToken,
        String username,
        String auth,
        String email,
        String identityToken,
        String password
    ){
        this.registryAddress = registryAddress;
        this.registryToken = registryToken;
        this.username = username;
        this.auth = auth;
        this.email = email;
        this.identityToken = identityToken;
        this.password = password;
    }

    public String getRegistryAddress(){
        return registryAddress;
    }

    public String getRegistryToken() {
        return registryToken;
    }

    public String getUsername() {
        return username;
    }

    public String getAuth() {
        return auth;
    }

    public String getEmail() {
        return email;
    }

    public String getIdentityToken() {
        return identityToken;
    }

    public String getPassword() {
        return password;
    }    
    
}
