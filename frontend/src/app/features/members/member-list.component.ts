import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { MemberService } from '../../core/api/member.service';
import { Member, Page } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination.component';
import { errorMessage } from '../../shared/error-message';

/**
 * Librarian list of members with their loan counters (computed by the backend with GROUP BY queries).
 * "View loans" opens the Loans page filtered to that member via query parameters.
 */
@Component({
  selector: 'app-member-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, PaginationComponent],
  templateUrl: './member-list.component.html',
})
export class MemberListComponent implements OnInit {
  private readonly api = inject(MemberService);
  private readonly destroyRef = inject(DestroyRef);

  readonly search = new FormControl('', { nonNullable: true });
  readonly pageIndex = signal(0);
  readonly page = signal<Page<Member> | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.goToPage(0));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.list({ q: this.search.value, page: this.pageIndex(), size: 20, sort: 'name,asc' }).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    this.pageIndex.set(index);
    this.load();
  }
}
