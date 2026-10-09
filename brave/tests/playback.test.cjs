const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/playback.js'),'utf8');
function fixture(){
  const listeners=new Map(), messages=[];
  const video={tagName:'VIDEO',isConnected:true,playbackRate:1,defaultPlaybackRate:1};
  const document={fullscreenElement:null,scrollingElement:{scrollTop:0},title:'Episode',
    querySelectorAll(selector){return selector==='video,audio'?[video]:[];},
    addEventListener(name,fn){const list=listeners.get(name)||[];list.push(fn);listeners.set(name,list);}};
  const window={addEventListener:(name,fn)=>document.addEventListener(name,fn)};
  const context=vm.createContext({document,window,location:{href:'https://example.com/episode'},Date,aniBraveNative:payload=>messages.push(JSON.parse(payload))});
  vm.runInContext(source,context);
  return {context,video,messages,document,fire:(name,event={target:video})=>(listeners.get(name)||[]).forEach(fn=>fn(event))};
}
test('saved speed applies immediately and survives player metadata/loading',()=>{
  const f=fixture();f.context.__aniBrave.configure(1.75);assert.equal(f.video.playbackRate,1.75);
  f.video.playbackRate=1;f.fire('loadedmetadata');assert.equal(f.video.playbackRate,1.75);
});
test('a replacement video receives the saved speed without a polling loop',()=>{
  const f=fixture();f.context.__aniBrave.configure(2);
  const replacement={tagName:'VIDEO',isConnected:true,playbackRate:1};f.fire('playing',{target:replacement});assert.equal(replacement.playbackRate,2);
});
test('rate rejection retries are bounded and an explicit new speed resets the bound',()=>{
  const f=fixture();f.context.__aniBrave.configure(2);
  for(let i=0;i<30;i++){f.video.playbackRate=1;f.fire('ratechange');}
  assert.equal(f.messages.filter(m=>m.type==='rateRejected').length,1);
  f.context.__aniBrave.configure(1.5);assert.equal(f.video.playbackRate,1.5);
});
test('controllers in separate frame contexts accept the same top-site setting',()=>{
  const top=fixture(), embedded=fixture();top.context.__aniBrave.configure(1.25);embedded.context.__aniBrave.configure(1.25);
  assert.equal(top.video.playbackRate,1.25);assert.equal(embedded.video.playbackRate,1.25);
});
test('fullscreen reports state without changing website controls or moving DOM',()=>{
  const f=fixture();f.video.controls=false;f.document.fullscreenElement={};f.fire('fullscreenchange');
  assert.equal(f.video.controls,false);assert.deepEqual(f.messages.at(-1),{type:'fullscreen',enabled:true});
});
test('scrolling and vertical gestures reveal tools without consuming touch events',()=>{
  const f=fixture();f.document.scrollingElement.scrollTop=70;f.fire('scroll',{target:f.document});
  f.fire('touchstart',{touches:[{clientY:100}]});f.fire('touchmove',{touches:[{clientY:50}]});
  assert.equal(f.messages.filter(m=>m.type==='reveal').length,2);
});
test('multiple installations do not register duplicate listeners',()=>{
  const f=fixture();vm.runInContext(source,f.context);f.document.fullscreenElement={};f.fire('fullscreenchange');assert.equal(f.messages.length,1);
});
test('invalid rates leave the active speed untouched',()=>{
  const f=fixture();f.context.__aniBrave.configure(1.5);f.context.__aniBrave.configure(10);assert.equal(f.video.playbackRate,1.5);
});
