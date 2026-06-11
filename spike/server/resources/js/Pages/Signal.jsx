import { usePage } from '@inertiajs/react';

// Signal page (S0.6 / ADR-0005). On a NATIVE host this never paints: the bridge
// detects `inp.signal` in the page props on 'navigate' and emits a `signal`
// message instead. On the web / browser-mock it renders this tiny fallback so
// the redirect target is never a blank screen.
export default function Signal() {
  const { inp } = usePage().props;
  const sig = inp?.signal;
  return (
    <div style={{ padding: 24, font: '14px/1.5 system-ui, sans-serif', color: '#6b7280' }}>
      <p>signal: <strong>{sig?.name}</strong></p>
      <p>fallback: <a href={sig?.fallbackUrl}>{sig?.fallbackUrl}</a></p>
      <p style={{ fontSize: 12 }}>(A native client would have intercepted this and never rendered it.)</p>
    </div>
  );
}
