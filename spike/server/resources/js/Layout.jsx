import { Link, usePage } from '@inertiajs/react';

// Shared chrome for the spike pages. Kept deliberately plain — this is a test
// harness, not a design exercise (OC-5).
export default function Layout({ title, children }) {
  const { native } = usePage().props;

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '16px', font: '15px/1.5 system-ui, sans-serif' }}>
      <header style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16, flexWrap: 'wrap' }}>
        <Link href="/products" style={navStyle}>Products</Link>
        <Link href="/settings" style={navStyle}>Settings</Link>
        <Link href="/external" style={navStyle}>External</Link>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: '#6b7280' }}>
          host: {native?.enabled ? `native/${native.platform}` : 'web'}
        </span>
      </header>
      {title && <h1 style={{ fontSize: 22, marginBottom: 12 }}>{title}</h1>}
      {children}
    </div>
  );
}

const navStyle = {
  textDecoration: 'none',
  color: '#4f46e5',
  fontWeight: 600,
};
