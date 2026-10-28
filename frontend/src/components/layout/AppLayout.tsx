import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';

/** Main application frame: top bar, sidebar and the routed workspace. */
export function AppLayout() {
  return (
    <div className="flex h-full flex-col">
      <TopBar />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <main className="flex min-w-0 flex-1 flex-col overflow-y-auto bg-gk-bg">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
