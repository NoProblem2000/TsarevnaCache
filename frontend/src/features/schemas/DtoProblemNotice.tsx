import { FileCode2, PenLine } from 'lucide-react';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { configureSchemaRoute, dtoLabel, editSchemaRoute, type DtoProblem } from './schemaLinks';

/**
 * One sentence explaining a dynamic DTO problem precisely, with the matching action:
 * - nested DTO missing: names the registered root and the missing nested class, and configures the
 *   nested class (not the root);
 * - top-level DTO missing: configures that class;
 * - field layout error: names the class and field, and edits that existing schema.
 */
export function DtoProblemNotice({ problem, compact = false }: { problem: DtoProblem; compact?: boolean }) {
  const { t } = useTranslation();
  const code = <code className="gk-mono text-gk-cyan" />;
  const strong = <strong className="gk-mono text-gk-warning" />;
  let message;
  let action;
  if (problem.kind === 'missing') {
    const missing = dtoLabel(problem.target);
    message =
      problem.nested && problem.root ? (
        <Trans i18nKey="dtoProblem.nestedMissing" values={{ root: dtoLabel(problem.root), missing }} components={{ code, strong }} />
      ) : (
        <Trans i18nKey="dtoProblem.rootMissing" values={{ missing }} components={{ strong }} />
      );
    action = (
      <Link to={configureSchemaRoute(problem.target)} className="gk-btn-primary shrink-0 px-3 py-1.5 text-xs">
        <FileCode2 className="h-3.5 w-3.5" aria-hidden />
        {t('dtoProblem.configureMissing', { classId: problem.target.classId })}
      </Link>
    );
  } else {
    message = (
      <Trans
        i18nKey="dtoProblem.fieldError"
        values={{ schema: dtoLabel(problem.schema), order: problem.fieldOrder, field: problem.fieldName, type: problem.fieldType }}
        components={{ code, strong }}
      />
    );
    action = (
      <Link to={editSchemaRoute(problem.schema.factoryId, problem.schema.classId)} className="gk-btn-primary shrink-0 px-3 py-1.5 text-xs">
        <PenLine className="h-3.5 w-3.5" aria-hidden />
        {t('dtoProblem.editSchema', { classId: problem.schema.classId })}
      </Link>
    );
  }
  return (
    <div className={`flex flex-wrap items-center gap-3 rounded-md border border-gk-cyan/30 bg-gk-cyan/5 ${compact ? 'px-3 py-2' : 'p-3'}`}>
      <div className="min-w-0 flex-1 text-xs text-gk-text">
        <p>{message}</p>
        {!compact && problem.kind === 'field' && problem.fieldError && (
          <p className="gk-mono mt-1 break-words text-gk-muted">{problem.fieldError}</p>
        )}
        {!compact && problem.fieldPath && (
          <p className="mt-1 text-gk-subtle">
            {t('dtoProblem.path')} <span className="gk-mono">{problem.fieldPath}</span>
          </p>
        )}
      </div>
      {action}
    </div>
  );
}
