import { Routes } from '@angular/router';
import { guestGuard, homeRedirectGuard, roleGuard } from './core/auth/auth.guard';

/**
 * URL -> component mapping, with access rules.
 *
 * - `loadComponent` LAZY-loads each page: its code is split into a separate JavaScript chunk that is downloaded
 *   only when the page is first visited. Members never download the librarian pages' code, and the initial
 *   load stays small.
 * - `canActivate: [roleGuard]` + `data: { roles: [...] }` restricts a page to certain roles. Without `roles`,
 *   roleGuard simply requires a logged-in user.
 *
 * Reminder: this only shapes the UI. The backend enforces the same rules on every API call.
 */
export const routes: Routes = [
  // "/" -> /dashboard (librarian), /catalog (member) or /login (anonymous). `children: []` because a route
  // needs a component, a redirect or children; the guard always redirects before anything renders.
  { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },

  // ---- Public pages (guestGuard bounces users who are already logged in) ----
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },

  // ---- Any logged-in user ----
  {
    path: 'catalog',
    canActivate: [roleGuard],
    loadComponent: () => import('./features/catalog/catalog.component').then((m) => m.CatalogComponent),
  },

  // ---- Members ----
  {
    path: 'my-loans',
    canActivate: [roleGuard],
    data: { roles: ['MEMBER'] },
    loadComponent: () => import('./features/my-loans/my-loans.component').then((m) => m.MyLoansComponent),
  },

  // ---- Librarians ----
  {
    path: 'dashboard',
    canActivate: [roleGuard],
    data: { roles: ['LIBRARIAN'] },
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'books',
    canActivate: [roleGuard],
    data: { roles: ['LIBRARIAN'] },
    loadComponent: () => import('./features/manage-books/book-list.component').then((m) => m.BookListComponent),
  },
  {
    path: 'books/new',
    canActivate: [roleGuard],
    data: { roles: ['LIBRARIAN'] },
    loadComponent: () => import('./features/manage-books/book-form.component').then((m) => m.BookFormComponent),
  },
  {
    // ":id" becomes the `id` @Input of BookFormComponent (withComponentInputBinding in app.config.ts).
    path: 'books/:id/edit',
    canActivate: [roleGuard],
    data: { roles: ['LIBRARIAN'] },
    loadComponent: () => import('./features/manage-books/book-form.component').then((m) => m.BookFormComponent),
  },
  {
    path: 'loans',
    canActivate: [roleGuard],
    data: { roles: ['LIBRARIAN'] },
    loadComponent: () => import('./features/loans/loan-admin.component').then((m) => m.LoanAdminComponent),
  },
  {
    path: 'members',
    canActivate: [roleGuard],
    data: { roles: ['LIBRARIAN'] },
    loadComponent: () => import('./features/members/member-list.component').then((m) => m.MemberListComponent),
  },

  // Unknown URLs go to the role's home page (via the "" route above).
  { path: '**', redirectTo: '' },
];
