import { Link } from '@inertiajs/react';
import Layout from '../../Layout';

// Page 2: DETAIL (push target). Tapping a list item advances here; native back
// should pop and restore the list's scroll position (NAV-1/NAV-2).
export default function Show({ product }) {
  return (
    <Layout title={product.name}>
      <div style={{ color: '#6b7280', marginBottom: 16 }}>${product.price} · {product.category}</div>
      <p style={{ marginBottom: 16 }}>{product.description}</p>

      {/* PROBE S0.3: a real <video> so re-parenting can be observed against
          media playback (does it pause/keep-playing/lose buffer when the live
          WKWebView moves between view controllers?). Public sample asset. */}
      <video
        controls
        playsInline
        width="100%"
        style={{ borderRadius: 8, marginBottom: 16, background: '#000' }}
        poster="https://interactive-examples.mdn.mozilla.net/media/cc0-images/flowers.jpg"
        src="https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
      />

      {/* Tall block so the detail screen also has its own scroll state. */}
      <div style={{ height: 600, background: 'linear-gradient(#f9fafb,#e5e7eb)', borderRadius: 8, marginBottom: 16,
        display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#9ca3af' }}>
        media / detail body
      </div>

      <div style={{ display: 'flex', gap: 12 }}>
        {/* Modal-context candidate (PC-1, path-config context=modal). */}
        <Link href={`/products/${product.id}/edit`} native={{ action: 'modal' }} style={btn}>
          Edit (modal)
        </Link>
        <Link href="/products" style={{ ...btn, background: '#6b7280' }}>Back to list</Link>
      </div>
    </Layout>
  );
}

const btn = {
  display: 'inline-block',
  background: '#4f46e5',
  color: 'white',
  padding: '10px 16px',
  borderRadius: 8,
  textDecoration: 'none',
  fontWeight: 600,
};
