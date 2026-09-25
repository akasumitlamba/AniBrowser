/* SPDX-License-Identifier: MPL-2.0 */
"use strict";
const allowed = [1, 1.25, 1.5, 1.75, 2];
let speed = 1;
let nativePort;
let chromeInset = 0;
let mediaPreferences = {background:true, sites:{}};
let appInBackground = false;
function mediaPolicy(url) {
  let hostname = "";
  try { hostname = new URL(url).hostname.toLowerCase(); } catch (_) {}
  return {type:"mediaPolicy", enabled:mediaPreferences.background === true && mediaPreferences.sites[hostname] !== false, background:appInBackground};
}
function publishMedia() {
  browser.tabs.query({}).then(tabs => Promise.all(tabs.map(tab => browser.tabs.sendMessage(tab.id,mediaPolicy(tab.url)).catch(()=>{}))));
}
const pending = new Map();
const newWindows = new Set();
browser.tabs.onCreated.addListener(tab => {
  if (tab.openerTabId != null) newWindows.add(tab.id);
});
browser.tabs.onRemoved.addListener(id => newWindows.delete(id));
let sequence = 0;
function confirmNavigation(source, destination) {
  if (!nativePort) return Promise.resolve(false);
  return new Promise(resolve => {
    const id = String(++sequence);
    const timer = setTimeout(() => { pending.delete(id); resolve(false); }, 10000);
    pending.set(id, allowed => { clearTimeout(timer); pending.delete(id); resolve(allowed); });
    try { nativePort.postMessage({type: "navigation", id, source, destination}); }
    catch (_) { pending.get(id)?.(false); }
  });
}

const AD_DOMAINS = /(?:^|\.)(?:popads\.net|popcash\.net|adsterra\.com|propellerads\.com|exoclick\.com|exosrv\.com|tsyndicate\.com|trafficfactory\.biz|doubleclick\.net|googleadservices\.com|googlesyndication\.com|adservice\.google\.com|onclickads\.net|adtrue\.com|adnxs\.com|juicyads\.com|bet365\.com|1xbet\.com)\.?$/i;

function isAd(url) {
  try {
    const u = new URL(url);
    return AD_DOMAINS.test(u.hostname);
  } catch (_) { return false; }
}

// Built-in Ad Blocker: silently cancel requests to known ad/popunder networks
browser.webRequest.onBeforeRequest.addListener(details => {
  if (isAd(details.url)) return {cancel: true};
  return {};
}, {urls: [
  "*://*.popads.net/*", "*://*.popcash.net/*", "*://*.adsterra.com/*",
  "*://*.propellerads.com/*", "*://*.exoclick.com/*", "*://*.exosrv.com/*",
  "*://*.tsyndicate.com/*", "*://*.trafficfactory.biz/*", "*://*.doubleclick.net/*",
  "*://*.googleadservices.com/*", "*://*.googlesyndication.com/*",
  "*://*.adservice.google.com/*", "*://*.onclickads.net/*", "*://*.adtrue.com/*",
  "*://*.adnxs.com/*", "*://*.juicyads.com/*", "*://*.bet365.com/*", "*://*.1xbet.com/*"
], types: ["sub_frame", "script", "xmlhttprequest"]}, ["blocking"]);

browser.webRequest.onBeforeRequest.addListener(details => {
  if (details.tabId < 0) return {};
  if (isAd(details.url)) return {cancel: true};
  newWindows.delete(details.tabId);
  // Do not pause HTTP redirect chains for a popup preference. Mobile endpoints,
  // consent and login redirects must preserve their method/body and load normally.
  return {};
}, {urls: ["http://*/*", "https://*/*"], types: ["main_frame"]}, ["blocking"]);
const ready = browser.storage.local.get("speed").then(saved => {
  if (allowed.includes(saved.speed)) speed = saved.speed;
});
async function update(value) {
  await ready;
  if (!allowed.includes(value) || value === speed) return;
  speed = value;
  await browser.storage.local.set({speed});
  const tabs = await browser.tabs.query({});
  await Promise.all(tabs.map(tab => browser.tabs.sendMessage(tab.id, {speed}).catch(() => {})));
}
browser.runtime.onMessage.addListener(async (message, sender) => {
  await ready;
  if (message.type === "mediaPolicy") return mediaPolicy(sender?.tab?.url || sender?.url);
  if (message.type === "speed") return {speed};
  if (message.type === "chromeInset") return {bottom: chromeInset};
});
function connect() {
  nativePort = browser.runtime.connectNative("anibrowser");
  nativePort.onMessage.addListener(message => {
    if (message.type === "navigationResult") pending.get(message.id)?.(message.allowed === true);
    else if (message.type === "mediaPreferences") { mediaPreferences = {background:message.background === true, sites:message.sites || {}}; publishMedia(); }
    else if (message.type === "mediaCommand") {
      browser.tabs.query({active:true}).then(tabs => Promise.all(tabs.map(tab => browser.tabs.sendMessage(tab.id, {type:"mediaCommand", command:message.command}).catch(()=>{}))));
    }
    else if (message.type === "pipPresentation") {
      browser.tabs.query({active:true}).then(tabs => Promise.all(tabs.map(tab => browser.tabs.sendMessage(tab.id, {type:"pipPresentation", enabled:message.enabled === true}).catch(()=>{}))));
    }
    else if (message.type === "appVisibility") { appInBackground = message.background === true; publishMedia(); }
    else if (message.type === "chromeInset") {
      const next = Number.isFinite(message.bottom) ? Math.max(0, Math.min(120, message.bottom)) : 0;
      if (next === chromeInset) return;
      chromeInset = next;
      browser.tabs.query({}).then(tabs => Promise.all(tabs.map(tab => browser.tabs.sendMessage(tab.id, {type: "chromeInset", bottom: chromeInset}).catch(() => {}))));
    } else update(message.speed);
  });
  nativePort.onDisconnect.addListener(() => {
    nativePort = null;
    for (const resolve of [...pending.values()]) resolve(false);
    setTimeout(connect, 1000);
  });
  nativePort.postMessage({type: "ready"});
}
ready.then(connect);
