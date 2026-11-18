import type { ReactNode } from 'react';
import { Button, ErrorNote, Spinner } from './ui';

interface QueryLike<T> {
  data?: T; isLoading: boolean; isError: boolean; error: unknown; refetch: () => unknown;
}

/** Renders loading, error (with retry) or the data. */
export function Async<T>({ query, children, skeleton }: { query: QueryLike<T>; children: (data: T) => ReactNode; skeleton?: ReactNode }) {
  if (query.isLoading) return <>{skeleton ?? <Spinner />}</>;
  if (query.isError) {
    return (
      <div className="space-y-3">
        <ErrorNote error={query.error} />
        <Button variant="secondary" size="sm" onClick={() => { void query.refetch(); }}>Try again</Button>
      </div>
    );
  }
  if (query.data === undefined) return null;
  return <>{children(query.data)}</>;
}
