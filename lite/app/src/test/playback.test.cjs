const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname,'../main/assets/extensions/lite/playback.js'),'utf8');
const bgSource = fs.readFileSync(path.join(__dirname,'../main/assets/extensions/lite/background.js'),'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));
function player() {
  return {tagName:'VIDEO',isConnected:true,playbackRate:1,defaultPlaybackRate:1,paused:false,ended:false,currentTime:25,duration:100,
    pause(){this.paused=true;},play(){this.paused=false;return Promise.resolve();}};
}
function harness(initial=1.5, elements=[player()]) {
  const listeners = {}, sent = [];
  let messages;
  const document = {hidden:false,fullscreenElement:null,
    addEventListener(name,fn){(listeners[name]??=[]).push(fn);},querySelectorAll(){return elements;}};
  vm.runInNewContext(source,{document,performance:{now:()=>1000},browser:{runtime:{
    onMessage:{addListener(fn){messages=fn;}},sendMessage(message){sent.push(message);return Promise.resolve({type:'settings',speed:initial});}
  }}});
  return {elements,document,sent,event(name,target){for(const fn of listeners[name]||[]) fn({target});},message(m){messages(m);}};
}
test('restores saved speed before user playback and preserves pitch',async()=>{
  const h=harness(1.75); await tick(); const p=h.elements[0];
  assert.equal(p.playbackRate,1.75); assert.equal(p.defaultPlaybackRate,1.75); assert.equal(p.preservesPitch,true);
});
test('new and replaced episode players are discovered from media events',async()=>{
  const h=harness(2); await tick(); h.elements[0].isConnected=false;
  const next=player(); h.event('loadedmetadata',next); assert.equal(next.playbackRate,2);
  next.playbackRate=1;h.event('playing',next);assert.equal(next.playbackRate,2);
});
test('rate resets restore without recurring timers and hostile players are bounded',async()=>{
  const h=harness(1.5);await tick();const p=h.elements[0];
  p.playbackRate=1;h.event('ratechange',p);assert.equal(p.playbackRate,1.5);
  for(let i=0;i<10;i++){p.playbackRate=1;h.event('ratechange',p);}
  assert.equal(p.playbackRate,1);
  h.message({type:'settings',speed:2});assert.equal(p.playbackRate,2);
});
test('fresh native selection beats stale asynchronous initial settings',async()=>{
  const h=harness(1.25);h.message({type:'settings',speed:2});await tick();assert.equal(h.elements[0].playbackRate,2);
});
test('invalid speeds cannot mutate media',async()=>{
  const h=harness();await tick();for(const speed of [NaN,Infinity,-1,0,9,'2']) h.message({type:'settings',speed});
  assert.equal(h.elements[0].playbackRate,1.5);
});
test('native transport controls seek and toggle without changing site controls',async()=>{
  const h=harness();await tick();const p=h.elements[0];
  h.message({type:'command',command:'back'});assert.equal(p.currentTime,15);
  h.message({type:'command',command:'forward'});assert.equal(p.currentTime,25);
  h.message({type:'command',command:'toggle'});assert.equal(p.paused,true);
  h.message({type:'command',command:'toggle'});assert.equal(p.paused,false);
  h.document.hidden=true;h.event('visibilitychange');assert.equal(p.paused,true);
});
test('bare video fullscreen exposes native controls and restores prior state on exit',async()=>{
  const h=harness();await tick();const p=h.elements[0];p.controls=false;
  h.document.fullscreenElement=p;h.event('fullscreenchange');assert.equal(p.controls,true);
  h.document.fullscreenElement=null;h.event('fullscreenchange');assert.equal(p.controls,false);
});
test('custom fullscreen player container and its controls are not modified',async()=>{
  const h=harness();await tick();const container={tagName:'DIV',children:['video','seek','quality','subtitles']};
  h.document.fullscreenElement=container;h.event('fullscreenchange');
  assert.deepEqual(container,{tagName:'DIV',children:['video','seek','quality','subtitles']});
});
function background() {
  const events={}, native=[], broadcasts=[]; let receive;let allowed=true;
  const hook=name=>({addListener(fn){events[name]=fn;}});
  const browser={runtime:{onMessage:hook('message'),connectNative(){return {onDisconnect:hook('disconnect'),onMessage:{addListener(fn){receive=fn;}},postMessage(message){native.push(message);queueMicrotask(()=>receive({replyTo:message.requestId,result:message.type==='redirect'?{allowed}:{type:'settings',speed:1.75}}));}};}},
    tabs:{onRemoved:hook('removed'),onUpdated:hook('updated'),query:async()=>[{id:1}],sendMessage:async(id,message,options)=>{broadcasts.push({id,message,options});}},webRequest:{onBeforeRedirect:hook('redirect'),onBeforeRequest:hook('request'),onCompleted:hook('complete'),onErrorOccurred:hook('error')}};
  vm.runInNewContext(bgSource,{browser});
  return {events,native,broadcasts,receive(m){receive(m);},allow(value){allowed=value;}};
}
test('embedded frame settings use the top-level website, not media CDN',async()=>{
  const h=background();await h.events.message({type:'settings'},{tab:{id:1,url:'https://site.example/watch'},url:'https://cdn.example/embed'});
  assert.equal(h.native[0].url,'https://site.example/watch');
});
test('document-start settings use the new network URL even before tab URL is committed',async()=>{
  const h=background();
  await h.events.request({requestId:'load',tabId:1,url:'https://new.example/video'});
  await h.events.message({type:'settings'},{tab:{id:1,url:'about:blank'},url:'https://cdn.example/embed'});
  assert.equal(h.native[0].url,'https://new.example/video');
});
test('embedded player speed selection persists and broadcasts to all frames',async()=>{
  const h=background();await h.events.message({type:'saveSpeed',speed:1.75},{tab:{id:1,url:'https://site.example/watch'},url:'https://cdn.example/embed'});
  assert.equal(h.native[0].url,'https://site.example/watch');assert.equal(h.broadcasts.length,1);
});
test('HTTP redirect denial cancels original request and each new hop is checked',async()=>{
  const h=background();h.allow(false);
  h.events.redirect({requestId:'a',url:'https://one.example',redirectUrl:'https://two.example'});
  assert.equal((await h.events.request({requestId:'a',url:'https://two.example'})).cancel,true);
  h.allow(true);h.events.redirect({requestId:'a',url:'https://two.example',redirectUrl:'https://three.example'});
  assert.equal((await h.events.request({requestId:'a',url:'https://three.example'})).cancel,false);
  assert.equal(h.native.length,2);
});
test('ordinary documents and subresources trigger no redirect messaging',async()=>{
  const h=background();await h.events.request({requestId:'normal',url:'https://one.example'});assert.equal(h.native.length,0);
});
test('redirect bookkeeping is released after completion or network error',async()=>{
  const h=background();for(const cleanup of ['complete','error']) {
    h.events.redirect({requestId:'a',url:'https://one.example',redirectUrl:'https://two.example'});h.events[cleanup]({requestId:'a'});
    await h.events.request({requestId:'a',url:'https://two.example'});
  }assert.equal(h.native.length,0);
});
test('transport commands target the playing embedded frame rather than every player',async()=>{
  const h=background();
  await h.events.message({type:'mediaState',hasMedia:true,playing:false,area:200000},{tab:{id:1},frameId:0});
  await h.events.message({type:'mediaState',hasMedia:true,playing:true,area:100000},{tab:{id:1},frameId:3});
  h.receive({type:'command',command:'toggle'});await tick();
  assert.equal(h.broadcasts.length,1);assert.equal(h.broadcasts[0].options.frameId,3);
  h.receive({type:'command',command:'pause'});await tick();assert.equal(h.broadcasts[1].options,undefined);
});
