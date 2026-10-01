package com.presupuesto.comun.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AutenticacionEntryPointPersonalizado entryPointPersonalizado;
    private final AccesoDenegadoHandlerPersonalizado accessDeniedHandlerPersonalizado;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/actuator/health", "/api/v1/auth/**")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .exceptionHandling(
                        exceptions -> exceptions
                                .authenticationEntryPoint(entryPointPersonalizado)
                                .accessDeniedHandler(accessDeniedHandlerPersonalizado));
        return http.build();
    }
}
