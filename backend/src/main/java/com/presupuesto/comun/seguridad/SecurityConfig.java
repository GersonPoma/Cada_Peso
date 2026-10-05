package com.presupuesto.comun.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final AutenticacionEntryPointPersonalizado entryPointPersonalizado;
    private final AccesoDenegadoHandlerPersonalizado accessDeniedHandlerPersonalizado;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/actuator/health", "/api/v1/auth/**", "/error")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .exceptionHandling(
                        exceptions -> exceptions
                                .authenticationEntryPoint(entryPointPersonalizado)
                                .accessDeniedHandler(accessDeniedHandlerPersonalizado))
                // Instanciado con new y no como bean: ver FiltroAutenticacionJwt.
                .addFilterBefore(
                        new FiltroAutenticacionJwt(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
