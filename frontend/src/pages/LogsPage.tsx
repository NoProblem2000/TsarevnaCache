import { useState } from 'react';
import { FileText, HardDriveUpload, Radio, ScrollText } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { Tabs } from '../components/ui/Tabs';
import { LiveStreamTab } from '../features/logs/LiveStreamTab';
import { LocalFileTab } from '../features/logs/LocalFileTab';
import { ServerLogsTab } from '../features/logs/ServerLogsTab';

type LogsTab = 'files' | 'live' | 'local';

const WRAP_KEY = 'tsarevna.logs.wrap';

function readWrap(): boolean {
  try {
    return window.localStorage.getItem(WRAP_KEY) === 'true';
  } catch {
    return false;
  }
}

function writeWrap(value: boolean): void {
  try {
    window.localStorage.setItem(WRAP_KEY, String(value));
  } catch {
    // storage may be unavailable (private mode)
  }
}

/**
 * Hazelcast Log Viewer: server side log files, the live stream of cluster events and log events,
 * and a local log file opened in the browser. The tab is kept in the URL; every tab stays mounted
 * so switching keeps its state (the live stream keeps collecting).
 */
export function LogsPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const tab: LogsTab = requested === 'live' || requested === 'local' ? requested : 'files';
  const [wrap, setWrap] = useState(readWrap);
  const changeWrap = (value: boolean) => {
    setWrap(value);
    writeWrap(value);
  };

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 border-b border-gk-border bg-gk-panel px-6 py-4">
        <ScrollText className="h-5 w-5 text-gk-cyan" aria-hidden />
        <div>
          <h1 className="text-lg font-semibold text-gk-text">{t('logs.title')}</h1>
          <p className="text-xs text-gk-muted">{t('logs.subtitle')}</p>
        </div>
      </header>
      <Tabs<LogsTab>
        active={tab}
        onChange={(next) => setSearchParams(next === 'files' ? {} : { tab: next }, { replace: true })}
        tabs={[
          { id: 'files', label: t('logs.tabs.files'), icon: FileText },
          { id: 'live', label: t('logs.tabs.live'), icon: Radio },
          { id: 'local', label: t('logs.tabs.local'), icon: HardDriveUpload },
        ]}
      />
      <div className={tab === 'files' ? 'flex min-h-0 flex-1 flex-col' : 'hidden'}>
        <ServerLogsTab wrap={wrap} onWrapChange={changeWrap} />
      </div>
      <div className={tab === 'live' ? 'flex min-h-0 flex-1 flex-col' : 'hidden'}>
        <LiveStreamTab wrap={wrap} onWrapChange={changeWrap} />
      </div>
      <div className={tab === 'local' ? 'flex min-h-0 flex-1 flex-col' : 'hidden'}>
        <LocalFileTab wrap={wrap} onWrapChange={changeWrap} />
      </div>
    </div>
  );
}
