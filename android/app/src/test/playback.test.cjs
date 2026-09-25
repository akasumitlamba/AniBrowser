/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../main/assets/extensions/anibrowser/playback.js'), 'utf8');

async function fixture(saved = 1.5) {
  const videos = [];
  let update, tick;
  const documentEvents={};
  class Document {get hidden(){return false;}}
  const document=Object.assign(new Document(),{querySelectorAll:()=>videos,addEventListener:(name,listener)=>documentEvents[name]=listener});
  const context = {
    document, Document,
    browser: {runtime: {onMessage: {addListener: callback => update = callback},
      sendMessage: async () => ({speed: saved})}},
    MutationObserver: class {constructor() { throw new Error('Playback must not scan DOM mutations'); }},
    queueMicrotask, performance, WeakSet, WeakMap, setTimeout,
    setInterval: callback => { tick = callback; return 1; }, clearInterval(){},
  };
  function add() {
    const listeners = {};
    const video = {tagName:'VIDEO',paused:false,ended:false,isConnected: true, playbackRate: 1, defaultPlaybackRate: 1,
      addEventListener: (event, callback) => listeners[event] = callback,
      fire: event => listeners[event]?.()};
    videos.push(video);
    return video;
  }
  const first = add();
  vm.runInNewContext(code, context);
  await new Promise(setImmediate);
  return {first, add, update: value => update({speed: value}), discover:media=>documentEvents.loadedmetadata({target:media}), tick: () => tick()};
}

test('restores the saved rate and preserves pitch on initial playback', async () => {
  const {first} = await fixture(1.75);
  assert.equal(first.playbackRate, 1.75);
  assert.equal(first.defaultPlaybackRate, 1.75);
  assert.equal(first.preservesPitch, true);
});
test('applies saved speed to a replacement episode player', async () => {
  const f = await fixture(2);
  f.first.isConnected = false;
  const next = f.add();
  f.discover(next);
  await new Promise(setImmediate);
  assert.equal(next.playbackRate, 2);
});
test('recovers from a player resetting speed', async () => {
  const {first} = await fixture();
  first.playbackRate = 1;
  first.fire('ratechange');
  await new Promise(resolve => setTimeout(resolve, 550));
  assert.equal(first.playbackRate, 1.5);
});
test('changes existing and future videos and rejects invalid rates', async () => {
  const f = await fixture();
  f.update(1.25);
  assert.equal(f.first.playbackRate, 1.25);
  f.update(99);
  const next = f.add();
  f.discover(next);
  f.tick();
  assert.equal(next.playbackRate, 1.25);
});
test('bounds work when a player repeatedly resets its rate', async () => {
  const {first} = await fixture();
  let writes = 0;
  Object.defineProperty(first, 'playbackRate', {get: () => 1, set: () => { writes++; }});
  for (let i = 0; i < 100; i++) first.fire('ratechange');
  await new Promise(resolve => setTimeout(resolve, 550));
  assert.ok(writes > 0 && writes <= 2, `unexpected ${writes} repeated writes`);
});

test('pitch preservation is restored even at the default playback rate', async () => {
  const {first} = await fixture(1);
  assert.equal(first.preservesPitch,true);
});

test('late startup replies never overwrite newer playback or background settings', async () => {
  let receive;const replies={};
  const media={tagName:'VIDEO',isConnected:true,paused:false,ended:false,playbackRate:1,defaultPlaybackRate:1,addEventListener(){},pause(){this.paused=true;}};
  class Document {get hidden(){return true;}}
  const document=Object.assign(new Document(),{querySelectorAll:()=>[media],addEventListener(){}});
  vm.runInNewContext(code,{document,Document,performance,setTimeout,setInterval:()=>1,clearInterval(){},
    browser:{runtime:{onMessage:{addListener:f=>receive=f},sendMessage:message=>new Promise(resolve=>replies[message.type]=resolve)}}});
  receive({speed:2});receive({type:'mediaPolicy',enabled:true,background:true});
  replies.speed({speed:1.25});replies.mediaPolicy({type:'mediaPolicy',enabled:false,background:true});
  await new Promise(setImmediate);
  assert.equal(media.playbackRate,2);assert.equal(media.paused,false);assert.equal(document.hidden,false);
});
