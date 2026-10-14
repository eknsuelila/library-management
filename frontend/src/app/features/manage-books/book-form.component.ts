import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { BookService } from '../../core/api/book.service';
import { BookRequest } from '../../core/models';
import { errorMessage } from '../../shared/error-message';

/**
 * Add/edit a book (librarians). One component serves both "/books/new" and "/books/:id/edit": if the router
 * supplies an `id` input we are editing, otherwise creating.
 *
 * Optimistic locking in the UI: when editing, the book's `version` is kept and sent back with the update. If
 * someone changed the book meanwhile (another librarian, or a member borrowing a copy), the server rejects the
 * save with 409 and we tell the user to reload, instead of silently overwriting the newer data.
 */
@Component({
  selector: 'app-book-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './book-form.component.html',
})
export class BookFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BookService);
  private readonly router = inject(Router);

  /** Filled from the ":id" route parameter (withComponentInputBinding in app.config.ts). */
  @Input() id?: string;

  /** Copies currently on loan (edit mode). Total copies may not go below this. */
  readonly onLoan = signal(0);
  private version: number | null = null;

  // Validators mirror the backend's BookRequest constraints.
  readonly form = this.fb.group({
    isbn: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^(\d{9}[\dX]|\d{13})$/)]),
    title: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(200)]),
    author: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(150)]),
    genre: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(50)]),
    publishedYear: this.fb.control<number | null>(null, [Validators.min(1450), Validators.max(2100)]),
    totalCopies: this.fb.nonNullable.control(1, [Validators.required, Validators.min(0), Validators.max(1000)]),
    coverUrl: this.fb.nonNullable.control('', [Validators.maxLength(500), Validators.pattern(/^(https?:\/\/.*)?$/)]),
    description: this.fb.nonNullable.control('', Validators.maxLength(2000)),
  });

  readonly genres = signal<string[]>([]);
  readonly saving = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  get isEdit(): boolean {
    return !!this.id;
  }

  ngOnInit(): void {
    // Suggestions for the genre field (<datalist>); librarians can still type a new genre.
    this.api.genres().subscribe({ next: (g) => this.genres.set(g) });
    if (this.id) {
      this.loading.set(true);
      this.api.get(Number(this.id)).subscribe({
        next: (book) => {
          // patchValue copies matching keys from the API object into the form controls.
          this.form.patchValue({ ...book, coverUrl: book.coverUrl ?? '', description: book.description ?? '' });
          this.version = book.version;
          this.onLoan.set(book.totalCopies - book.availableCopies);
          // Client-side version of the "can't go below copies on loan" rule.
          this.form.controls.totalCopies.addValidators(Validators.min(this.onLoan()));
          this.form.controls.totalCopies.updateValueAndValidity();
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err, 'Could not load this book'));
          this.loading.set(false);
        },
      });
    }
  }

  /** True when a control should display its error message (invalid and touched). */
  showError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  submit(): void {
    // ISBNs are often written with hyphens/spaces ("978-0-441-17271-9"); strip them before validating.
    const isbn = this.form.controls.isbn;
    isbn.setValue(isbn.value.replace(/[\s-]/g, '').toUpperCase());

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);

    const body: BookRequest = { ...this.form.getRawValue(), version: this.version };
    const request$ = this.id ? this.api.update(Number(this.id), body) : this.api.create(body);
    request$.subscribe({
      next: () => this.router.navigate(['/books']),
      error: (err) => {
        this.error.set(errorMessage(err, 'Could not save'));
        this.saving.set(false);
      },
    });
  }
}
