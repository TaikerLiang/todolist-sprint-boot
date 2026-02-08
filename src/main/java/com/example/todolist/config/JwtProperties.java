package com.example.todolist.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private AccessToken access = new AccessToken();
    private RefreshToken refresh = new RefreshToken();
    private String issuer;

    @Getter
    @Setter
    public static class AccessToken {
        private String secret;
        private Long expiration;
    }

    @Getter
    @Setter
    public static class RefreshToken {
        private String secret;
        private Long expiration;
        private Long gracePeriod;
    }
}
