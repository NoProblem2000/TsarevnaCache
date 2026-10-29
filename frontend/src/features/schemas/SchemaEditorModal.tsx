import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { AlertTriangle, ArrowDown, ArrowUp, Braces, Info, Plus, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import { DTO_FIELD_TYPES, type DtoFieldType, type DtoRegistryView } from '../../api/types';
import { JsonEditor } from '../../components/editor/JsonEditor';
import { toEditorText } from '../../components/editor/jsonText';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/Toast';
import {
  FIELD_TYPE_INFO,
  emptyField,
  fromForm,
  generateTemplate,
  knownClassNames,
  moveField,
  normalizeName,
  validateForm,
  type EditableField,
  type FormProblem,
  type SchemaForm,
} from './dtoSchemaModel';
import { useInvalidateSchemaData } from './schemaLinks';

const NULLABLE_TYPES = DTO_FIELD_TYPES.filter((type) => type.startsWith('NULLABLE_'));
const RAW_TYPES = DTO_FIELD_TYPES.filter((type) => type.startsWith('RAW_'));

interface SchemaEditorModalProps {
  open: boolean;
  /** Initial state (new, parsed from Java or an existing schema). */
  initial: SchemaForm;
  /** Key of the edited schema; it is removed when the ids change. */
  original?: { factoryId: number; classId: number };
  /** Parser warnings shown above the editor. */
  warnings?: string[];
  registry: DtoRegistryView | undefined;
  /** Class id expected by the opened target when the parser could not resolve one: offered, not filled in. */
  suggestedClassId?: number;
  onClose: () => void;
}

/**
 * Visual schema constructor: factory id, class id (or a factory constant), class name and the
 * ordered field table. The row order is the byte order of `readData`.
 */
export function SchemaEditorModal({
  open,
  initial,
  original,
  warnings = [],
  registry,
  suggestedClassId,
  onClose,
}: SchemaEditorModalProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateSchemaData();
  const [form, setForm] = useState<SchemaForm>(initial);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [showPreview, setShowPreview] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(initial);
      setError(null);
      setShowPreview(false);
    }
  }, [open, initial]);

  const problems = useMemo(() => validateForm(form), [form]);
  const fieldProblem = (index: number) => problems.find((problem) => 'index' in problem && problem.index === index);
  const has = (kind: FormProblem['kind']) => problems.some((problem) => problem.kind === kind);
  const classNames = knownClassNames(registry);
  const constants = Object.entries(registry?.factoryConstants ?? {}).sort((a, b) => a[1] - b[1]);
  const projectFactory = registry === undefined || form.factoryId.trim() === String(registry.factoryId);
  // another schema stored under the same factoryId:classId would be replaced by saving
  const occupied =
    !has('factoryId') && !has('classId')
      ? registry?.schemas.find(
          (schema) =>
            Number(schema.factoryId) === Number(form.factoryId.trim()) &&
            Number(schema.classId) === Number(form.classId.trim()) &&
            !(original && Number(original.factoryId) === Number(schema.factoryId) && Number(original.classId) === Number(schema.classId)),
        )
      : undefined;
  const preview = useMemo(
    () => (showPreview && problems.length === 0 ? toEditorText(generateTemplate(fromForm(form, registry), registry)) : ''),
    [showPreview, problems.length, form, registry],
  );

  const update = (patch: Partial<SchemaForm>) => setForm((current) => ({ ...current, ...patch }));
  const updateField = (index: number, patch: Partial<EditableField>) =>
    setForm((current) => ({
      ...current,
      fields: current.fields.map((field, position) => (position === index ? { ...field, ...patch } : field)),
    }));

  /** Picking a class name known to the factory also fills in its class id. */
  const changeClassName = (className: string) => {
    const known = projectFactory
      ? (registry?.factoryClasses[className] ??
        Object.entries(registry?.factoryClasses ?? {}).find(([name]) => normalizeName(name) === normalizeName(className))?.[1])
      : undefined;
    update(known !== undefined && form.classId.trim() === '' ? { className, classId: String(known) } : { className });
  };
  const pickConstant = (name: string) => {
    const id = registry?.factoryConstants[name];
    if (id === undefined) {
      return;
    }
    const className = Object.entries(registry?.factoryClasses ?? {}).find(([, classId]) => Number(classId) === Number(id))?.[0];
    update({ classId: String(id), className: form.className.trim() === '' && className ? className : form.className });
  };

  const save = async () => {
    if (problems.length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const schema = fromForm(form, registry);
      const result = await endpoints.schemas.save(schema);
      if (
        original &&
        (Number(original.factoryId) !== Number(result.schema.factoryId) || Number(original.classId) !== Number(result.schema.classId))
      ) {
        // the ids changed: the old entry would otherwise stay as a second schema
        await endpoints.schemas.remove(original.factoryId, original.classId).catch(() => undefined);
      }
      await invalidate();
      toast.success(
        result.restartRequired
          ? t('schemas.editor.savedRestart', { name: result.schema.className, factoryId: result.schema.factoryId })
          : t('schemas.editor.saved', { name: result.schema.className }),
      );
      if ((result.linkedSchemas?.length ?? 0) > 0) {
        toast.success(t('schemas.editor.linked', { name: result.schema.className, count: result.linkedSchemas?.length ?? 0 }));
      }
      for (const warning of result.warnings ?? []) {
        toast.error(warning);
      }
      onClose();
    } catch (failure) {
      setError(failure);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={saving}
      widthClass="max-w-5xl"
      title={
        <span className="flex items-center gap-2">
          <Braces className="h-4 w-4 text-gk-cyan" aria-hidden />
          {original ? t('schemas.editor.editTitle') : t('schemas.editor.newTitle')}
          {form.className.trim() && <span className="gk-mono text-gk-cyan">· {form.className.trim()}</span>}
        </span>
      }
      footer={
        <>
          <button type="button" className="gk-btn-ghost mr-auto" onClick={() => setShowPreview((value) => !value)} disabled={problems.length > 0}>
            <Braces className="h-4 w-4" aria-hidden />
            {showPreview ? t('schemas.editor.hidePreview') : t('schemas.editor.showPreview')}
          </button>
          <button type="button" className="gk-btn-secondary" onClick={onClose} disabled={saving}>
            {t('common.cancel')}
          </button>
          <button type="button" className="gk-btn-primary" onClick={() => void save()} disabled={saving || problems.length > 0}>
            {saving ? t('schemas.editor.saving') : occupied ? t('schemas.editor.replace') : t('schemas.editor.save')}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        {warnings.length > 0 && (
          <div className="rounded-lg border border-gk-warning/40 bg-gk-warning/5 p-3 text-xs">
            <p className="mb-1.5 flex items-center gap-1.5 font-medium text-gk-warning">
              <AlertTriangle className="h-3.5 w-3.5" aria-hidden />
              {t('schemas.editor.parserWarnings')}
            </p>
            <ul className="list-disc space-y-0.5 pl-5 text-gk-muted">
              {warnings.map((warning) => (
                <li key={warning} className="gk-mono break-words">
                  {warning}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="grid grid-cols-1 gap-3 md:grid-cols-[140px_minmax(0,1fr)_minmax(0,1.4fr)]">
          <label>
            <span className="gk-label">{t('schemas.factoryId')}</span>
            <input
              className={`gk-input gk-mono ${has('factoryId') ? 'border-gk-danger' : ''}`}
              value={form.factoryId}
              onChange={(event) => update({ factoryId: event.target.value })}
              inputMode="numeric"
            />
          </label>
          <div>
            <span className="gk-label">{t('schemas.classId')}</span>
            <div className="flex gap-2">
              <input
                className={`gk-input gk-mono w-24 shrink-0 ${has('classId') ? 'border-gk-danger' : ''}`}
                value={form.classId}
                onChange={(event) => update({ classId: event.target.value })}
                inputMode="numeric"
                aria-label={t('schemas.classId')}
              />
              <select
                className="gk-input gk-mono min-w-0"
                value=""
                onChange={(event) => pickConstant(event.target.value)}
                disabled={!projectFactory || constants.length === 0}
                aria-label={t('schemas.editor.pickConstant')}
                title={constants.length === 0 ? t('schemas.editor.noConstants') : undefined}
              >
                <option value="">{constants.length === 0 ? t('schemas.editor.noConstants') : t('schemas.editor.pickConstant')}</option>
                {constants.map(([name, id]) => (
                  <option key={name} value={name}>
                    {name} = {id}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <label>
            <span className="gk-label">{t('schemas.className')}</span>
            <input
              className={`gk-input gk-mono ${has('className') ? 'border-gk-danger' : ''}`}
              value={form.className}
              onChange={(event) => changeClassName(event.target.value)}
              list="dto-schema-class-names"
              placeholder="SomeDTO"
            />
            <datalist id="dto-schema-class-names">
              {Object.keys(registry?.factoryClasses ?? {}).map((name) => (
                <option key={name} value={name} />
              ))}
            </datalist>
          </label>
        </div>
        {suggestedClassId !== undefined && form.classId.trim() === '' && (
          <div className="flex flex-wrap items-center gap-2 rounded-md border border-gk-cyan/30 bg-gk-cyan/5 px-3 py-2 text-xs">
            <Info className="h-3.5 w-3.5 shrink-0 text-gk-cyan" aria-hidden />
            <span className="min-w-0 flex-1 text-gk-text">{t('schemas.editor.classIdUnresolved', { classId: suggestedClassId })}</span>
            <button type="button" className="gk-btn-secondary px-2 py-1 text-xs" onClick={() => update({ classId: String(suggestedClassId) })}>
              {t('schemas.editor.useSuggestedClassId', { classId: suggestedClassId })}
            </button>
          </div>
        )}
        {occupied && (
          <p className="flex items-center gap-1.5 text-xs text-gk-warning">
            <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden />
            {t('schemas.editor.occupied', { name: occupied.className, factoryId: occupied.factoryId, classId: occupied.classId })}
          </p>
        )}
        {(has('factoryId') || has('classId') || has('className')) && (
          <p className="text-xs text-gk-danger">{t('schemas.editor.idsInvalid')}</p>
        )}

        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-gk-muted">{t('schemas.editor.fields')}</span>
            <span className="gk-badge bg-gk-purple/15 text-gk-purple">{form.fields.length}</span>
            <span className="flex items-center gap-1 text-[11px] text-gk-subtle">
              <Info className="h-3 w-3" aria-hidden />
              {t('schemas.editor.orderHint')}
            </span>
          </div>
          <div className="overflow-x-auto rounded-lg border border-gk-border">
            <table className="w-full min-w-[760px] text-left text-xs">
              <thead className="bg-gk-bg text-gk-muted">
                <tr>
                  <th className="w-12 px-2 py-2 font-medium">#</th>
                  <th className="px-2 py-2 font-medium">{t('schemas.editor.fieldName')}</th>
                  <th className="w-64 px-2 py-2 font-medium">{t('schemas.editor.serializationType')}</th>
                  <th className="w-52 px-2 py-2 font-medium">{t('schemas.editor.nestedClass')}</th>
                  <th className="w-28 px-2 py-2 text-right font-medium">
                    <span className="sr-only">{t('common.actions')}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {form.fields.map((field, index) => {
                  const info = FIELD_TYPE_INFO[field.fieldType];
                  const problem = fieldProblem(index);
                  return (
                    <tr key={field.id} className="border-t border-gk-border align-top hover:bg-gk-card/60">
                      <td className="gk-mono px-2 py-2 text-gk-subtle">{index}</td>
                      <td className="px-2 py-1.5">
                        <input
                          className={`gk-input gk-mono py-1.5 ${problem ? 'border-gk-danger' : ''}`}
                          value={field.fieldName}
                          onChange={(event) => updateField(index, { fieldName: event.target.value })}
                          placeholder="fieldName"
                          aria-label={t('schemas.editor.fieldName')}
                        />
                        {problem && (
                          <span className="mt-0.5 block text-[11px] text-gk-danger">
                            {problem.kind === 'duplicate' ? t('schemas.editor.duplicateField', { name: problem.name }) : t('schemas.editor.invalidField')}
                          </span>
                        )}
                      </td>
                      <td className="px-2 py-1.5">
                        <select
                          className="gk-input gk-mono py-1.5"
                          value={field.fieldType}
                          onChange={(event) => updateField(index, { fieldType: event.target.value as DtoFieldType })}
                          aria-label={t('schemas.editor.serializationType')}
                        >
                          <optgroup label={t('schemas.editor.helperTypes')}>
                            {NULLABLE_TYPES.map((type) => (
                              <option key={type} value={type}>
                                {FIELD_TYPE_INFO[type].label} · {FIELD_TYPE_INFO[type].javaType}
                              </option>
                            ))}
                          </optgroup>
                          <optgroup label={t('schemas.editor.rawTypes')}>
                            {RAW_TYPES.map((type) => (
                              <option key={type} value={type}>
                                {FIELD_TYPE_INFO[type].label} · {FIELD_TYPE_INFO[type].readCall}
                              </option>
                            ))}
                          </optgroup>
                        </select>
                        <span className="gk-mono mt-0.5 block truncate text-[11px] text-gk-subtle" title={info.readCall}>
                          {info.readCall}
                        </span>
                      </td>
                      <td className="px-2 py-1.5">
                        <select
                          className="gk-input gk-mono py-1.5 disabled:opacity-40"
                          value={info.nestable ? field.nestedClassName : ''}
                          onChange={(event) => updateField(index, { nestedClassName: event.target.value })}
                          disabled={!info.nestable}
                          aria-label={t('schemas.editor.nestedClass')}
                          title={info.nestable ? t('schemas.editor.nestedHint') : t('schemas.editor.nestedDisabled')}
                        >
                          <option value="">{info.nestable ? t('schemas.editor.noNested') : '—'}</option>
                          {field.nestedClassName && !classNames.includes(field.nestedClassName) && (
                            <option value={field.nestedClassName}>{field.nestedClassName}</option>
                          )}
                          {classNames.map((name) => (
                            <option key={name} value={name}>
                              {name}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="px-2 py-1.5">
                        <div className="flex justify-end gap-0.5">
                          <IconButton label={t('schemas.editor.moveUp')} disabled={index === 0} onClick={() => update({ fields: moveField(form.fields, index, -1) })}>
                            <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                          </IconButton>
                          <IconButton
                            label={t('schemas.editor.moveDown')}
                            disabled={index === form.fields.length - 1}
                            onClick={() => update({ fields: moveField(form.fields, index, 1) })}
                          >
                            <ArrowDown className="h-3.5 w-3.5" aria-hidden />
                          </IconButton>
                          <IconButton
                            label={t('schemas.editor.removeField')}
                            danger
                            onClick={() => update({ fields: form.fields.filter((_, position) => position !== index) })}
                          >
                            <Trash2 className="h-3.5 w-3.5" aria-hidden />
                          </IconButton>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {form.fields.length === 0 && (
                  <tr className="border-t border-gk-border">
                    <td colSpan={5} className="px-3 py-6 text-center text-gk-muted">
                      {t('schemas.editor.noFields')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <button type="button" className="gk-btn-secondary mt-2" onClick={() => update({ fields: [...form.fields, emptyField()] })}>
            <Plus className="h-4 w-4" aria-hidden />
            {t('schemas.editor.addField')}
          </button>
        </div>

        {showPreview && preview && (
          <div>
            <span className="gk-label">{t('schemas.editor.previewTitle')}</span>
            <JsonEditor value={preview} readOnly minHeight="80px" />
          </div>
        )}

        {error !== null && <ErrorMessage error={error} />}
      </div>
    </Modal>
  );
}

function IconButton({
  label,
  onClick,
  disabled = false,
  danger = false,
  children,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  danger?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      className={`rounded p-1.5 text-gk-muted transition-colors disabled:cursor-not-allowed disabled:opacity-30 ${
        danger ? 'hover:bg-gk-danger/15 hover:text-gk-danger' : 'hover:bg-gk-hover hover:text-gk-cyan'
      }`}
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
    >
      {children}
    </button>
  );
}
