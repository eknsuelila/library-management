import { Component, Input, signal } from '@angular/core';

/** A handful of pleasant background colors for books without a cover image. */
const PALETTE = ['#0f766e', '#7c3aed', '#b45309', '#be123c', '#1d4ed8', '#15803d', '#4338ca', '#a21caf'];

/**
 * Shows a book's cover image, or a colored placeholder with the title when there is no image or it fails to load
 * (e.g. offline, or Open Library has no cover for that ISBN).
 *
 * `@Input()` declares a property the PARENT sets in its template: <app-book-cover [title]="book.title" ...>.
 * The template is written inline (`template:`) because it is tiny; bigger components use separate .html files.
 */
@Component({
  selector: 'app-book-cover',
  standalone: true,
  template: `
    @if (url && !failed()) {
      <!-- (error) fires if the image can't be loaded; we then switch to the placeholder. -->
      <img [src]="url" [alt]="'Cover of ' + title" loading="lazy" (error)="failed.set(true)" />
    } @else {
      <div class="placeholder" [style.background]="color" aria-hidden="true">
        <span>{{ title }}</span>
      </div>
    }
  `,
  styles: `
    :host { display: block; aspect-ratio: 2 / 3; border-radius: 8px; overflow: hidden; background: var(--surface-alt); }
    img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .placeholder { width: 100%; height: 100%; display: grid; place-items: center; padding: 10px; color: #fff;
      font-weight: 700; text-align: center; font-size: 13px; line-height: 1.3; }
  `,
})
export class BookCoverComponent {
  @Input() url: string | null = null;
  @Input({ required: true }) title = '';

  /** Set to true by the <img> (error) event. A signal so the template updates immediately. */
  readonly failed = signal(false);

  /** Same title => same color every time (a simple string hash picks a palette entry). */
  get color(): string {
    let hash = 0;
    for (const ch of this.title) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    return PALETTE[Math.abs(hash) % PALETTE.length];
  }
}
