import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './i18n';
import './index.css';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import i18n from './i18n';
import { CrashScreen } from './components/layout';

const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000 } } });

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <CrashScreen message={i18n.t('errors.crashed')} action={i18n.t('errors.reload')}>
            <App />
          </CrashScreen>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
