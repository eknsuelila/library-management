import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Member, Page } from '../models';
import { toParams } from './http-params';

export interface MemberQuery {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/** GET /api/members - librarian only. */
@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);

  list(query: MemberQuery = {}): Observable<Page<Member>> {
    return this.http.get<Page<Member>>('/api/members', { params: toParams(query) });
  }
}
