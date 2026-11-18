import { Link, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { AppShell } from './components/layout/AppShell';
import { Button, EmptyState } from './components/ui';
import { AlertsPage, NotificationsPage } from './pages/AlertsNotificationsPages';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from './pages/AuthPages';
import { DashboardPage } from './pages/DashboardPage';
import { AssetDetailPage, MarketPage } from './pages/MarketPages';
import { PortfolioDetailPage } from './pages/PortfolioDetailPage';
import { PortfoliosPage } from './pages/PortfoliosPage';
import { SettingsPage } from './pages/SettingsPage';
import { WatchlistsPage } from './pages/WatchlistsPage';

function NotFound() {
  return <EmptyState title="Page not found" text="That page does not exist." action={<Link to="/"><Button>Go to dashboard</Button></Link>} />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="portfolios" element={<PortfoliosPage />} />
          <Route path="portfolios/:id" element={<PortfolioDetailPage />} />
          <Route path="market" element={<MarketPage />} />
          <Route path="market/:assetId" element={<AssetDetailPage />} />
          <Route path="watchlists" element={<WatchlistsPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}
