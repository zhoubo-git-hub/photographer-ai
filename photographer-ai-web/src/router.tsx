import { ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import AppShell from './layout/AppShell';
import LoginPage from './pages/LoginPage';
import OrdersPage from './pages/OrdersPage';
import CustomersPage from './pages/CustomersPage';
import CalendarPage from './pages/CalendarPage';
import AiQuotePage from './pages/AiQuotePage';
import ContractPage from './pages/ContractPage';
import ReminderRulePage from './pages/ReminderRulePage';
import RepurchasePage from './pages/RepurchasePage';
import BillingPage from './pages/BillingPage';
import TeamPage from './pages/TeamPage';
import DashboardPage from './pages/DashboardPage';
import QuoteCalibrationPanel from './pages/QuoteCalibrationPanel';

function Protected({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <Protected>
        <AppShell />
      </Protected>
    ),
    children: [
      { index: true, element: <Navigate to="/orders" replace /> },
      { path: 'orders', element: <OrdersPage /> },
      { path: 'customers', element: <CustomersPage /> },
      { path: 'calendar', element: <CalendarPage /> },
      { path: 'ai-quote', element: <AiQuotePage /> },
      { path: 'contract', element: <ContractPage /> },
      { path: 'reminder-rules', element: <ReminderRulePage /> },
      { path: 'repurchases', element: <RepurchasePage /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'team', element: <TeamPage /> },
      { path: 'billing', element: <BillingPage /> },
      { path: 'quote-calibration', element: <QuoteCalibrationPanel /> },
    ],
  },
  { path: '*', element: <Navigate to="/orders" replace /> },
]);
