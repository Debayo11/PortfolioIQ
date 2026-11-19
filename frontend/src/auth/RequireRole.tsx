import { Link, Outlet } from 'react-router-dom';
import type { Role } from '../api/types';
import { Button, EmptyState } from '../components/ui';
import { useAuth } from './AuthContext';

/**
 * Hides screens the signed-in user is not allowed to use. This is only a courtesy: the server rejects every
 * administrator request from anyone else, whatever the browser shows.
 */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { hasRole } = useAuth();
  if (!hasRole(...roles)) {
    return <EmptyState title="Not authorised" text="You do not have access to this area." action={<Link to="/"><Button>Go to dashboard</Button></Link>} />;
  }
  return <Outlet />;
}
