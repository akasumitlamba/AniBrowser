(() => {
  'use strict';
  if (globalThis.__aniBrave) return;
  const media = new Set();
  const attempts = new WeakMap();
  const roots = new WeakSet();
  let rate = 1;
  let blocked = false;
  let lastScroll = 0;
  let lastReveal = 0;
  const send = payload => {
    try { globalThis.aniBraveNative(JSON.stringify(payload)); } catch (_) {}
  };
  const apply = video => {
    if (!video || !video.isConnected) { media.delete(video); return; }
    const count = attempts.get(video) || 0;
    if (Math.abs(video.playbackRate - rate) < 0.001) return;
    if (count >= 6) {
      if (!blocked) { blocked = true; send({type: 'rateRejected'}); }
      return;
    }
    attempts.set(video, count + 1);
    try { video.defaultPlaybackRate = rate; video.playbackRate = rate; } catch (_) {}
  };
  const discover = root => {
    if (!root || roots.has(root)) return;
    roots.add(root);
    ['loadedmetadata', 'loadstart', 'playing', 'ratechange'].forEach(name => {
      root.addEventListener(name, event => {
        const video = event.target;
        if (!video || !/^(VIDEO|AUDIO)$/.test(video.tagName)) return;
        media.add(video);
        if (name === 'loadstart' || name === 'loadedmetadata') attempts.delete(video);
        apply(video);
      }, true);
    });
    for (const video of root.querySelectorAll('video,audio')) { media.add(video); apply(video); }
    for (const element of root.querySelectorAll('*')) if (element.shadowRoot) discover(element.shadowRoot);
  };
  globalThis.__aniBrave = {
    configure(value) {
      if (!Number.isFinite(value) || value < 0.25 || value > 4) return;
      rate = value; blocked = false;
      for (const video of media) { attempts.delete(video); apply(video); }
    },
    // Used only for launcher artwork; never move or replace website player DOM.
    icon() {
      const icons = [...document.querySelectorAll('link[rel~="icon"],link[rel="apple-touch-icon"]')];
      return {url: location.href, title: document.title, icon: icons.length ? icons[icons.length - 1].href : ''};
    }
  };
  discover(document);
  document.addEventListener('DOMContentLoaded', () => discoverAfterLoad(), {once: true});
  function discoverAfterLoad() {
    for (const video of document.querySelectorAll('video,audio')) { media.add(video); apply(video); }
    for (const element of document.querySelectorAll('*')) if (element.shadowRoot) discover(element.shadowRoot);
  }
  document.addEventListener('fullscreenchange', () => {
    send({type: 'fullscreen', enabled: !!document.fullscreenElement});
  });
  document.addEventListener('scroll', event => {
    const target = event.target === document ? document.scrollingElement : event.target;
    const scroll = target ? target.scrollTop : 0;
    const now = Date.now();
    if (Math.abs(scroll - lastScroll) >= 24 && now - lastReveal > 1500) {
      lastReveal = now; send({type: 'reveal'});
    }
    lastScroll = scroll;
  }, {capture: true, passive: true});
  let touchY = null;
  document.addEventListener('touchstart', e => { touchY = e.touches.length === 1 ? e.touches[0].clientY : null; }, {passive: true});
  document.addEventListener('touchmove', e => {
    if (touchY === null || e.touches.length !== 1) return;
    if (Math.abs(e.touches[0].clientY - touchY) >= 24) {
      touchY = null; send({type: 'reveal'});
    }
  }, {passive: true});
  window.addEventListener('pagehide', () => media.clear(), {once: true});
  window.addEventListener('resize', () => send({type: 'resize'}), {passive: true});
})();
