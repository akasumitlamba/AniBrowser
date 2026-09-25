/* SPDX-License-Identifier: MPL-2.0 */
(() => {
  "use strict";
  // Preserve the site's fullscreen tree: reparenting a video loses subtitles,
  // custom controls and framework event handlers. Native video controls are the
  // fallback when no site controls are present inside that tree.
  const controls = '[role="slider"], input[type="range"], .vjs-control-bar, .jw-controlbar, .plyr__controls, .shaka-controls-container, .ytp-chrome-bottom, [class*="control-bar"], [class*="controls-bar"]';
  let active = null, observer = null;
  const originals = new Map();
  function restore() {
    observer?.disconnect(); observer = null;
    for (const [video, original] of originals) video.controls = original;
    originals.clear(); active = null;
  }
  function update() {
    const root = document.fullscreenElement;
    if (!root) { restore(); return; }
    const videos = root.tagName === 'VIDEO' ? [root] : Array.from(root.querySelectorAll('video'));
    // Container-level controls (including temporarily auto-hidden controls) belong
    // to the website. Never replace them or force them visible.
    const siteControls = root.tagName !== 'VIDEO' && !!root.querySelector(controls);
    for (const [video, original] of originals) {
      if (!videos.includes(video) || siteControls) { video.controls = original; originals.delete(video); }
    }
    if (!siteControls) for (const video of videos) {
      if (!originals.has(video)) originals.set(video, video.controls);
      if (!video.controls) video.controls = true;
    }
  }
  function changed() {
    if (document.fullscreenElement !== active) {
      restore(); active = document.fullscreenElement;
      if (active) {
        observer = new MutationObserver(update);
        observer.observe(active, {childList: true, subtree: true});
      }
    }
    update();
  }
  document.addEventListener('fullscreenchange', changed);
  document.addEventListener('loadedmetadata', changed, true);
  window.addEventListener('pagehide', restore);
  changed();
})();
