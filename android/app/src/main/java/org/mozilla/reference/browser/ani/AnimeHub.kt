/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context

/** Lightweight responsive launcher markup rendered by [AnimeHubView]. */
object AnimeHub {
    fun getHtml(context: Context): String {
        val tiles = AniHomeManager.getTiles(context)
        return """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><title>AniBrowser Home</title>
<style>
:root {
  color-scheme: dark;
  --bg: #060913;
  --surface: rgba(255, 255, 255, 0.08);
  --line: rgba(255, 255, 255, 0.22);
  --text: #f8fafc;
  --muted: #94a3b8;
  --cyan: #38bdf8;
  --accent: #ff6b35;
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; }
html {
  height: 100%;
  background: #080c18;
}
body {
  margin: 0;
  min-height: 100vh;
  min-height: 100dvh;
  width: 100%;
  box-sizing: border-box;
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  overflow-x: hidden;
  padding: clamp(14px, 2.5vw, 28px) clamp(14px, 3.2vw, 36px) 36px;
  background-color: #080c18;
  background-image:
    radial-gradient(circle 900px at 10% 12%, rgba(59, 130, 246, 0.55) 0%, transparent 55%),
    radial-gradient(circle 800px at 90% 15%, rgba(217, 70, 239, 0.52) 0%, transparent 55%),
    radial-gradient(circle 950px at 50% 50%, rgba(14, 165, 233, 0.42) 0%, transparent 60%),
    radial-gradient(circle 850px at 85% 85%, rgba(244, 63, 94, 0.48) 0%, transparent 55%),
    radial-gradient(circle 850px at 15% 85%, rgba(16, 185, 129, 0.42) 0%, transparent 55%),
    linear-gradient(180deg, #0e1628 0%, #060912 100%);
  background-attachment: fixed;
  background-size: cover;
  background-repeat: no-repeat;
}
button, input { font: inherit; }
button { border: 0; color: inherit; }
.shell { width: 100%; max-width: 1400px; margin: auto; }

/* Apple Liquid Glass Top Bar */
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 8px 16px 8px 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0.04) 50%, rgba(255, 255, 255, 0.07) 100%);
  backdrop-filter: blur(36px) saturate(210%) brightness(112%);
  -webkit-backdrop-filter: blur(36px) saturate(210%) brightness(112%);
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 26px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45), inset 0 1.5px 1px rgba(255, 255, 255, 0.45), inset 0 -1px 1px rgba(0, 0, 0, 0.25);
}
.brand { display: flex; align-items: center; gap: 12px; min-width: 0; }
.brand img { width: 42px; height: 42px; object-fit: contain; filter: drop-shadow(0 4px 12px rgba(0,0,0,0.4)); }
.brand-copy { min-width: 0; }
.brand-name { font-size: 20px; font-weight: 800; letter-spacing: -0.4px; color: #ffffff; text-shadow: 0 2px 8px rgba(0,0,0,0.3); }
.spacer { flex: 1; }

.actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.icon-btn, .shield {
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0.04) 100%);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25), inset 0 1px 1px rgba(255, 255, 255, 0.35);
  transition: all 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}
.icon-btn { width: 42px; padding: 0; color: #f1f5f9; }
.icon-btn:active, .shield:active {
  transform: scale(0.93);
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.45);
}
.icon-btn svg, .shield svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.1;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.shield {
  gap: 8px;
  padding: 0 14px;
  color: #34d399;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.24) 0%, rgba(16, 185, 129, 0.08) 100%);
  border-color: rgba(52, 211, 153, 0.45);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}
.shield.off {
  color: #94a3b8;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.02) 100%);
  border-color: rgba(255, 255, 255, 0.16);
}

/* Grid & Cards - Apple Liquid Glass */
.grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 22px;
  margin-top: 26px;
}
.tile {
  position: relative;
  min-width: 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1.62 / 1;
  padding: 12px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0.035) 45%, rgba(255, 255, 255, 0.065) 100%);
  backdrop-filter: blur(34px) saturate(210%) brightness(114%);
  -webkit-backdrop-filter: blur(34px) saturate(210%) brightness(114%);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.42), inset 0 1.5px 1px rgba(255, 255, 255, 0.42), inset 0 -1px 1px rgba(0, 0, 0, 0.22);
  transition: transform 0.24s cubic-bezier(0.16, 1, 0.3, 1), background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}
.card::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.24) 0%, rgba(255, 255, 255, 0.04) 40%, transparent 65%);
  pointer-events: none;
  border-radius: 24px;
}
.tile:active .card {
  transform: scale(0.95);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0.08) 100%);
  border-color: rgba(255, 255, 255, 0.45);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.5), inset 0 1.5px 1px rgba(255, 255, 255, 0.55);
}
.mark {
  position: relative;
  z-index: 1;
  width: 68px;
  height: 68px;
  padding: 10px;
  background: #ffffff;
  border-radius: 19px;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  transition: transform 0.2s ease;
}
.mark img {
  width: 100%!important;
  height: 100%!important;
  object-fit: contain!important;
}
.mark:has(img.wide) {
  width: 82%!important;
  height: 72px!important;
  background: transparent!important;
  box-shadow: none!important;
}
.monogram {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
}
.tile-name {
  width: 100%;
  margin-top: 9px;
  color: #f1f5f9;
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.2;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.15px;
  text-shadow: 0 2px 6px rgba(0,0,0,0.4);
}
.tile-host { display: none!important; }

.delete {
  display: none;
  position: absolute;
  right: -5px;
  top: -5px;
  z-index: 4;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #ef4444;
  border: 2px solid #060913;
  place-items: center;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.4);
}
.edit .delete { display: grid; }
.delete svg { width: 14px; height: 14px; stroke: white; stroke-width: 2.2; }

/* Modals with Apple Liquid Glass */
.overlay {
  display: none;
  position: fixed;
  inset: 0;
  z-index: 50;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: rgba(2, 6, 18, 0.78);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}
.overlay.show { display: flex; }
.modal {
  width: min(500px, 100%);
  max-height: min(700px, 88vh);
  overflow-y: auto;
  padding: 26px;
  background: linear-gradient(135deg, rgba(22, 32, 54, 0.92) 0%, rgba(15, 23, 42, 0.94) 100%);
  backdrop-filter: blur(40px) saturate(200%);
  -webkit-backdrop-filter: blur(40px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 28px;
  box-shadow: 0 32px 70px rgba(0, 0, 0, 0.7), inset 0 1.5px 1px rgba(255, 255, 255, 0.25);
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}
.modal-head-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.modal-head svg { width: 22px; height: 22px; stroke: var(--cyan); fill: none; stroke-width: 2; }
.modal h2 { font-size: 20px; font-weight: 800; margin: 0; color: #fff; }
.modal p { font-size: 13px; color: var(--muted); margin: 0 0 18px; }

/* Sleek Icon-only Refresh Button in Modal */
.refresh-btn {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.14);
  border: 1px solid rgba(56, 189, 248, 0.35);
}
.refresh-btn:active { background: rgba(56, 189, 248, 0.25); }
.refresh-btn svg { width: 18px; height: 18px; stroke-width: 2.2; }
@keyframes spin { 100% { transform: rotate(360deg); } }
.refresh-btn.spinning svg { animation: spin 0.75s linear infinite; }

label { display: block; color: #cbd5e1; font-size: 12px; font-weight: 650; margin: 14px 0 6px; }
.input {
  width: 100%;
  height: 48px;
  border-radius: 15px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(11, 17, 29, 0.85);
  color: var(--text);
  padding: 0 15px;
  outline: 0;
  user-select: text;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.input:focus { border-color: var(--cyan); box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.2); }
.buttons { display: flex; gap: 10px; margin-top: 22px; }
.btn {
  flex: 1;
  min-height: 46px;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.18);
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}
.btn:active { transform: scale(0.97); }
.primary { background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%); color: #fff; border: 0; box-shadow: 0 6px 20px rgba(37, 99, 235, 0.35); }
.add { background: linear-gradient(135deg, #059669 0%, #047857 100%); color: #fff; border: 0; box-shadow: 0 6px 20px rgba(5, 150, 105, 0.35); }

.manage { display: flex; flex-direction: column; gap: 10px; }
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 17px;
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.row-mark {
  width: 40px;
  height: 40px;
  padding: 6px;
  display: grid;
  place-items: center;
  flex: none;
  background: white;
  color: #152033;
  border-radius: 12px;
}
.row-mark img { max-width: 100%; max-height: 100%; object-fit: contain; }
.row-copy { min-width: 0; flex: 1; }
.row-copy input {
  width: 100%;
  padding: 3px 5px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 7px;
  color: white;
  font-weight: 650;
}
.row-copy input:focus { border-color: var(--cyan); outline: 0; background: rgba(0,0,0,0.3); }
.url { display: block; padding: 2px 5px; color: var(--muted); font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.remove {
  height: 36px;
  padding: 0 12px;
  border-radius: 11px;
  background: rgba(239, 68, 68, 0.18);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: #f87171;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.remove:active { transform: scale(0.94); background: rgba(239, 68, 68, 0.32); }

.search-modal { align-items: flex-start; padding-top: max(24px, 8vh); }
.searchbox {
  display: flex;
  align-items: center;
  gap: 12px;
  width: min(640px, 100%);
  padding: 10px 18px;
  background: linear-gradient(135deg, rgba(22, 32, 54, 0.94) 0%, rgba(15, 23, 42, 0.96) 100%);
  backdrop-filter: blur(36px);
  -webkit-backdrop-filter: blur(36px);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 22px;
  box-shadow: 0 28px 65px rgba(0, 0, 0, 0.7);
}
.searchbox svg { width: 22px; height: 22px; stroke: var(--cyan); fill: none; flex-shrink: 0; }
.searchbox input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: white;
  font-size: 17px;
  padding: 9px 0;
  user-select: text;
}
.empty { text-align: center; color: var(--muted); padding: 25px; font-size: 14px; }

/* Responsive Media Queries */
@media (max-width: 1024px) {
  .grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 720px) {
  .grid { grid-template-columns: repeat(3, minmax(0, 1fr)); column-gap: 12px; row-gap: 16px; }
  .mark { width: 58px; height: 58px; }
  .card { border-radius: 18px; }
}
@media (max-width: 580px) {
  body { padding: 10px 10px 24px; }
  .topbar { min-height: 52px; padding: 7px 10px; border-radius: 20px; gap: 8px; }
  .brand img { width: 34px; height: 34px; }
  .brand-name { font-size: 16px; }
  .brand-sub { display: none; }
  .actions { gap: 6px; }
  .shield { height: 36px; padding: 0 10px; font-size: 11.5px; border-radius: 12px; }
  .icon-btn { width: 36px; height: 36px; border-radius: 12px; }
  .icon-btn svg, .shield svg { width: 17px; height: 17px; }
  .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 10px; row-gap: 14px; margin-top: 18px; }
  .card { aspect-ratio: 1.52 / 1; border-radius: 18px; padding: 10px; }
  .mark { width: 56px; height: 56px; border-radius: 15px; padding: 8px; }
  .tile-name { font-size: 12.5px; margin-top: 6px; }
  .modal { padding: 20px; border-radius: 22px; }
  .overlay { align-items: flex-end; padding: 10px; }
  .search-modal { align-items: flex-start; padding-top: 14px; }
  .remove span { display: none; }
}
@media (max-width: 360px) {
  .shield span { display: none; }
  .shield { padding: 0 10px; }
  .brand-name { font-size: 14px; }
}
@media (prefers-reduced-motion: reduce) { * { transition: none!important; } }

/* Shared glass finish; full-bleed shortcut surfaces. */
body { background: transparent; padding-bottom: 160px; }
.wallpaper { position: fixed; inset: 0; z-index: -1; pointer-events: none;
 background: radial-gradient(ellipse at 10% 10%, #508ca8, transparent 65%), radial-gradient(ellipse at 95% 60%, #946caa, transparent 65%), linear-gradient(135deg,#243f69,#457b82); }
.wallpaper-photo { position:absolute; inset:0; background-position:center; background-size:cover; }
.wallpaper::after { content:''; position:absolute; inset:0; background:linear-gradient(180deg,rgba(9,20,37,.23),rgba(9,20,37,.04) 55%,rgba(9,20,37,.28)); }
html { isolation:isolate; }
.topbar { border-radius:32px; background:linear-gradient(125deg,rgba(233,245,255,.26),rgba(211,232,252,.09)); box-shadow:0 10px 32px #06152830,inset 0 1px 1px #ffffff88,inset 0 -1px 1px #ffffff20; }
.brand-name { font-weight:650; letter-spacing:-.6px; }
.icon-btn { border-radius:50%; width:44px; height:44px; background:linear-gradient(135deg,#203a5855,#162c4533); }
.grid { max-width:1120px; margin:30px auto 0; gap:24px 20px; }
.card { backdrop-filter:blur(24px) saturate(140%); padding:0; border-radius:26px; background:#263b57; box-shadow:0 10px 24px #06152835,inset 0 1px 1px #ffffff80; }
.card::after { z-index:2; border-radius:inherit; background:linear-gradient(130deg,#ffffff1a,transparent 45%); box-shadow:inset 0 1px 1px #ffffff55; }
.mark { position:absolute; inset:0; width:100%; height:100%; padding:6%; background:transparent; border-radius:inherit; box-shadow:none; }
.mark img { display:block; width:100%!important; height:100%!important; object-fit:contain!important; }
.mark:has(img.wide) { width:100%!important; height:100%!important; }
.monogram { border-radius:0; font-size:clamp(32px,5vw,64px); background:transparent; }
.tile-name { font-weight:600; text-shadow:0 1px 5px #000,0 2px 12px #0008; }
.overlay { background:#06102055; }
.modal,.searchbox { background:linear-gradient(130deg,rgba(62,82,108,.86),rgba(25,42,64,.9)); border-radius:32px; box-shadow:0 28px 64px #0005,inset 0 1px 1px #ffffff77; }
.btn,.input { border-radius:20px; }
.primary,.add { background:linear-gradient(135deg,#70b7ff,#427fec); box-shadow:inset 0 1px 1px #ffffff66,0 5px 18px #153c7330; }
.wallpaper-credit { position:fixed; bottom:92px; right:16px; max-width:calc(100% - 32px); border-radius:18px; background:#10213965; backdrop-filter:blur(20px); padding:8px 12px; font-size:11px; color:#fff; text-shadow:0 1px 3px #000; text-align:right; }
.wallpaper-credit,.wallpaper-credit[hidden] { display:none!important; }
button:focus-visible,.tile:focus-visible { outline:3px solid #bce2ff; outline-offset:4px; }
@media(max-width:580px) {
 .topbar { flex-wrap:wrap; padding:10px 12px; border-radius:28px; }
 .brand { flex:1; } .spacer { display:none; }
 .actions { gap:6px; } .shield { display:none; }
 .icon-btn { width:44px; height:44px; }
 .grid { gap:18px 12px; margin-top:24px; }
 .card { border-radius:24px; padding:0; }
 .mark { width:100%; height:100%; padding:6%; border-radius:inherit; }
 .wallpaper-credit { font-size:10px; padding:8px 12px; }
}
@media(max-width:390px) { .brand-name { font-size:15px; } .brand img { width:26px; height:26px; } .brand {gap:5px;} .actions {gap:2px;} }

.refresh-btn { flex:none; color:#e6f6ff; }
.refresh-btn svg { fill:none; stroke:currentColor; transform-origin:center; }
.row .remove,.row .rename { width:44px; height:44px; flex:none; padding:0; border-radius:15px; color:#e6f6ff; background:#ffffff12; border:1px solid #ffffff30; }
.row .remove svg { width:20px; height:20px; }
.row .remove:active { background:#e18b8b44; }
.row-copy input { min-height:36px; }
@media(max-width:580px) { .row { gap:7px; padding:8px; } .row-mark { width:30px;height:30px;padding:3px; } }
</style></head><body><div class="wallpaper"><div class="wallpaper-photo" style="background-image:url('${DailyWallpaper.file(context)?.let { "https://anibrowser.local/wallpaper/" + it.name }.orEmpty()}')"></div></div><div class="shell">
<header class="topbar">
  <div class="brand">
    <img src="https://anibrowser.local/assets/logo.png" alt="">
    <div class="brand-copy">
      <div class="brand-name">AniBrowser</div>
    </div>
  </div>
  <div class="spacer"></div>
  <div class="actions">
    ${iconButton("openSearch()", "Search", "<circle cx='10.5' cy='10.5' r='7.5'/><path d='m16 16 5 5'/>")}
    ${iconButton("openAdd()", "Add website", "<path d='M12 5v14M5 12h14'/>")}
    ${iconButton("openManage()", "Manage websites", "<path d='m4 20 4.2-1 11-11a2.8 2.8 0 0 0-4-4l-11 11L4 20Z'/><path d='m13.8 5.4 4 4'/>")}
    ${iconButton("openSettings()", "Settings", "<path d='M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54A.484.484 0 0 0 13.92 2.4h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.73 8.87a.49.49 0 0 0 .12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z'/>")}
  </div>
</header>
<main class="grid">${buildTilesHtml(tiles)}</main><div id="wallpaperCredit" class="wallpaper-credit" hidden></div>
</div>
${addModal()}${manageModal(tiles)}
<div id="search" class="overlay search-modal" onclick="backdrop(event,'search')">
  <div class="searchbox">
    <svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/></svg>
    <input id="q" autocomplete="off" placeholder="Search with DuckDuckGo or enter URL" onkeydown="if(event.key==='Enter')submitSearch()">
  </div>
</div>
<script>
const byId = id => document.getElementById(id),
      show = id => byId(id).classList.add('show'),
      hide = id => byId(id).classList.remove('show');

function setWallpaper(state) {
 const photo = document.querySelector('.wallpaper-photo'), credit = byId('wallpaperCredit');
 if (!state.url || photo.dataset.url === state.url) return;
 const image = new Image();
 image.onload = () => { photo.style.backgroundImage = 'url("' + state.url + '")'; photo.dataset.url = state.url; credit.textContent = 'Bing · ' + state.credit; credit.hidden = false; };
 image.src = state.url;
}
function fillTile(img) {
 if (img.dataset.ready || img.dataset.queued) return;
 img.dataset.queued = '1';
 const run = () => {
  delete img.dataset.queued;
  if (img.isConnected && !document.hidden) renderTile(img);
 };
 if (window.requestIdleCallback) requestIdleCallback(run, {timeout:3000});
 else setTimeout(run, 500);
}
function renderTile(img) {
 if (img.dataset.ready) return;
 img.dataset.ready = '1';
 try {
  const canvas = document.createElement('canvas'); canvas.width = 96; canvas.height = 96;
  const ctx = canvas.getContext('2d',{willReadFrequently:true}); ctx.drawImage(img,0,0,96,96);
  const data = ctx.getImageData(0,0,96,96).data, counts = new Map();
  for(let y=0;y<96;y++) for(let x=0;x<96;x++) {
   if(x>7&&x<88&&y>7&&y<88) continue;
   const i=(y*96+x)*4; if(data[i+3]<200) continue;
   const color=[data[i],data[i+1],data[i+2]].join(',');
   counts.set(color,(counts.get(color)||0)+1);
  }
  const bg = [...counts].sort((a,b)=>b[1]-a[1])[0];
  let light = 0, visible = 0;
  for(let i=0;i<data.length;i+=4) if(data[i+3]>128) { light += .2126*data[i]+.7152*data[i+1]+.0722*data[i+2]; visible++; }
  const fallback = visible && light/visible > 190 ? 'rgba(28,39,55,.62)' : 'rgba(255,255,255,.58)';
  img.closest('.card').style.backgroundColor = bg ? 'rgba('+bg[0]+',.62)' : fallback;
  if(bg && bg[1]>150) {
   const rgb=bg[0].split(',').map(Number), full=document.createElement('canvas');
   const scale=Math.min(1,256/Math.max(img.naturalWidth,img.naturalHeight));
   full.width=Math.max(1,Math.round(img.naturalWidth*scale)); full.height=Math.max(1,Math.round(img.naturalHeight*scale));
   const fc=full.getContext('2d');fc.drawImage(img,0,0,full.width,full.height);
   const pixels=fc.getImageData(0,0,full.width,full.height), seen=new Uint8Array(full.width*full.height), queue=[];
   const visit=i=>{if(i<0||i>=seen.length||seen[i])return;seen[i]=1;const j=i*4;
    if(pixels.data[j+3]<30||Math.max(Math.abs(rgb[0]-pixels.data[j]),Math.abs(rgb[1]-pixels.data[j+1]),Math.abs(rgb[2]-pixels.data[j+2]))<24){pixels.data[j+3]=0;queue.push(i);}};
   for(let x=0;x<full.width;x++){visit(x);visit((full.height-1)*full.width+x);}
   for(let y=0;y<full.height;y++){visit(y*full.width);visit(y*full.width+full.width-1);}
   for(let n=0;n<queue.length;n++){const i=queue[n];if(i%full.width)visit(i-1);if(i%full.width<full.width-1)visit(i+1);visit(i-full.width);visit(i+full.width);}
   fc.putImageData(pixels,0,0);img.src=full.toDataURL('image/png');
  }
 } catch (_) { /* Retain the glass fallback when no image pixels are available. */ }
}

function backdrop(e, id) { if (e.target === byId(id)) hide(id); }
function launch(url) { if (window.AniHomeBridge) AniHomeBridge.openUrl(url); }
function openSearch() { show('search'); setTimeout(() => byId('q').focus(), 80); }
function openAdd() { show('addModal'); setTimeout(() => byId('siteUrl').focus(), 80); }
function openManage() { show('manageModal'); }
function openSettings() { if (window.AniHomeBridge) AniHomeBridge.openSettings(); }
function submitSearch() {
  let q = byId('q').value.trim();
  if (!q) return;
  hide('search');
  launch(/^https?:\/\//i.test(q) ? q : (q.includes('.') && !q.includes(' ') ? 'https://' + q : AniHomeBridge.searchUrl(q)));
}
function addSite() {
  const u = byId('siteUrl').value.trim(), n = byId('siteName').value.trim();
  if (!u) { byId('siteUrl').focus(); return; }
  hide('addModal');
  AniHomeBridge.addTile(u, n);
}
function removeSite(e, id) {
  if (e) e.stopPropagation();
  const row = (e && e.target) ? e.target.closest('.row') : document.querySelector('.row[data-id="' + id + '"]');
  if (row) row.remove();
  const tile = document.querySelector('.tile[data-id="' + id + '"]');
  if (tile) tile.remove();
  const manageDiv = document.querySelector('.manage');
  if (manageDiv && manageDiv.querySelectorAll('.row').length === 0) {
    manageDiv.innerHTML = '<div class="empty">No sites added yet.</div>';
  }
  if (window.AniHomeBridge) AniHomeBridge.deleteTile(id);
}
function renameSite(id, v) {
  if (v.trim()) {
    AniHomeBridge.updateTile(id, v.trim());
    document.querySelector('.tile[data-id="'+id+'"] .tile-name').textContent=v.trim();
  }
}
function beginRename(button) {
 const input = button.closest('.row').querySelector('input'); input.focus(); input.select();
}
function finishRefresh(count) {
 const btn=document.querySelector('.refresh-btn'); btn.classList.remove('spinning'); btn.disabled=false;
 document.querySelectorAll('.mark img,.row-mark img').forEach(img=>{delete img.dataset.ready;img.src=img.src.split('?')[0]+'?v='+Date.now();});
 byId('refreshStatus').textContent=count ? 'Website logos updated.' : 'Could not refresh logos. Your saved logos are kept.';
}
function triggerRefresh(btn) {
  if (btn.classList.contains('spinning')) return;
  btn.classList.add('spinning');
  if (window.AniHomeBridge) AniHomeBridge.refreshLogos();
  btn.disabled = true;
}
document.addEventListener('DOMContentLoaded', () => { document.querySelectorAll('.mark img').forEach(img => { if(img.complete && img.naturalWidth) fillTile(img); }); });
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') document.querySelectorAll('.overlay.show').forEach(x => x.classList.remove('show'));
});
</script></body></html>"""
    }

    internal fun buildTilesHtml(tiles: List<AniHomeTile>) = tiles.joinToString("") { tile ->
        val icon = if (tile.iconPath != null) {
            "<img src=\"https://anibrowser.local/icon/${tile.id}.png\" alt=\"\" onload=\"if(window.fillTile)fillTile(this)\" onerror=\"this.style.display='none';this.parentElement.textContent=this.closest('.tile').querySelector('.tile-name').textContent.slice(0,2)\">"
        } else {
            "<span class=\"monogram\">${escapeHtml(tile.title.take(2).uppercase())}</span>"
        }
        """<article class="tile" tabindex="0" role="button" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();this.click()}" data-id="${tile.id}" data-url="${escapeHtml(tile.url)}" onclick="launch(this.dataset.url)"><button class="delete" onclick="removeSite(event,'${tile.id}')" aria-label="Remove ${escapeHtml(tile.title)}"><svg viewBox="0 0 24 24"><path d="m7 7 10 10M17 7 7 17"/></svg></button><div class="card"><div class="mark">$icon</div></div><div class="tile-name">${escapeHtml(tile.title)}</div></article>"""
    }

    private fun manageModal(tiles: List<AniHomeTile>): String {
        val rows = if (tiles.isEmpty()) {
            "<div class=\"empty\">No sites added yet.</div>"
        } else {
            tiles.joinToString("") { tile ->
                val icon = if (tile.iconPath != null) {
                    "<img src=\"https://anibrowser.local/icon/${tile.id}.png\" alt=\"\">"
                } else {
                    "<b>${escapeHtml(tile.title.take(2).uppercase())}</b>"
                }
                """<div class="row" data-id="${tile.id}"><div class="row-mark">$icon</div><div class="row-copy"><input value="${escapeHtml(tile.title)}" onchange="renameSite('${tile.id}',this.value)"><span class="url">${escapeHtml(tile.url)}</span></div><button class="icon-btn rename" onclick="beginRename(this)" aria-label="Rename ${escapeHtml(tile.title)}"><svg viewBox="0 0 24 24"><path d="m4 20 4-1L20 7a2.8 2.8 0 0 0-4-4L4 15Z"/><path d="m14 5 5 5"/></svg></button><button class="icon-btn remove" aria-label="Delete ${escapeHtml(tile.title)}" onclick="removeSite(event,'${tile.id}')"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13"/></svg></button></div>"""
            }
        }
        return """<div id="manageModal" class="overlay" onclick="backdrop(event,'manageModal')"><section class="modal"><div class="modal-head"><div class="modal-head-title"><svg viewBox="0 0 24 24"><path d="m4 20 4.2-1 11-11a2.8 2.8 0 0 0-4-4l-11 11L4 20Z"/><path d="m13.8 5.4 4 4"/></svg><h2>Manage sites</h2></div><button class="icon-btn refresh-btn" onclick="triggerRefresh(this)" title="Refresh website logos" aria-label="Refresh website logos"><svg viewBox="0 0 24 24"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg></button></div><p id="refreshStatus" role="status">Rename or remove shortcuts from Home.</p><div class="manage">$rows</div><div class="buttons"><button class="btn primary" onclick="hide('manageModal')">Done</button></div></section></div>"""
    }

    private fun addModal() = """<div id="addModal" class="overlay" onclick="backdrop(event,'addModal')"><section class="modal"><div class="modal-head"><div class="modal-head-title"><svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg><h2>Add website</h2></div></div><p>Save a site to AniBrowser Home. Its logo is cached locally.</p><label for="siteUrl">Website address</label><input id="siteUrl" class="input" type="url" placeholder="https://example.com"><label for="siteName">Name (optional)</label><input id="siteName" class="input" placeholder="Detected from the address"><div class="buttons"><button class="btn" onclick="hide('addModal')">Cancel</button><button class="btn add" onclick="addSite()">Add</button></div></section></div>"""

    private fun iconButton(action: String, label: String, path: String) = "<button class=\"icon-btn\" onclick=\"$action\" aria-label=\"$label\" title=\"$label\"><svg viewBox=\"0 0 24 24\">$path</svg></button>"
    private fun escapeHtml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
