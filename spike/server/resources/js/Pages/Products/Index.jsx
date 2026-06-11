import { Link, usePage } from '@inertiajs/react';
import Layout from '../../Layout';

// Page 1: LIST. A long, scrollable list so S0.2 (restore fidelity) has a real
// scroll position to preserve across push/pop.
export default function Index({ products }) {
  const { flash } = usePage().props;

  return (
    <Layout title="Products">
      {flash?.message && (
        <div style={{ background: '#ecfdf5', color: '#065f46', padding: 8, borderRadius: 6, marginBottom: 12 }}>
          {flash.message}
        </div>
      )}
      <p style={{ color: '#6b7280', marginBottom: 12 }}>
        {products.length} items — scroll down, open one, then come back to test scroll restore (S0.2).
      </p>
      <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {products.map((p) => (
          <li key={p.id} style={{ borderBottom: '1px solid #e5e7eb', padding: '12px 0' }}>
            <Link href={`/products/${p.id}`} style={{ textDecoration: 'none', color: '#111827' }}>
              <strong>{p.name}</strong>
              <div style={{ color: '#6b7280', fontSize: 13 }}>${p.price} · {p.category}</div>
            </Link>
          </li>
        ))}
      </ul>
    </Layout>
  );
}
