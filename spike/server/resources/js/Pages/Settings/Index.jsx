import { router } from '@inertiajs/react';
import Layout from '../../Layout';
import { spikeSend, bridgeInfo, isNative } from '../../spike/inp-spike';

// Page 4: SETTINGS. Doubles as the bridge round-trip test surface for S0.1
// acceptance criterion 2 (JS->native echo) and a `replace_root` candidate
// for path-config experiments.
export default function Settings() {
  const info = bridgeInfo();

  function sendEcho() {
    // JS -> native: this should appear in the native debug overlay, and the
    // native side bounces an `echo` back which our bridge logs (criterion 2).
    spikeSend('log', { level: 'info', message: 'hello from web', context: { at: Date.now() } });
  }

  return (
    <Layout title="Settings">
      <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '6px 16px', marginBottom: 20 }}>
        <dt style={dt}>Bridge host</dt><dd>{info.host}</dd>
        <dt style={dt}>Native</dt><dd>{String(isNative())}</dd>
        <dt style={dt}>screenId</dt><dd>{info.configured?.screenId ?? '—'}</dd>
        <dt style={dt}>debug</dt><dd>{String(info.configured?.debug ?? false)}</dd>
      </dl>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <button onClick={sendEcho} style={btn}>Send echo → native</button>
        <button onClick={() => router.reload()} style={{ ...btn, background: '#6b7280' }}>Reload (pull-to-refresh proxy)</button>
      </div>

      <p style={{ color: '#6b7280', fontSize: 13, marginTop: 16 }}>
        On a native host the echo lands in the debug overlay and a native→web ack
        is logged back. In a plain browser it lands in the bottom mock overlay.
      </p>
    </Layout>
  );
}

const dt = { fontWeight: 600, color: '#374151' };
const btn = { background: '#4f46e5', color: 'white', padding: '10px 16px', borderRadius: 8, border: 0, fontWeight: 600, cursor: 'pointer' };
