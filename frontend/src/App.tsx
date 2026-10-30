import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useParams } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { AppShell } from './components/layout/AppShell';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { PublicOnlyRoute } from './components/auth/PublicOnlyRoute';

// Eagerly loaded pages (smallest, always needed for routing)
import { LandingPage } from './pages/LandingPage';
import { AuthPage } from './pages/AuthPage';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { OnboardingPage } from './pages/OnboardingPage';

// Route-level lazy loading splits the bundle per page
const OverviewPage = lazy(() => import('./pages/OverviewPage').then(m => ({ default: m.OverviewPage })));
const CasesListPage = lazy(() => import('./pages/CasesListPage').then(m => ({ default: m.CasesListPage })));
const NewCasePage = lazy(() => import('./pages/NewCasePage').then(m => ({ default: m.NewCasePage })));
const CaseWorkspacePage = lazy(() => import('./pages/CaseWorkspacePage').then(m => ({ default: m.CaseWorkspacePage })));
const ReviewQueuePage = lazy(() => import('./pages/ReviewQueuePage').then(m => ({ default: m.ReviewQueuePage })));
const BenchmarksPage = lazy(() => import('./pages/BenchmarksPage').then(m => ({ default: m.BenchmarksPage })));
const ModelsPage = lazy(() => import('./pages/ModelsPage').then(m => ({ default: m.ModelsPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

// Helper component for backward-compatible case redirection
const CaseRedirect: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  return <Navigate to={`/app/cases/${id}`} replace />;
};

// Full-screen loading fallback for lazy routes
const PageLoader: React.FC = () => (
  <div className="flex items-center justify-center min-h-screen">
    <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500" />
  </div>
);

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <ToastProvider>
            <BrowserRouter>
              <Suspense fallback={<PageLoader />}>
                <Routes>
                  {/* Public landing page at root "/" */}
                  <Route path="/" element={<LandingPage />} />

                  {/* Public-only authentication routes */}
                  <Route element={<PublicOnlyRoute />}>
                    <Route path="/signin" element={<AuthPage initialMode="signin" />} />
                    <Route path="/signup" element={<AuthPage initialMode="signup" />} />
                  </Route>

                  {/* OAuth redirect callback */}
                  <Route path="/auth/callback" element={<AuthCallbackPage />} />

                  {/* Post-login onboarding */}
                  <Route path="/onboarding" element={<OnboardingPage />} />

                  {/* Protected app routes inside AppShell under /app/* */}
                  <Route element={<ProtectedRoute />}>
                    <Route path="/app" element={<AppShell />}>
                      <Route index element={<OverviewPage />} />
                      <Route path="cases/new" element={<NewCasePage />} />
                      <Route path="cases" element={<CasesListPage />} />
                      <Route path="cases/:id" element={<CaseWorkspacePage />} />
                      <Route path="review" element={<ReviewQueuePage />} />
                      <Route path="benchmarks" element={<BenchmarksPage />} />
                      <Route path="models" element={<ModelsPage />} />
                      <Route path="settings" element={<SettingsPage />} />
                      <Route path="*" element={<Navigate to="/app" replace />} />
                    </Route>
                  </Route>

                  {/* Backward compatibility redirects for legacy routes */}
                  <Route path="/cases/new" element={<Navigate to="/app/cases/new" replace />} />
                  <Route path="/cases/:id" element={<CaseRedirect />} />
                  <Route path="/cases" element={<Navigate to="/app/cases" replace />} />
                  <Route path="/review" element={<Navigate to="/app/review" replace />} />
                  <Route path="/benchmarks" element={<Navigate to="/app/benchmarks" replace />} />
                  <Route path="/models" element={<Navigate to="/app/models" replace />} />
                  <Route path="/settings" element={<Navigate to="/app/settings" replace />} />

                  {/* Fallback to landing */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
            </BrowserRouter>
          </ToastProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
};

export default App;
