/* SPDX-License-Identifier: MPL-2.0 */
"use strict";
(() => {
  const allowed = [1, 1.25, 1.5, 1.75, 2];
  let speed = 1;
  const attached = new WeakSet();
  const mediaElements = new Set();
  let recoveryTimer = null;
  const attempts = new WeakMap();
  const recovering = new WeakSet();

  let backgroundAllowed = false;
  let appInBackground = false;
  let policyReceived = false;
  let speedReceived = false;
  const visibilityProperties = ["hidden", "visibilityState", "webkitHidden", "webkitVisibilityState"];
  const visibilityOriginals = new Map();
  const pageDocument = document.wrappedJSObject || document;
  const hiddenGetter = Object.getOwnPropertyDescriptor(Document.prototype, "hidden")?.get;
  const nativeHidden = () => hiddenGetter ? hiddenGetter.call(document) : document.hidden;
  let visibilityOverridden = false;
  function updateVisibility() {
    // Only a playing media document needs background visibility protection.
    // Idle tabs and frames must retain normal browser throttling.
    const playing = [...mediaElements].some(media => media.isConnected && !media.paused && !media.ended);
    const enabled = backgroundAllowed && playing && (appInBackground || nativeHidden());
    if (enabled === visibilityOverridden) return;
    visibilityOverridden = enabled;
    for (const key of visibilityProperties) {
      try {
        if (enabled) {
          visibilityOriginals.set(key, Object.getOwnPropertyDescriptor(pageDocument, key));
          const getter = () => key.includes("State") ? "visible" : false;
          Object.defineProperty(pageDocument, key, {get:typeof exportFunction === "function" ? exportFunction(getter, pageDocument) : getter, configurable:true});
        } else {
          const descriptor = visibilityOriginals.get(key);
          if (descriptor) Object.defineProperty(pageDocument, key, descriptor); else delete pageDocument[key];
        }
      } catch (_) {}
    }
    if (!enabled) visibilityOriginals.clear();
  }
  const stopVisibility = event => {
    updateVisibility();
    if (visibilityOverridden) event.stopImmediatePropagation?.();
  };
  function pauseMedia() {
    if (!backgroundAllowed && appInBackground) for (const media of mediaElements) {
      if (!media.paused) media.pause?.();
    }
  }
  function setMediaPolicy(message) {
    if (message.type !== "mediaPolicy") return;
    backgroundAllowed = message.enabled === true;
    appInBackground = message.background === true;
    pauseMedia();
    updateVisibility();
  }
  for (const event of ["visibilitychange", "webkitvisibilitychange"]) document.addEventListener(event, stopVisibility, true);
  browser.runtime.sendMessage({type:"mediaPolicy"}).then(message => {
    if (!policyReceived) setMediaPolicy(message);
  }).catch(()=>{});
  function apply(video) {
    if (!video.isConnected) return;
    // Bound retries when a player continually fights the selected rate.
    const now = performance.now();
    let state = attempts.get(video);
    if (!state || now - state.start > 1000) state = {start: now, count: 0};
    if (state.count >= 2) return;
    if (video.playbackRate === speed && video.defaultPlaybackRate === speed && video.preservesPitch === true) return;
    state.count++;
    attempts.set(video, state);
    try {
      if (video.preservesPitch !== true) video.preservesPitch = true;
      if (video.defaultPlaybackRate !== speed) video.defaultPlaybackRate = speed;
      if (video.playbackRate !== speed) video.playbackRate = speed;
    } catch (_) { /* A player may temporarily reject a rate during media replacement. */ }
  }
  function track(media) {
    if (!media || (media.tagName !== "VIDEO" && media.tagName !== "AUDIO")) return;
    mediaElements.add(media);
    if (!attached.has(media)) {
      attached.add(media);
      media.addEventListener("ratechange", () => {
        if (media.playbackRate === speed || recovering.has(media)) return;
        recovering.add(media);
        setTimeout(() => { recovering.delete(media); apply(media); }, 500);
      });
    }
    apply(media);
    if (recoveryTimer === null) recoveryTimer = setInterval(() => {
      for (const element of mediaElements) {
        if (!element.isConnected) mediaElements.delete(element);
        else if (!element.paused) apply(element);
      }
      updateVisibility();
      if (!mediaElements.size) { clearInterval(recoveryTimer); recoveryTimer = null; }
    }, 5000);
  }
  function scan() {
    for (const media of document.querySelectorAll("video,audio")) track(media);
  }
  function setSpeed(value) {
    if (!allowed.includes(value) || value === speed) return;
    speed = value;
    for (const media of mediaElements) attempts.delete(media);
    scan();
  }
  browser.runtime.onMessage.addListener(message => {
    if (allowed.includes(message.speed)) { speedReceived = true; setSpeed(message.speed); }
    if (message.type === "mediaPolicy") { policyReceived = true; setMediaPolicy(message); }
  });
  browser.runtime.sendMessage({type: "speed"}).then(message => {
    if (!speedReceived) setSpeed(message.speed);
  }).catch(() => {});
  // Captured media events discover dynamically inserted players without observing
  // every DOM mutation made by the website (comments, ads, subtitles, animation).
  for (const name of ["loadedmetadata", "play", "playing", "emptied", "pause", "ended"]) {
    document.addEventListener(name, event => {
      track(event.target);
      pauseMedia();
      updateVisibility();
    }, true);
  }
  document.addEventListener("DOMContentLoaded", scan, {once:true});
  scan();
})();
