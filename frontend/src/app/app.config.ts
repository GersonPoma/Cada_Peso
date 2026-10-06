import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { sesionInterceptor } from './core/auth/sesion.interceptor';
import { proveerMaterial } from './core/material/proveer-material';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([sesionInterceptor])),
    ...proveerMaterial(),
  ],
};
