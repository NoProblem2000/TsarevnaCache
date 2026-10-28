import { Compass } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
      <Compass className="h-10 w-10 text-gk-purple" aria-hidden />
      <h1 className="text-lg font-semibold">{t('notFound.title')}</h1>
      <p className="text-gk-muted">{t('notFound.text')}</p>
      <Link to="/" className="gk-btn-secondary">
        {t('notFound.back')}
      </Link>
    </div>
  );
}
