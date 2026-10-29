import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { AppLayout } from './components/layout/AppLayout';
import { CenteredSpinner } from './components/ui/Spinner';
import { DashboardPage } from './pages/DashboardPage';
import { HealthcheckPage } from './pages/HealthcheckPage';
import { LoginPage } from './pages/LoginPage';
import { MapPage } from './pages/MapPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { SchemasPage } from './pages/SchemasPage';
import { StructurePage } from './pages/StructurePage';

/** Routes; everything except the login screen requires a session. */
export function App() {
  const { status } = useAuth();

  if (status === 'checking') {
    return <CenteredSpinner />;
  }
  if (status === 'anonymous') {
    return <LoginPage />;
  }
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="maps/:mapName" element={<MapPage />} />
          <Route path="structures/:type/:name" element={<StructurePage />} />
          <Route path="schemas" element={<SchemasPage />} />
          <Route path="healthcheck" element={<HealthcheckPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
