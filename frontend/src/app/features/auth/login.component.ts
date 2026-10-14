import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../shared/error-message';

/** The seeded demo accounts (created by the backend's DemoDataSeeder). */
export const DEMO_ACCOUNTS = {
  librarian: { email: 'librarian@example.com', password: 'password123' },
  member: { email: 'member@example.com', password: 'password123' },
} as const;

/**
 * Login page, built with a REACTIVE FORM.
 *
 * Reactive forms are defined in TypeScript (FormBuilder below) rather than in the HTML, which makes them easy
 * to validate, test and reason about. The template connects to them with [formGroup] and formControlName.
 *
 * After logging in, each role is sent to its own home page (librarian -> dashboard, member -> catalog).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './auth-page.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** nonNullable: reset() returns to '' instead of null, which keeps the types simple (string, not string | null). */
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Fills in one of the demo accounts and logs in, so a recruiter can try each role in one click. */
  useDemo(kind: keyof typeof DEMO_ACCOUNTS): void {
    this.form.setValue({ ...DEMO_ACCOUNTS[kind] });
    this.submit();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // reveal the validation messages on every field
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homeUrl()),
      error: (err) => {
        this.error.set(errorMessage(err, 'Login failed'));
        this.loading.set(false);
      },
    });
  }
}
