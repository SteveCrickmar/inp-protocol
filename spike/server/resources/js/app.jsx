import { createInertiaApp } from '@inertiajs/react';
import { createRoot } from 'react-dom/client';
import { initSpikeBridge } from './spike/inp-spike';

// Boot the spike INP bridge BEFORE Inertia so the very first render can read
// window.__INP__ (handshake ordering, spec §2.3). On the web (no native host)
// this is an inert no-op that also installs a small browser mock so the harness
// is demonstrable in a plain browser tab.
initSpikeBridge();

createInertiaApp({
  resolve: (name) => {
    const pages = import.meta.glob('./Pages/**/*.jsx', { eager: true });
    return pages[`./Pages/${name}.jsx`];
  },
  setup({ el, App, props }) {
    createRoot(el).render(<App {...props} />);
  },
  progress: {
    // Native shows its own progress affordance; keep the web bar for browser use.
    color: '#4f46e5',
  },
});
