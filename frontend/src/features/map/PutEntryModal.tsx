import { useState } from 'react';
import { Info } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import { DATA_TYPES, KEY_TYPES, type DataType, type EntryDTO, type PutEntryRequest } from '../../api/types';
import { JsonEditor } from '../../components/editor/JsonEditor';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/Toast';
import { useInvalidateMapData } from './useInvalidateMapData';
import { editorTextToValue, isJsonEdited, parseTtl } from './valueEditing';

/** Value types offered when creating an entry (Compact needs an existing schema: edit it in the inspector). */
const VALUE_TYPES: DataType[] = DATA_TYPES.filter((type) => type !== 'COMPACT');

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
    (valueType !== 'CLASS' || valueClassName.trim().length > 0);

  const reset = () => {
    setKey('');
    setValueText(valueJson ? '{\n  \n}' : '');
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
    if (isJsonEdited(next) !== wasJson) {
      setValueText(isJsonEdited(next) ? '{\n  \n}' : '');
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
        valueClassName: valueType === 'CLASS' ? valueClassName.trim() : undefined,
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

  const hint = valueType === 'JSON' ? t('putModal.jsonHint') : valueType === 'CLASS' ? t('putModal.classHint') : null;

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
          {key.length > 0 && !parsedKey.ok && <p className="text-xs text-gk-danger sm:col-span-2">{t('inspector.invalidJson')}</p>}
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
            ) : (
              <div />
            )}
          </div>
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
            {!parsedValue.ok && (
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
