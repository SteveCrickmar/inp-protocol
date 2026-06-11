import Layout from '../Layout';

// Page 5: EXTERNAL-LINK page. Exercises NAV-5 (external domains open in an
// in-app browser via the native WebView policy layer; the web stack is left
// untouched). In a plain browser these are ordinary links.
export default function External() {
  return (
    <Layout title="External links">
      <p style={{ color: '#6b7280', marginBottom: 16 }}>
        Tapping these should open SFSafariViewController / Custom Tabs on a native
        host, NOT navigate the shared webview.
      </p>
      <ul style={{ listStyle: 'none', padding: 0, display: 'grid', gap: 12 }}>
        <li><a href="https://inertiajs.com" style={link}>inertiajs.com (external https)</a></li>
        <li><a href="https://github.com/SteveCrickmar/inp-protocol" style={link}>github.com/.../inp-protocol</a></li>
        <li><a href="mailto:hello@example.com" style={link}>mailto: (system handler)</a></li>
        <li><a href="tel:+15551234567" style={link}>tel: (system handler)</a></li>
        <li><a href="https://example.com" target="_blank" rel="noreferrer" style={link}>example.com (target=_blank)</a></li>
      </ul>
    </Layout>
  );
}

const link = { color: '#4f46e5', fontWeight: 600, textDecoration: 'none' };
