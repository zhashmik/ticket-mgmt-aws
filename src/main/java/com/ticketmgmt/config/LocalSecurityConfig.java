package com.ticketmgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile({"local", "docker"})
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .headers(h -> h.frameOptions(f -> f.disable())) // allow H2 console frames
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
