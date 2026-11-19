import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { api } from '../../api/endpoints';
import type { AdminUser, Role } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Async } from '../../components/Async';
import { ConfirmModal } from '../../components/ConfirmModal';
import { Badge, Button, Card, EmptyState, ErrorNote, Modal, Notice, SelectField, Skeleton } from '../../components/ui';
import { fmtDate, humanize } from '../../lib/format';
import { useDebounced } from '../../lib/hooks';

const ROLES: Role[] = ['ADMINISTRATOR', 'ANALYST', 'INVESTOR'];
const ROLE_HELP: Record<Role, string> = {
  ADMINISTRATOR: 'Manage users and see system activity',
  ANALYST: 'Can add assets to the shared catalogue',
  INVESTOR: 'Standard access to their own portfolios',
};

function UserEditor({ user, isSelf, onChanged, onClose }: { user: AdminUser; isSelf: boolean; onChanged: (u: AdminUser) => void; onClose: () => void }) {
  const qc = useQueryClient();
  const [roles, setRoles] = useState<Role[]>(user.roles);
  const [confirmToggle, setConfirmToggle] = useState(false);
  const refresh = async (u: AdminUser) => { onChanged(u); await qc.invalidateQueries({ queryKey: ['admin-users'] }); await qc.invalidateQueries({ queryKey: ['admin-stats'] }); };
  const saveRoles = useMutation({ mutationFn: () => api.admin.setRoles(user.id, roles), onSuccess: refresh });
  const toggle = useMutation({ mutationFn: () => api.admin.setEnabled(user.id, !user.enabled), onSuccess: async (u) => { setConfirmToggle(false); await refresh(u); } });
  const changed = roles.length !== user.roles.length || roles.some((r) => !user.roles.includes(r));
  const flip = (r: Role) => setRoles(roles.includes(r) ? roles.filter((x) => x !== r) : [...roles, r]);

  return (
    <div className="space-y-5">
      <div>
        <div className="text-lg font-semibold text-white">{user.firstName} {user.lastName}</div>
        <div className="text-sm text-ink-300">{user.email}</div>
        <div className="mt-2 flex flex-wrap gap-2">
          <Badge tone={user.enabled ? 'gain' : 'loss'}>{user.enabled ? 'Enabled' : 'Disabled'}</Badge>
          <Badge tone={user.emailVerified ? 'gain' : 'warn'}>{user.emailVerified ? 'Email verified' : 'Email not verified'}</Badge>
          <Badge>{user.portfolioCount} portfolio{user.portfolioCount === 1 ? '' : 's'}</Badge>
        </div>
        <div className="mt-1 text-xs text-ink-400">Joined {fmtDate(user.createdAt)}</div>
      </div>
      {isSelf && <Notice>This is your own account. You cannot remove your administrator role or disable yourself.</Notice>}

      <fieldset>
        <legend className="label">Roles</legend>
        <ul className="space-y-2">
          {ROLES.map((r) => (
            <li key={r}>
              <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-ink-700 bg-ink-900/60 p-3">
                <input type="checkbox" className="mt-1 h-4 w-4 accent-emerald-400" checked={roles.includes(r)} onChange={() => flip(r)} />
                <span><span className="block text-sm font-medium text-white">{humanize(r)}</span><span className="block text-xs text-ink-400">{ROLE_HELP[r]}</span></span>
              </label>
            </li>
          ))}
        </ul>
        {roles.length === 0 && <p className="mt-2 text-xs text-red-300">Choose at least one role.</p>}
      </fieldset>
      <ErrorNote error={saveRoles.error} />
      <div className="flex flex-wrap justify-between gap-2">
        <Button variant={user.enabled ? 'danger' : 'secondary'} disabled={isSelf && user.enabled} onClick={() => setConfirmToggle(true)}>{user.enabled ? 'Disable account' : 'Enable account'}</Button>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button loading={saveRoles.isPending} disabled={!changed || roles.length === 0} onClick={() => saveRoles.mutate()}>Save roles</Button>
        </div>
      </div>
      <ConfirmModal open={confirmToggle} danger={user.enabled} title={user.enabled ? 'Disable this account?' : 'Enable this account?'} confirmLabel={user.enabled ? 'Disable' : 'Enable'}
        busy={toggle.isPending} error={toggle.error}
        message={user.enabled ? `${user.email} will be signed out on their next request and will not be able to sign in until the account is enabled again.` : `${user.email} will be able to sign in again.`}
        onConfirm={() => toggle.mutate()} onClose={() => { setConfirmToggle(false); toggle.reset(); }} />
    </div>
  );
}

export function AdminUsersPage() {
  const { user: me } = useAuth();
  const [text, setText] = useState('');
  const q = useDebounced(text.trim(), 300);
  const [role, setRole] = useState('');
  const [state, setState] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AdminUser | null>(null);

  const list = useQuery({
    queryKey: ['admin-users', q, role, state, page],
    queryFn: () => api.admin.users({ q: q || undefined, role: role || undefined, enabled: state === '' ? undefined : state === 'enabled', page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <div className="relative sm:col-span-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" aria-hidden />
          <input className="field pl-10" placeholder="Search name or email" aria-label="Search users" value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} />
        </div>
        <SelectField label="Role" aria-label="Filter by role" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
          <option value="">All roles</option>{ROLES.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
        </SelectField>
        <SelectField label="Status" aria-label="Filter by status" value={state} onChange={(e) => { setState(e.target.value); setPage(0); }}>
          <option value="">Any status</option><option value="enabled">Enabled</option><option value="disabled">Disabled</option>
        </SelectField>
      </div>
      <Async query={list} skeleton={<Skeleton className="h-64" />}>
        {(p) => p.content.length === 0 ? <Card><EmptyState title="No users found" text="Try a different search or filter." /></Card> : (
          <Card className="p-0 sm:p-0">
            <ul className="divide-y divide-ink-700/60">
              {p.content.map((u) => (
                <li key={u.id}>
                  <button type="button" onClick={() => setSelected(u)} className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left hover:bg-ink-800/60 sm:px-5">
                    <div className="min-w-0">
                      <div className="truncate font-medium text-white">{u.firstName} {u.lastName}{u.id === me?.id && <span className="ml-2 text-xs text-ink-400">(you)</span>}</div>
                      <div className="truncate text-sm text-ink-400">{u.email}</div>
                    </div>
                    <div className="flex shrink-0 flex-wrap items-center justify-end gap-1.5">
                      {u.roles.map((r) => <Badge key={r} tone="info">{humanize(r)}</Badge>)}
                      {!u.enabled && <Badge tone="loss">Disabled</Badge>}
                      {!u.emailVerified && <Badge tone="warn">Unverified</Badge>}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
            {p.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-ink-700/60 px-4 py-3 text-sm text-ink-300">
                <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
                <span>Page {p.page + 1} of {p.totalPages} · {p.totalElements} users</span>
                <Button variant="ghost" size="sm" disabled={page + 1 >= p.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
              </div>
            )}
          </Card>
        )}
      </Async>
      <Modal open={selected !== null} onClose={() => setSelected(null)} title="Manage user">
        {selected && <UserEditor key={selected.id} user={selected} isSelf={selected.id === me?.id} onChanged={setSelected} onClose={() => setSelected(null)} />}
      </Modal>
    </>
  );
}
