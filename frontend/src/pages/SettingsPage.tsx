import { useMutation, useQuery } from '@tanstack/react-query';
import { CheckCircle2, Download, LogOut, MailWarning, Share, Smartphone } from 'lucide-react';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Badge, Button, Card, ErrorNote, Notice, PageHeader, SectionTitle } from '../components/ui';
import { useInstall } from '../lib/install';
import { fmtDateTime, humanize } from '../lib/format';
import { sessionStore } from '../lib/session';

export function SettingsPage() {
  const { user, logout } = useAuth();
  const { installed, canInstall, showIosSteps, install } = useInstall();
  const status = useQuery({ queryKey: ['market-status'], queryFn: api.market.status, staleTime: 5 * 60_000 });
  const resend = useMutation({ mutationFn: () => api.auth.resendVerification() });
  const session = sessionStore.load();

  if (!user) return null;
  return (
    <>
      <PageHeader title="Settings" subtitle="Your account and this app" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <SectionTitle>Account</SectionTitle>
          <div className="text-lg font-semibold text-white">{user.firstName} {user.lastName}</div>
          <div className="text-sm text-ink-300">{user.email}</div>
          <div className="mt-3 flex flex-wrap gap-2">{user.roles.map((r) => <Badge key={r} tone="info">{humanize(r)}</Badge>)}</div>
          {session && <p className="mt-3 text-xs text-ink-400">Signed in until {fmtDateTime(new Date(session.expiresAt).toISOString())}</p>}
          <Button variant="secondary" className="mt-4" icon={<LogOut className="h-4 w-4" />} onClick={logout}>Sign out</Button>
        </Card>

        <Card>
          <SectionTitle>Email</SectionTitle>
          {user.emailVerified ? (
            <div className="flex items-center gap-2 text-gain"><CheckCircle2 className="h-5 w-5" aria-hidden /> Your email address is verified</div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-amber-300"><MailWarning className="h-5 w-5" aria-hidden /> Your email address is not verified yet</div>
              <ErrorNote error={resend.error} />
              {resend.isSuccess ? <Notice>We have sent a new verification link. Check your inbox.</Notice>
                : <Button variant="secondary" loading={resend.isPending} onClick={() => resend.mutate()}>Resend verification email</Button>}
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle>Install as an app</SectionTitle>
          {installed ? (
            <div className="flex items-center gap-2 text-gain"><CheckCircle2 className="h-5 w-5" aria-hidden /> You are using the installed app</div>
          ) : (
            <div className="space-y-3 text-sm text-ink-200">
              <p>PortfolioIQ installs on phones, tablets and computers. It opens in its own window and keeps working with a flaky connection (live data still needs the internet).</p>
              {canInstall && <Button icon={<Download className="h-4 w-4" />} onClick={() => { void install(); }}>Install PortfolioIQ</Button>}
              {showIosSteps && <p className="flex items-start gap-2 rounded-xl border border-ink-700 bg-ink-900/60 p-3"><Smartphone className="mt-0.5 h-4 w-4 shrink-0" aria-hidden /><span>On iPhone or iPad: tap <Share className="mx-0.5 inline h-4 w-4" aria-label="Share" /> in Safari, then choose “Add to Home Screen”.</span></p>}
              {!canInstall && !showIosSteps && <p className="text-ink-400">If your browser supports it, use its install option in the address bar or menu (Chrome and Edge show an install icon on computers; on Android choose “Install app”).</p>}
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle>Market data</SectionTitle>
          {status.data ? (
            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-2"><Badge tone={status.data.simulated ? 'warn' : 'gain'}>{status.data.provider}</Badge>{status.data.simulated && <span className="text-amber-200">Simulated</span>}</div>
              <p className="text-ink-300">{status.data.message}</p>
            </div>
          ) : <p className="text-sm text-ink-400">Checking…</p>}
          <a href="/swagger-ui.html" target="_blank" rel="noreferrer" className="mt-4 inline-block text-sm text-emerald-300 hover:underline">API documentation</a>
        </Card>
      </div>
      <p className="mt-6 text-center text-xs text-ink-500">PortfolioIQ 0.5.0 · Figures describe past performance only and are not investment advice.</p>
    </>
  );
}
