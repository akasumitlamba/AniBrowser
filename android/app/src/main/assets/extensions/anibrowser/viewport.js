/* SPDX-License-Identifier: MPL-2.0 */
(() => {
  "use strict";
  if (window.top !== window) return;
  // Native viewport reserves the toolbar area, including fixed site controls.
  // No document mutation is needed in the default layout.
  let inset = 0;
  let insetReceived = false;
  const originals = new Map();
  const scrollCandidates = new Set();
  let scheduled = false;
  function gap() { return document.fullscreenElement ? 0 : inset / (window.visualViewport?.scale || 1); }
  function apply(element) {
    if (!element) return;
    const clearance = gap();
    let saved = originals.get(element);
    if (!saved && !clearance) return;
    if (!saved) {
      saved = {value: element.style.getPropertyValue("padding-bottom"), priority: element.style.getPropertyPriority("padding-bottom"), base: parseFloat(getComputedStyle(element).paddingBottom) || 0};
      originals.set(element, saved);
    }
    if (!clearance) {
      if (saved.value) element.style.setProperty("padding-bottom", saved.value, saved.priority);
      else element.style.removeProperty("padding-bottom");
      originals.delete(element);
    } else {
      const value = (saved.base + clearance) + "px";
      if (element.style.getPropertyValue("padding-bottom") !== value)
        element.style.setProperty("padding-bottom", value, "important");
    }
  }
  function update() {
    const root = document.scrollingElement || document.documentElement;
    apply(root);
    for (const element of [...originals.keys()]) {
      if (!element.isConnected) originals.delete(element);
      else if (element !== root) apply(element);
    }
  }
  function scheduleUpdate() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      for (const element of scrollCandidates) {
        if (!element.isConnected || originals.has(element) || !gap()) continue;
        const rect = element.getBoundingClientRect();
        if (rect.height > innerHeight / 2 && rect.bottom >= innerHeight - 100 && element.scrollHeight > element.clientHeight) apply(element);
      }
      scrollCandidates.clear();
      update();
    });
  }
  // App-style sites can scroll a panel instead of the document itself.
  document.addEventListener("scroll", event => {
    const element = event.target;
    if (!(element instanceof HTMLElement) || element === document.scrollingElement || originals.has(element) || !gap()) return;
    scrollCandidates.add(element);
    scheduleUpdate();
  }, {capture: true, passive: true});
  document.addEventListener("fullscreenchange", update);
  window.addEventListener("resize", scheduleUpdate, {passive: true});
  window.visualViewport?.addEventListener("resize", scheduleUpdate, {passive: true});
  browser.runtime.onMessage.addListener(message => {
    if (message.type === "chromeInset" && Number.isFinite(message.bottom)) {
      insetReceived = true;
      const next = Math.max(0, Math.min(120, message.bottom));
      if (next !== inset) { inset = next; scheduleUpdate(); }
    }
  });
  browser.runtime.sendMessage({type: "chromeInset"}).then(message => {
    if (!insetReceived && Number.isFinite(message?.bottom)) inset = Math.max(0, Math.min(120, message.bottom));
    update();
  }).catch(update);
  update();
})();
