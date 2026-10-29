import { useState } from 'react';
import { FileCode2, Info, Wand2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { endpoints } from '../../api/endpoints';
import { useDtoSchemas } from '../../api/queries';
import { DATA_TYPES, KEY_TYPES, type DataType, type EntryDTO, type PutEntryRequest } from '../../api/types';
import { JsonEditor } from '../../components/editor/JsonEditor';
import { toEditorText } from '../../components/editor/jsonText';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/Toast';
import { generateTemplate, schemaLabel } from '../schemas/dtoSchemaModel';
import { useInvalidateMapData } from './useInvalidateMapData';
import { editorTextToValue, isJsonEdited, parseTtl } from './valueEditing';

/**
 * Value types offered when creating an entry (Compact needs an existing schema: edit it in the
 * inspector); registered DTOs come from the DTO Schema Studio.
 */
const VALUE_TYPES: DataType[] = [...DATA_TYPES.filter((type) => type !== 'COMPACT'), 'IDENTIFIED_DTO'];

const EMPTY_OBJECT = '{\n  \n}';
const isBlankJson = (text: string) => text.replace(/\s/g, '') === '' || text.replace(/\s/g, '') === '{}';

interface PutEntryModalProps {
  mapName: string;
  open: boolean;
  onClose: () => void;
  /** Called with the stored entry, e.g. to open it in the inspector. */
  onSaved?: (entry: EntryDTO) => void;
}

/** "Put Entry" dialog: key + key type, value + value type (JSON editor for JSON/class values), TTL. */
export function PutEntryModal({ mapName, open, onClose, onSaved }: PutEntryModalProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const invalidate = useInvalidateMapData(mapName);
  const [key, setKey] = useState('');
  const [keyType, setKeyType] = useState<DataType>('STRING');
  const [keyClassName, setKeyClassName] = useState('');
  const [valueType, setValueType] = useState<DataType>('JSON');
  const [valueClassName, setValueClassName] = useState('');
  const [valueText, setValueText] = useState('{\n  \n}');
  const [ttl, setTtl] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [dtoKey, setDtoKey] = useState('');
  const registry = useDtoSchemas();
  const dtoSchemas = [...(registry.data?.schemas ?? [])].sort(
    (a, b) => a.factoryId - b.factoryId || a.className.localeCompare(b.className),
  );
  const dtoSchema = dtoSchemas.find((schema) => `${schema.factoryId}/${schema.classId}` === dtoKey);

  const keyJson = isJsonEdited(keyType);
  const valueJson = isJsonEdited(valueType);
  const parsedValue = editorTextToValue(valueText, valueType);
  const parsedKey = editorTextToValue(key, keyType);
  const ttlParsed = parseTtl(ttl);
  const valid =
    key.length > 0 &&
    parsedKey.ok &&
    parsedValue.ok &&
    ttlParsed.ok &&
    (keyType !== 'CLASS' || keyClassName.trim().length > 0) &&
    (valueType !== 'CLASS' || valueClassName.trim().length > 0) &&
    (valueType !== 'IDENTIFIED_DTO' || dtoSchema !== undefined);

  const fillTemplate = (key: string) => {
    const schema = dtoSchemas.find((item) => `${item.factoryId}/${item.classId}` === key);
    if (schema) {
      setValueText(toEditorText(generateTemplate(schema, registry.data)));
    }
  };
  const changeDto = (key: string) => {
    setDtoKey(key);
    if (isBlankJson(valueText)) {
      fillTemplate(key);
    }
  };

  const reset = () => {
    setKey('');
    setValueText(valueJson ? EMPTY_OBJECT : '');
    setTtl('');
    setError(null);
  };
  const close = () => {
    reset();
    onClose();
  };

  const changeValueType = (next: DataType) => {
    const wasJson = valueJson;
    setValueType(next);
    if (next === 'IDENTIFIED_DTO' && dtoKey && (isBlankJson(valueText) || !wasJson)) {
      fillTemplate(dtoKey);
    } else if (isJsonEdited(next) !== wasJson) {
      setValueText(isJsonEdited(next) ? EMPTY_OBJECT : '');
    }
  };

  const submit = async () => {
    if (!valid || !parsedKey.ok || !parsedValue.ok || !ttlParsed.ok) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const request: PutEntryRequest = {
        key: parsedKey.value,
        keyType,
        keyClassName: keyType === 'CLASS' ? keyClassName.trim() : undefined,
        value: parsedValue.value,
        valueType,
        valueClassName:
          valueType === 'CLASS' ? valueClassName.trim() : valueType === 'IDENTIFIED_DTO' ? dtoSchema?.className : undefined,
        valueFactoryId: valueType === 'IDENTIFIED_DTO' ? dtoSchema?.factoryId : undefined,
        valueClassId: valueType === 'IDENTIFIED_DTO' ? dtoSchema?.classId : undefined,
        ttlSeconds: ttlParsed.value,
      };
      const saved = await endpoints.maps.put(mapName, request);
      toast.success(t('browser.saved'));
      await invalidate();
      close();
      onSaved?.(saved);
    } catch (failure) {
      setError(failure);
    } finally {
      setSaving(false);
    }
  };

  const hint =
    valueType === 'JSON'
      ? t('putModal.jsonHint')
      : valueType === 'CLASS'
        ? t('putModal.classHint')
        : valueType === 'IDENTIFIED_DTO' && dtoSchemas.length > 0
          ? t('putModal.dtoHint')
          : null;

  return (
    <Modal
      open={open}
      onClose={close}
      busy={saving}
      widthClass="max-w-2xl"
      title={
        <span>
          {t('putModal.title')} · <span className="gk-mono text-gk-cyan">{mapName}</span>
        </span>
      }
      footer={
        <>
          <button type="button" className="gk-btn-secondary" onClick={close} disabled={saving}>
            {t('common.cancel')}
          </button>
          <button type="button" className="gk-btn-primary" onClick={() => void submit()} disabled={!valid || saving}>
            {saving ? t('putModal.submitting') : t('putModal.submit')}
          </button>
        </>
      }
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <fieldset className="grid grid-cols-1 gap-3 sm:grid-cols-[180px_1fr]">
          <label>
            <span className="gk-label">{t('putModal.keyType')}</span>
            <select className="gk-input" value={keyType} onChange={(event) => setKeyType(event.target.value as DataType)}>
              {KEY_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`dataTypes.${type}`)}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="gk-label">{t('putModal.key')}</span>
            <input
              className="gk-input gk-mono"
              value={key}
              onChange={(event) => setKey(event.target.value)}
              placeholder={keyJson ? t('putModal.jsonPlaceholder') : t('putModal.valuePlaceholder')}
            />
          </label>
          {keyType === 'CLASS' && (
            <label className="sm:col-span-2">
              <span className="gk-label">{t('putModal.keyClassName')}</span>
              <input
                className="gk-input gk-mono"
                value={keyClassName}
                onChange={(event) => setKeyClassName(event.target.value)}
                placeholder={t('putModal.classPlaceholder')}
              />
            </label>
          )}
          {keyJson && key.length > 0 && !parsedKey.ok && <p className="text-xs text-gk-danger sm:col-span-2">{t('inspector.invalidJson')}</p>}
        </fieldset>

        <fieldset className="space-y-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-[180px_1fr]">
            <label>
              <span className="gk-label">{t('putModal.valueType')}</span>
              <select className="gk-input" value={valueType} onChange={(event) => changeValueType(event.target.value as DataType)}>
                {VALUE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {t(`dataTypes.${type}`)}
                  </option>
                ))}
              </select>
            </label>
            {valueType === 'CLASS' ? (
              <label>
                <span className="gk-label">{t('putModal.valueClassName')}</span>
                <input
                  className="gk-input gk-mono"
                  value={valueClassName}
                  onChange={(event) => setValueClassName(event.target.value)}
                  placeholder={t('putModal.classPlaceholder')}
                />
              </label>
            ) : valueType === 'IDENTIFIED_DTO' ? (
              <div>
                <span className="gk-label">{t('putModal.dtoClass')}</span>
                <div className="flex gap-2">
                  <select
                    className="gk-input gk-mono min-w-0"
                    value={dtoKey}
                    onChange={(event) => changeDto(event.target.value)}
                    disabled={dtoSchemas.length === 0}
                    aria-label={t('putModal.dtoClass')}
                  >
                    <option value="">{dtoSchemas.length === 0 ? t('putModal.noDtoSchemas') : t('putModal.pickDto')}</option>
                    {dtoSchemas.map((schema) => (
                      <option key={`${schema.factoryId}/${schema.classId}`} value={`${schema.factoryId}/${schema.classId}`}>
                        {schemaLabel(schema, registry.data)}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    className="gk-btn-secondary shrink-0 px-2.5"
                    onClick={() => fillTemplate(dtoKey)}
                    disabled={!dtoSchema}
                    title={t('putModal.generateTemplateHint')}
                  >
                    <Wand2 className="h-4 w-4" aria-hidden />
                    {t('putModal.generateTemplate')}
                  </button>
                </div>
              </div>
            ) : (
              <div />
            )}
          </div>
          {valueType === 'IDENTIFIED_DTO' && dtoSchemas.length === 0 && (
            <p className="flex flex-wrap items-center gap-2 rounded-md border border-gk-warning/40 bg-gk-warning/5 p-2.5 text-xs text-gk-text">
              {t('putModal.noDtoSchemasHint')}
              <Link to="/schemas" className="inline-flex items-center gap-1 text-gk-cyan hover:underline" onClick={close}>
                <FileCode2 className="h-3.5 w-3.5" aria-hidden />
                {t('putModal.openStudio')}
              </Link>
            </p>
          )}
          {hint && (
            <p className="flex items-start gap-1.5 text-xs text-gk-muted">
              <Info className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
              {hint}
            </p>
          )}
          <div>
            <span className="gk-label">{t('putModal.value')}</span>
            {valueJson ? (
              <JsonEditor value={valueText} onChange={setValueText} minHeight="200px" ariaLabel={t('putModal.value')} />
            ) : valueType === 'STRING' ? (
              <textarea
                className="gk-input gk-mono min-h-[100px] resize-y"
                value={valueText}
                onChange={(event) => setValueText(event.target.value)}
                placeholder={t('putModal.valuePlaceholder')}
              />
            ) : valueType === 'BOOLEAN' ? (
              <select className="gk-input gk-mono" value={valueText} onChange={(event) => setValueText(event.target.value)}>
                <option value="">—</option>
                <option value="true">true</option>
                <option value="false">false</option>
              </select>
            ) : (
              <input
                className="gk-input gk-mono"
                value={valueText}
                onChange={(event) => setValueText(event.target.value)}
                placeholder={t('putModal.valuePlaceholder')}
              />
            )}
            {valueJson && !parsedValue.ok && (
              <p className="mt-1 text-xs text-gk-danger">
                {t('inspector.invalidJson')}: <span className="gk-mono">{parsedValue.message}</span>
              </p>
            )}
          </div>
        </fieldset>

        <label className="block w-56">
          <span className="gk-label">{t('putModal.ttl')}</span>
          <input
            className={`gk-input gk-mono ${ttlParsed.ok ? '' : 'border-gk-danger'}`}
            value={ttl}
            onChange={(event) => setTtl(event.target.value)}
            placeholder={t('putModal.ttlPlaceholder')}
            inputMode="numeric"
          />
          <span className="mt-1 block text-[11px] text-gk-subtle">{ttlParsed.ok ? t('putModal.ttlHint') : t('inspector.ttlInvalid')}</span>
        </label>

        {error !== null && <ErrorMessage error={error} />}
        <button type="submit" className="hidden" aria-hidden tabIndex={-1} />
      </form>
    </Modal>
  );
}
