import { useForm, Link } from '@inertiajs/react';
import Layout from '../../Layout';

// Page 3: EDIT FORM (modal-ish). Exercises the non-GET visit + 303 redirect +
// signal flow (FRM-1/FRM-2, S0.6). On submit the server redirects via a
// recede signal so native pops the modal; web gets a plain redirect.
export default function Edit({ product }) {
  const { data, setData, put, processing, errors } = useForm({
    name: product.name,
    price: product.price,
    description: product.description,
  });

  function submit(e) {
    e.preventDefault();
    put(`/products/${product.id}`);
  }

  return (
    <Layout title={`Edit ${product.name}`}>
      <form onSubmit={submit} style={{ display: 'grid', gap: 12, maxWidth: 420 }}>
        <label style={lbl}>
          Name
          <input value={data.name} onChange={(e) => setData('name', e.target.value)} style={input} />
          {errors.name && <span style={err}>{errors.name}</span>}
        </label>
        <label style={lbl}>
          Price
          <input value={data.price} onChange={(e) => setData('price', e.target.value)} style={input} />
          {errors.price && <span style={err}>{errors.price}</span>}
        </label>
        <label style={lbl}>
          Description
          <textarea value={data.description} onChange={(e) => setData('description', e.target.value)} rows={4} style={input} />
        </label>
        <div style={{ display: 'flex', gap: 12 }}>
          <button type="submit" disabled={processing} style={btn}>
            {processing ? 'Saving…' : 'Save'}
          </button>
          <Link href={`/products/${product.id}`} style={{ ...btn, background: '#6b7280' }}>Cancel</Link>
        </div>
      </form>
    </Layout>
  );
}

const lbl = { display: 'grid', gap: 4, fontWeight: 600, fontSize: 13 };
const input = { padding: '8px 10px', border: '1px solid #d1d5db', borderRadius: 6, font: 'inherit' };
const err = { color: '#dc2626', fontWeight: 400, fontSize: 12 };
const btn = { background: '#4f46e5', color: 'white', padding: '10px 16px', borderRadius: 8, border: 0, fontWeight: 600, textDecoration: 'none', cursor: 'pointer' };
