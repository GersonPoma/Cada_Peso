package com.presupuesto.comun.seguridad;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(String secreto, Duration expiracion) {}
