import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

/**
 * Root component: the app "shell". It renders the role-aware navigation bar and a <router-outlet>, the
 * placeholder where the component for the current URL is displayed.
 *
 * `standalone: true` means the component declares its own dependencies in `imports` instead of belonging to
 * an NgModule (the default style since Angular 17).
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  /** `protected`: usable from the template, but not part of the class's public API. */
  protected readonly auth = inject(AuthService);
}
