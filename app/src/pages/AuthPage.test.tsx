import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../i18n';
import i18n from '../i18n';
import { AuthProvider } from '../auth/AuthContext';
import { AuthPage } from './AuthPage';

function renderLogin() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <AuthProvider>
          <AuthPage mode="login" />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('AuthPage', () => {
  it('shows a translated error when the password is wrong', async () => {
    await i18n.changeLanguage('en');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 401, detail: 'bad' }), { status: 401 }),
    );
    renderLogin();
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'a@b.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrongpass' } });
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Email or password is wrong.'));
  });

  it('renders in Telugu when the app language is Telugu', async () => {
    await i18n.changeLanguage('te');
    renderLogin();
    expect(screen.getByLabelText('ఈమెయిల్')).toBeInTheDocument();
    await i18n.changeLanguage('en');
  });
});
