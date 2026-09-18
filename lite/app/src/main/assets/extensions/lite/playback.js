/* SPDX-License-Identifier: MPL-2.0 */
"use strict";
(() => {
  let speed = 1, ready = false, generation = 0;
  const media = new Set(), writes = new WeakMap();
  function prune() { for (const item of media) if (!item.isConnected) media.delete(item); }
  function apply(item) {
    if (!ready || !item.isConnected) return;
    const now = performance.now();
    const previous = writes.get(item);
    // A hostile player must not cause a hot ratechange loop. No polling timer.
    if (previous && now - previous.time < 1000 && previous.count >= 3) return;
    if (item.playbackRate === speed && item.defaultPlaybackRate === speed) return;
    writes.set(item, {time: previous && now - previous.time < 1000 ? previous.time : now,
      count: previous && now - previous.time < 1000 ? previous.count + 1 : 1});
    try { item.preservesPitch = true; item.defaultPlaybackRate = speed; item.playbackRate = speed; } catch (_) {}
  }
  function track(item) {
    if (!item || !["VIDEO", "AUDIO"].includes(item.tagName)) return;
    prune(); media.add(item); apply(item);
  }
  function settings(message) {
    if (!Number.isFinite(message?.speed) || message.speed < 0.25 || message.speed > 4) return;
    speed = message.speed; ready = true;
    prune();
    for (const item of media) { writes.delete(item); apply(item); }
  }
  function scan() { for (const item of document.querySelectorAll("video,audio")) track(item); }
  for (const name of ["loadedmetadata", "play", "playing", "emptied", "ratechange"]) {
    document.addEventListener(name, event => track(event.target), true);
  }
  function selected() {
    prune();
    return [...media].find(item => !item.paused && !item.ended) || [...media].find(item => item.tagName === "VIDEO") || [...media][0];
  }
  function announce() {
    const item = selected();
    const rect = item?.getBoundingClientRect?.();
    browser.runtime.sendMessage({type: "mediaState", hasMedia: !!item,
      playing: !!item && !item.paused && !item.ended,
      area: rect ? Math.max(0, Math.min(rect.bottom, innerHeight) - Math.max(0, rect.top)) * Math.max(0, Math.min(rect.right, innerWidth) - Math.max(0, rect.left)) : 0}).catch(() => {});
  }
  for (const name of ["loadedmetadata", "play", "playing", "pause", "ended", "emptied"])
    document.addEventListener(name, announce, true);
  document.addEventListener("DOMContentLoaded", announce, {once: true});
  announce();
  function command(value) {
    const item = selected(); if (!item) return;
    if (value === "toggle") { if (item.paused) item.play().catch(() => {}); else item.pause(); }
    else if (value === "pause") item.pause();
    else if (value === "back" || value === "forward") {
      try { item.currentTime = Math.max(0, Math.min(Number.isFinite(item.duration) ? item.duration : Infinity, item.currentTime + (value === "back" ? -10 : 10))); } catch (_) {}
    }
  }
  browser.runtime.onMessage.addListener(message => {
    if (message.type === "settings") { generation++; settings(message); }
    if (message.type === "command") command(message.command);
  });
  const initialGeneration = generation;
  browser.runtime.sendMessage({type: "settings"}).then(message => {
    if (generation === initialGeneration) settings(message);
  }).catch(() => {});
  document.addEventListener("DOMContentLoaded", scan, {once: true}); scan();
  // Never restyle, reparent or replace the site's fullscreen player container.
  // A bare video has no custom sibling controls in the fullscreen tree: expose
  // its native controls for that case, and restore its original state on exit.
  // Lite's own controls live in Android UI, so no page CSS can hide them.
  let bareVideo = null, hadControls = false;
  function fullscreen() {
    if (bareVideo) { bareVideo.controls = hadControls; bareVideo = null; }
    const root = document.fullscreenElement;
    if (root?.tagName === "VIDEO") { bareVideo = root; hadControls = root.controls; root.controls = true; track(root); }
  }
  document.addEventListener("fullscreenchange", fullscreen);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) for (const item of media) if (!item.paused) item.pause();
  });
})();
