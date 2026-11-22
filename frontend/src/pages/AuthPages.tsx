import { useEffect, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle } from 'lucide-react';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { safePath } from '../lib/hooks';
import { sessionStore } from '../lib/session';
import { isEmail, validatePassword } from '../lib/validation';
import { Button, ErrorNote, Notice, Spinner, TextField } from '../components/ui';

function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle?: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="pt-safe pb-safe grid min-h-full place-items-center px-4 py-10">
      <div className="w-full max-w-md animate-rise">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <img src="/favicon.svg" alt="" className="h-14 w-14 rounded-2xl shadow-lg shadow-emerald-500/10" />
          <div>
            <div className="text-2xl font-semibold tracking-tight text-white">Portfolio<span className="text-emerald-400">IQ</span></div>
            <div className="text-sm text-ink-300">Investment and portfolio management</div>
          </div>
        </div>
        <div className="card p-6">
          <h1 className="text-xl font-semibold text-white">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-ink-300">{subtitle}</p>}
          <div className="mt-5">{children}</div>
        </div>
        {footer && <div className="mt-5 text-center text-sm text-ink-300">{footer}</div>}
      </div>
    </div>
  );
}

export function LoginPage() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = safePath((location.state as { from?: string } | null)?.from);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (status === 'authenticated') navigate(from, { replace: true }); }, [status, from, navigate]);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try { await login(email, password); } catch (err) { setError(err); } finally { setBusy(false); }
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to your account"
      footer={<>New here? <Link to="/register" className="font-medium text-emerald-300 hover:underline">Create an account</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorNote error={error} />
        <TextField label="Email" type="email" autoComplete="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <TextField label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <Button type="submit" className="w-full" loading={busy} disabled={!email || !password}>Sign in</Button>
        <div className="text-center"><Link to="/forgot-password" className="text-sm text-ink-300 hover:text-white">Forgot your password?</Link></div>
      </form>
    </AuthLayout>
  );
}

export function RegisterPage() {
  const { register, status } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (status === 'authenticated') navigate('/', { replace: true }); }, [status, navigate]);
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!form.firstName.trim()) next.firstName = 'Required';
    if (!form.lastName.trim()) next.lastName = 'Required';
    if (!isEmail(form.email)) next.email = 'Enter a valid email address';
    const pw = validatePassword(form.password);
    if (pw) next.password = pw;
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    setError(null);
    setBusy(true);
    try { await register(form); } catch (err) { setError(err); } finally { setBusy(false); }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Track portfolios, markets and performance in one place"
      footer={<>Already have an account? <Link to="/login" className="font-medium text-emerald-300 hover:underline">Sign in</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorNote error={error} />
        <div className="grid grid-cols-2 gap-3">
          <TextField label="First name" autoComplete="given-name" value={form.firstName} onChange={set('firstName')} error={errors.firstName} />
          <TextField label="Last name" autoComplete="family-name" value={form.lastName} onChange={set('lastName')} error={errors.lastName} />
        </div>
        <TextField label="Email" type="email" autoComplete="email" inputMode="email" value={form.email} onChange={set('email')} error={errors.email} />
        <TextField label="Password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')}
          error={errors.password} hint="At least 10 characters" />
        <Button type="submit" className="w-full" loading={busy}>Create account</Button>
        <p className="text-center text-xs text-ink-400">We will email you a link to verify your address.</p>
      </form>
    </AuthLayout>
  );
}

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const { refreshUser } = useAuth();
  const [state, setState] = useState<'idle' | 'working' | 'done' | 'failed'>(token ? 'working' : 'idle');
  const [error, setError] = useState<unknown>(null);
  const started = useRef(false);

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true; // the token is single-use, so only ever send it once
    api.auth.verifyEmail(token)
      .then(async () => {
        setState('done');
        // Roles can change at verification (the first administrator is promoted now), so reload them if signed in.
        if (sessionStore.load()) await refreshUser().catch(() => undefined);
      })
      .catch((err) => { setError(err); setState('failed'); });
  }, [token, refreshUser]);

  return (
    <AuthLayout title="Verify your email">
      {state === 'idle' && <Notice>Open the verification link from your email on this device to confirm your address.</Notice>}
      {state === 'working' && <Spinner label="Verifying" />}
      {state === 'done' && (
        <div className="flex flex-col items-center gap-3 text-center">
          <CheckCircle2 className="h-10 w-10 text-gain" aria-hidden />
          <p className="text-ink-100">Your email address is verified.</p>
          <Link to="/"><Button>Continue</Button></Link>
        </div>
      )}
      {state === 'failed' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 text-loss"><XCircle className="h-5 w-5" aria-hidden /> Verification failed</div>
          <ErrorNote error={error} />
          <p className="text-sm text-ink-300">The link may have expired or already been used. Sign in and request a new one from Settings.</p>
          <Link to="/login"><Button variant="secondary">Go to sign in</Button></Link>
        </div>
      )}
    </AuthLayout>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try { await api.auth.forgotPassword(email.trim()); setSent(true); } catch (err) { setError(err); } finally { setBusy(false); }
  }

  return (
    <AuthLayout title="Reset your password" subtitle="Enter your email and we will send a reset link"
      footer={<Link to="/login" className="font-medium text-emerald-300 hover:underline">Back to sign in</Link>}>
      {sent ? (
        <Notice>If an account exists for that address, a reset link is on its way. It is valid for one hour.</Notice>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <ErrorNote error={error} />
          <TextField label="Email" type="email" autoComplete="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Button type="submit" className="w-full" loading={busy} disabled={!isEmail(email)}>Send reset link</Button>
        </form>
      )}
    </AuthLayout>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [fieldError, setFieldError] = useState<string>('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const problem = validatePassword(password) ?? (password !== confirm ? 'Passwords do not match' : null);
    setFieldError(problem ?? '');
    if (problem) return;
    setError(null);
    setBusy(true);
    try { await api.auth.resetPassword(token, password); setDone(true); } catch (err) { setError(err); } finally { setBusy(false); }
  }

  return (
    <AuthLayout title="Choose a new password">
      {!token ? (
        <Notice tone="warn">This page needs the link from your reset email. <Link to="/forgot-password" className="underline">Request a new one</Link>.</Notice>
      ) : done ? (
        <div className="space-y-4">
          <Notice>Your password has been changed. Sign in with the new password.</Notice>
          <Link to="/login"><Button className="w-full">Sign in</Button></Link>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <ErrorNote error={error} />
          <TextField label="New password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} hint="At least 10 characters" />
          <TextField label="Confirm password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={fieldError} />
          <Button type="submit" className="w-full" loading={busy}>Change password</Button>
        </form>
      )}
    </AuthLayout>
  );
}
