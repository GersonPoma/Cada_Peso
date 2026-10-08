package com.presupuesto.comun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas ({@code @Scheduled}) de la aplicación. */
@Configuration
@EnableScheduling
public class ProgramacionConfig {}
