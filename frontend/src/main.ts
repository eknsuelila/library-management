/**
 * Browser entry point: the first TypeScript that runs.
 *
 * bootstrapApplication() starts Angular with the root component (AppComponent, which renders <app-root> in
 * index.html) and the app-wide providers from app.config.ts (router, HttpClient + JWT interceptor).
 * There is no AppModule: since Angular 17 apps are built from standalone components by default.
 */
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
