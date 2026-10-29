import { CornerDownRight, Download, PencilLine, RotateCcw, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { DtoClassSchema, DtoRegistryView } from '../../api/types';
import { FIELD_TYPE_INFO, nestedSchema } from './dtoSchemaModel';

interface SchemaCardProps {
  schema: DtoClassSchema;
  registry: DtoRegistryView;
  highlighted?: boolean;
  onEdit: () => void;
  onDelete: () => void;
  onExport: () => void;
}

/** Registered DTO: name, ids, the ordered field list with types and nested DTOs, actions. */
export function SchemaCard({ schema, registry, highlighted = false, onEdit, onDelete, onExport }: SchemaCardProps) {
  const { t } = useTranslation();
  const pending = !registry.activeFactoryIds.includes(schema.factoryId);
  const fields = [...schema.fields].sort((a, b) => a.order - b.order);
  return (
    <article
      id={`schema-${schema.factoryId}-${schema.classId}`}
      className={`gk-card flex flex-col transition-shadow ${highlighted ? 'shadow-glow' : 'hover:border-gk-cyan/40'}`}
    >
      <header className="flex flex-wrap items-start gap-2 border-b border-gk-border px-4 py-3">
        <div className="min-w-0 flex-1">
          <h3 className="gk-mono truncate text-base font-semibold text-gk-cyan" title={schema.className}>
            {schema.className}
          </h3>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            <span className="gk-badge gk-mono bg-gk-purple/15 text-gk-purple">
              {t('schemas.card.ids', { factoryId: schema.factoryId, classId: schema.classId })}
            </span>
            <span className="gk-badge bg-gk-bg text-gk-muted">{t('schemas.card.fieldCount', { count: fields.length })}</span>
            {pending && (
              <span className="gk-badge gap-1 bg-gk-warning/15 text-gk-warning" title={t('schemas.card.pendingHint')}>
                <RotateCcw className="h-3 w-3" aria-hidden />
                {t('schemas.card.pending')}
              </span>
            )}
          </div>
        </div>
      </header>

      <ol className="min-h-0 flex-1 divide-y divide-gk-border/60 px-4 py-1 text-xs">
        {fields.map((field) => {
          const info = FIELD_TYPE_INFO[field.fieldType];
          const nested = info.nestable ? field.nestedClassName ?? nestedSchema(registry, schema.factoryId, field)?.className : undefined;
          const nestedKnown = nested ? nestedSchema(registry, schema.factoryId, field) !== undefined : true;
          return (
            <li key={`${field.order}:${field.fieldName}`} className="flex items-center gap-2 py-1.5">
              <span className="gk-mono w-6 shrink-0 text-right text-gk-subtle">{field.order}</span>
              <span className="gk-mono min-w-0 flex-1 truncate text-gk-text" title={field.fieldName}>
                {field.fieldName}
              </span>
              <span className="gk-mono shrink-0 text-gk-purple" title={info.readCall}>
                {info.label}
              </span>
              {nested && (
                <span
                  className={`gk-mono flex shrink-0 items-center gap-0.5 ${nestedKnown ? 'text-gk-green' : 'text-gk-warning'}`}
                  title={nestedKnown ? undefined : t('schemas.card.nestedMissing', { name: nested })}
                >
                  <CornerDownRight className="h-3 w-3" aria-hidden />
                  {nested}
                </span>
              )}
            </li>
          );
        })}
        {fields.length === 0 && <li className="py-3 text-gk-muted">{t('schemas.card.noFields')}</li>}
      </ol>

      <footer className="flex flex-wrap gap-1.5 border-t border-gk-border px-3 py-2">
        <button type="button" className="gk-btn-ghost px-2 py-1 text-xs" onClick={onEdit}>
          <PencilLine className="h-3.5 w-3.5" aria-hidden />
          {t('schemas.card.edit')}
        </button>
        <button type="button" className="gk-btn-ghost px-2 py-1 text-xs" onClick={onExport}>
          <Download className="h-3.5 w-3.5" aria-hidden />
          {t('schemas.card.export')}
        </button>
        <button type="button" className="gk-btn-ghost ml-auto px-2 py-1 text-xs hover:text-gk-danger" onClick={onDelete}>
          <Trash2 className="h-3.5 w-3.5" aria-hidden />
          {t('common.delete')}
        </button>
      </footer>
    </article>
  );
}
