// ---------------------------------------------------------------------------
// INP spike bridge — DISPOSABLE Phase-0 code (OC-5).
//
// This is NOT the @inertia-native/adapter. It is the smallest thing that proves
// the transport round-trips, so the iOS/Android harness apps have a real web
// peer to talk to. The production adapter (Phase 2, A2.x) reimplements all of
// this properly against the frozen protocol. Do not import this anywhere real.
//
// What it demonstrates for S0.1 acceptance:
//   (2) JS->native and native->JS echo messages round-trip and appear in the
//       native debug overlay.
// It also lays the visit.propose groundwork the other spike tasks build on, but
// deliberately stays observational in a plain browser so the 5 pages remain
// navigable without a native host (S0.1 acceptance criterion 3).
// ---------------------------------------------------------------------------

import { router } from '@inertiajs/react';

const PROTOCOL_MAJOR = 1;

let host = null; // 'ios' | 'android' | 'browser-mock'
let configured = null; // last session.configure payload
const listeners = new Set();

function uuid() {
  // Spike-grade id. Real adapter uses crypto.randomUUID with a fallback.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function detectHost() {
  if (typeof window === 'undefined') return null; // SSR safety
  // The native user script defines window.__INP__ at document-start (§2.3).
  if (window.__INP__ && window.__INP__.platform) return window.__INP__.platform;
  return 'browser-mock';
}

function rawSend(envelope) {
  const json = JSON.stringify(envelope);
  if (host === 'ios' && window.webkit?.messageHandlers?.inp) {
    window.webkit.messageHandlers.inp.postMessage(json);
  } else if (host === 'android' && window.InpChannel?.postMessage) {
    window.InpChannel.postMessage(json);
  } else {
    // browser-mock: echo to the console + on-page log so the harness is usable
    // in a plain browser tab during development.
    // eslint-disable-next-line no-console
    console.debug('[INP→native(mock)]', envelope.type, envelope.payload);
    appendMockOverlay(`→ ${envelope.type}`);
  }
}

export function spikeSend(type, payload = {}, replyTo = null) {
  const envelope = { inp: PROTOCOL_MAJOR, id: uuid(), replyTo, type, payload };
  rawSend(envelope);
  return envelope.id;
}

// Native -> web entry point. The native side calls
//   webView.evaluateJavaScript("window.__INP__.receive(<json>)")
function receive(json) {
  let env;
  try {
    env = typeof json === 'string' ? JSON.parse(json) : json;
  } catch (e) {
    // Malformed inbound must never throw into app code (spec §2.4 / §8).
    spikeSend('log', { level: 'error', message: 'malformed inbound', context: String(json).slice(0, 200) });
    return;
  }
  if (!env || env.inp !== PROTOCOL_MAJOR) return; // wrong/absent major: ignore

  switch (env.type) {
    case 'session.configure':
      configured = env.payload;
      break;
    case 'echo': // spike-only: native asks us to bounce a message back
      spikeSend('log', { level: 'debug', message: 'echo-ack', context: env.payload }, env.id);
      break;
    case 'visit.execute':
      // Native commands the real Inertia visit. Flag it internal so our own
      // 'before' interceptor lets it pass (spec §3.3 rule 1).
      internalVisit(env.payload?.url, env.payload?.options || {});
      break;
    case 'page.restore':
      // Real restore is ADR-0001's job; the spike just re-fetches (strategy d).
      internalVisit(window.location.pathname + window.location.search, { preserveScroll: true });
      spikeSend('page.restored', { screenId: env.payload?.screenId, url: window.location.pathname, ok: false });
      break;
    default:
      // unknown type => ignore + debug log (forward compatibility, §2.2)
      spikeSend('log', { level: 'debug', message: 'ignored unknown type', context: env.type });
  }
  listeners.forEach((fn) => fn(env));
}

let internalFlag = false;
function internalVisit(url, options) {
  if (!url) return;
  internalFlag = true;
  router.visit(url, {
    preserveScroll: !!options.preserveScroll,
    preserveState: !!options.preserveState,
    onFinish: () => {
      internalFlag = false;
    },
  });
}

export function onInp(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function isNative() {
  return host === 'ios' || host === 'android';
}

export function bridgeInfo() {
  return { host, configured };
}

// --- Inertia lifecycle wiring (spike-level, observational on web) -----------

function wireInertia() {
  router.on('before', (event) => {
    if (internalFlag) return; // commanded visit, allow (rule 1)
    const visit = event.detail.visit;
    if (visit.method !== 'get') return; // forms run in place (rule 2)

    const url = typeof visit.url === 'string' ? visit.url : visit.url?.href;
    const action = url === window.location.href || url === window.location.pathname ? 'replace' : 'advance';

    if (isNative()) {
      // In a real native host the web side proposes and waits; cancel the visit.
      event.preventDefault();
      spikeSend('visit.propose', {
        proposalId: uuid(),
        url,
        method: 'get',
        action,
        options: {
          preserveScroll: !!visit.preserveScroll,
          preserveState: !!visit.preserveState,
          only: visit.only || [],
          native: visit.native || null,
        },
      });
    } else {
      // browser-mock: do NOT cancel — keep the 5 pages navigable in a browser.
      spikeSend('visit.propose', { url, method: 'get', action, mock: true });
    }
  });

  router.on('navigate', (event) => {
    const page = event.detail.page;
    spikeSend('visit.completed', {
      screenId: configured?.screenId ?? null,
      url: page.url,
      component: page.component,
      title: typeof document !== 'undefined' ? document.title : null,
    });

    // S0.6 / ADR-0005: reserved-shared-prop signal detection. If the server set
    // `inp.signal`, report it and (in native) suppress rendering of this page.
    const signal = page.props?.inp?.signal;
    if (signal && isNative()) {
      spikeSend('signal', {
        name: signal.name,
        flash: signal.flash ?? null,
        fallbackUrl: signal.fallbackUrl ?? null,
      });
    }
  });
}

// --- Minimal browser-mock on-page overlay (dev convenience only) ------------

function appendMockOverlay(line) {
  if (typeof document === 'undefined') return;
  let el = document.getElementById('inp-mock-overlay');
  if (!el) {
    el = document.createElement('div');
    el.id = 'inp-mock-overlay';
    el.style.cssText =
      'position:fixed;bottom:0;left:0;right:0;max-height:25vh;overflow:auto;' +
      'font:11px/1.4 monospace;background:rgba(0,0,0,.8);color:#9ae6b4;' +
      'padding:6px 8px;z-index:99999;white-space:pre-wrap';
    document.body.appendChild(el);
  }
  const ts = new Date().toISOString().slice(11, 19);
  el.textContent = `${ts}  ${line}\n` + el.textContent.slice(0, 4000);
}

export function initSpikeBridge() {
  if (typeof window === 'undefined') return; // SSR no-op
  host = detectHost();

  // Expose the native->web entry point. The native user script may pre-seed
  // window.__INP__ with handshake constants; we attach receive() onto it.
  window.__INP__ = window.__INP__ || { platform: 'browser-mock' };
  window.__INP__.receive = receive;

  wireInertia();

  // Handshake: announce ourselves (spec §2.3). On the mock host this just logs.
  spikeSend('adapter.ready', {
    adapterVersion: '0.0.0-spike',
    inertiaVersion: '3.x',
    protocolVersion: PROTOCOL_MAJOR,
    page: { url: window.location?.pathname, component: null },
  });
}
