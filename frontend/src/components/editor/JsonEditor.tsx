import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { EditorView } from '@codemirror/view';
import { tags } from '@lezer/highlight';

/** GitKraken-like dark editor chrome. */
const gitKrakenTheme = EditorView.theme(
  {
    '&': {
      backgroundColor: '#14171A',
      color: '#E6EDF3',
      fontSize: '13px',
      border: '1px solid #2D333B',
      borderRadius: '6px',
    },
    '&.cm-focused': { outline: 'none', borderColor: '#00E5FF' },
    '.cm-content': { fontFamily: '"JetBrains Mono", "Fira Code", monospace', caretColor: '#00E5FF', padding: '8px 0' },
    '.cm-scroller': { fontFamily: '"JetBrains Mono", "Fira Code", monospace', lineHeight: '1.55' },
    '.cm-gutters': { backgroundColor: '#1C2128', color: '#6E7681', border: 'none', borderRight: '1px solid #2D333B' },
    '.cm-activeLine': { backgroundColor: 'rgba(0, 229, 255, 0.05)' },
    '.cm-activeLineGutter': { backgroundColor: 'rgba(0, 229, 255, 0.08)', color: '#E6EDF3' },
    '.cm-selectionBackground, &.cm-focused .cm-selectionBackground, ::selection': {
      backgroundColor: 'rgba(0, 229, 255, 0.22) !important',
    },
    '.cm-cursor': { borderLeftColor: '#00E5FF' },
    '.cm-matchingBracket': { backgroundColor: 'rgba(155, 81, 224, 0.35)', outline: 'none' },
    '.cm-foldPlaceholder': { backgroundColor: '#22272E', border: 'none', color: '#8B949E' },
  },
  { dark: true },
);

/** JSON token colors in the GitKraken accent palette. */
const gitKrakenHighlight = HighlightStyle.define([
  { tag: tags.propertyName, color: '#00E5FF' },
  { tag: tags.string, color: '#2ECC71' },
  { tag: tags.number, color: '#F39C12' },
  { tag: [tags.bool, tags.null], color: '#9B51E0', fontWeight: '600' },
  { tag: [tags.brace, tags.squareBracket, tags.separator, tags.punctuation], color: '#8B949E' },
  { tag: tags.invalid, color: '#E74C3C' },
]);

const extensions = [json(), gitKrakenTheme, syntaxHighlighting(gitKrakenHighlight), EditorView.lineWrapping];

interface JsonEditorProps {
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  /** CSS height, e.g. `320px` or `100%`. */
  height?: string;
  minHeight?: string;
  ariaLabel?: string;
}

/** CodeMirror JSON editor/viewer with syntax highlighting, folding and bracket matching. */
export function JsonEditor({ value, onChange, readOnly = false, height, minHeight = '120px', ariaLabel }: JsonEditorProps) {
  return (
    <CodeMirror
      value={value}
      onChange={onChange}
      readOnly={readOnly}
      editable={!readOnly}
      height={height}
      minHeight={minHeight}
      theme="none"
      extensions={extensions}
      aria-label={ariaLabel}
      basicSetup={{
        lineNumbers: true,
        foldGutter: true,
        highlightActiveLine: !readOnly,
        highlightActiveLineGutter: !readOnly,
        bracketMatching: true,
        closeBrackets: !readOnly,
        autocompletion: false,
        searchKeymap: true,
      }}
    />
  );
}
