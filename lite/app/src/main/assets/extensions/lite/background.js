/* SPDX-License-Identifier: MPL-2.0 */
"use strict";
// Persistence is native, keyed by the TOP-LEVEL website, including embedded players.
const port = browser.runtime.connectNative("anilite");
const pending = new Map();
let sequence = 0;
function native(message) {
  return new Promise((resolve, reject) => {
    const requestId = String(++sequence);
    pending.set(requestId, {resolve, reject});
    try { port.postMessage({...message, requestId}); }
    catch (error) { pending.delete(requestId); reject(error); }
  });
}
port.onDisconnect.addListener(() => {
  for (const request of pending.values()) request.reject(new Error("Browser connection closed"));
  pending.clear();
});
const players = new Map();
const topUrls = new Map();
function siteUrl(sender) {
  return topUrls.get(sender.tab?.id) || (sender.tab?.url?.startsWith("http") ? sender.tab.url : sender.url);
}
browser.runtime.onMessage.addListener(async (message, sender) => {
  if (message.type === "mediaState" && sender.tab) {
    let frames = players.get(sender.tab.id);
    if (!frames) { frames = new Map(); players.set(sender.tab.id, frames); }
    if (!message.hasMedia) frames.delete(sender.frameId);
    else frames.set(sender.frameId, {playing: message.playing === true, area: message.area || 0, time: Date.now()});
    return;
  }
  if (message.type === "settings") return native({type: "settings", url: siteUrl(sender)});
  if (message.type === "saveSpeed") {
    const result = await native({type: "saveSpeed", url: siteUrl(sender), speed: message.speed});
    if (sender.tab) await browser.tabs.sendMessage(sender.tab.id, result).catch(() => {});
    return result;
  }
});
// Gecko's navigation delegate handles normal loads. This narrow listener covers
// HTTP redirect hops without cancelling/replaying requests or losing POST bodies.
const redirects = new Map();
browser.webRequest.onBeforeRedirect.addListener(details => {
  redirects.set(details.requestId, {source: details.url, destination: details.redirectUrl});
}, {urls: ["<all_urls>"], types: ["main_frame"]});
browser.webRequest.onBeforeRequest.addListener(async details => {
  const hop = redirects.get(details.requestId);
  if (!hop || hop.destination !== details.url) {
    if (details.tabId >= 0) topUrls.set(details.tabId, details.url);
    return {};
  }
  redirects.delete(details.requestId);
  try {
    const result = await native({type: "redirect", source: hop.source, destination: details.url});
    if (result.allowed === true && details.tabId >= 0) topUrls.set(details.tabId, details.url);
    return {cancel: result.allowed !== true};
  } catch (_) { return {cancel: true}; }
}, {urls: ["<all_urls>"], types: ["main_frame"]}, ["blocking"]);
const cleanup = details => redirects.delete(details.requestId);
browser.webRequest.onCompleted.addListener(cleanup, {urls: ["<all_urls>"], types: ["main_frame"]});
browser.webRequest.onErrorOccurred.addListener(cleanup, {urls: ["<all_urls>"], types: ["main_frame"]});
browser.tabs.onRemoved.addListener(id => { players.delete(id); topUrls.delete(id); });
browser.tabs.onUpdated.addListener((id, change) => { if (change.status === "loading") players.delete(id); });
port.onMessage.addListener(message => {
  if (message.replyTo) {
    const request = pending.get(message.replyTo); pending.delete(message.replyTo);
    if (request) { if (message.error) request.reject(new Error(message.error)); else request.resolve(message.result); }
    return;
  }
  browser.tabs.query({active: true}).then(tabs => Promise.all(tabs.map(tab => {
    if (message.type !== "command" || message.command === "pause")
      return browser.tabs.sendMessage(tab.id, message).catch(() => {});
    const frames = [...(players.get(tab.id) || new Map())];
    frames.sort((a,b) => Number(b[1].playing)-Number(a[1].playing) || b[1].area-a[1].area || b[1].time-a[1].time);
    if (frames.length) return browser.tabs.sendMessage(tab.id, message, {frameId: frames[0][0]}).catch(() => {});
  }))).catch(() => {});
});
