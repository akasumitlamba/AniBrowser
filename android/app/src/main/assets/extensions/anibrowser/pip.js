/* SPDX-License-Identifier: MPL-2.0 */
(() => {
  'use strict';
  let enabled = false, sheet = null, target = null, lastMedia = null;
  const ancestors = [];
  function clear() {
    sheet?.remove(); sheet = null;
    target?.classList.remove('ani-pip-target'); target = null;
    ancestors.splice(0).forEach(node => node.classList.remove('ani-pip-ancestor'));
  }
  function present(node) {
    if (!enabled || !node) return;
    clear(); target = node; node.classList.add('ani-pip-target');
    for (let parent = node.parentElement; parent; parent = parent.parentElement) {
      parent.classList.add('ani-pip-ancestor'); ancestors.push(parent);
    }
    sheet = document.createElement('style');
    sheet.textContent = `html,body{background:#000!important;overflow:hidden!important}body *{visibility:hidden!important}.ani-pip-ancestor{transform:none!important;filter:none!important;perspective:none!important;contain:none!important;overflow:visible!important;position:static!important}.ani-pip-target{visibility:visible!important;display:block!important;position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;border:0!important;object-fit:contain!important;z-index:2147483647!important;background:#000!important}`;
    (document.head || document.documentElement).appendChild(sheet);
    if (window.parent !== window) window.parent.postMessage({aniPipVideo:true}, '*');
  }
  window.addEventListener('message', event => {
    if (!enabled || event.data?.aniPipVideo !== true) return;
    const frame = Array.from(document.querySelectorAll('iframe')).find(node => node.contentWindow === event.source);
    if (frame) present(frame);
  });
  browser.runtime.onMessage.addListener(message => {
    if (message.type === 'mediaCommand') {
      const candidates = Array.from(document.querySelectorAll('video,audio'));
      const media = target?.tagName === 'VIDEO' && target.isConnected ? target :
        candidates.find(node => !node.paused && !node.ended) ||
        (lastMedia?.isConnected ? lastMedia : null);
      if (!media) return;
      lastMedia = media;
      if (message.command === 'toggle') { if (media.paused) media.play().catch(()=>{}); else media.pause(); }
      if (message.command === 'back') media.currentTime = Math.max(0,media.currentTime-10);
      if (message.command === 'forward') media.currentTime = Math.min(Number.isFinite(media.duration)?media.duration:Infinity,media.currentTime+10);
      return;
    }
    if (message.type !== 'pipPresentation') return;
    enabled = message.enabled === true;
    clear();
    if (enabled) {
      const video = Array.from(document.querySelectorAll('video')).filter(node => !node.paused && !node.ended)
        .sort((a,b) => b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight)[0];
      present(video);
    }
  });
})();
