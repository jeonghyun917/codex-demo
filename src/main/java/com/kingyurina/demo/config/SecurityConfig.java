package com.kingyurina.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                pathPattern("/"),
                                pathPattern("/healthz"),
                                pathPattern("/quant"),
                                pathPattern("/dashboard"),
                                pathPattern("/atelier"),
                                pathPattern("/stocks"),
                                pathPattern("/stocks/**"),
                                pathPattern("/signals/**"),
                                pathPattern("/etfs"),
                                pathPattern("/etfs/**"),
                                pathPattern("/error"),
                                pathPattern("/css/**"),
                                pathPattern("/design-concepts/**"),
                                pathPattern("/images/**"),
                                pathPattern("/js/**"),
                                pathPattern("/favicon.ico")).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
