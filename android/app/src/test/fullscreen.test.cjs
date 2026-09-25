const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const code=fs.readFileSync(path.join(__dirname,'../main/assets/extensions/anibrowser/fullscreen.js'),'utf8');
function fixture(){
 const events={},win={};let mutation;
 const document={fullscreenElement:null,addEventListener:(n,f)=>events[n]=f};
 vm.runInNewContext(code,{document,window:{addEventListener:(n,f)=>win[n]=f},MutationObserver:class{constructor(f){mutation=f;}observe(){}disconnect(){}}});
 return {enter(root){document.fullscreenElement=root;events.fullscreenchange();},mutate(){mutation();},hide(){win.pagehide();}};
}
const video=(controls=false)=>({tagName:'VIDEO',controls});
const player=(v,custom=false)=>({tagName:'DIV',videos:[v],custom,querySelectorAll(){return this.videos;},querySelector(){return this.custom?{}:null;}});
test('bare fullscreen video gets browser controls and restores original state on exit',()=>{
 const f=fixture(),v=video();f.enter(v);assert.equal(v.controls,true);f.enter(null);assert.equal(v.controls,false);
});
test('native controls already enabled remain enabled after exit',()=>{
 const f=fixture(),v=video(true);f.enter(v);f.enter(null);assert.equal(v.controls,true);
});
test('site fullscreen controls including auto-hidden controls stay untouched',()=>{
 const f=fixture(),v=video(),root=player(v,true);f.enter(root);assert.equal(v.controls,false);
});
test('control-less fullscreen container receives fallback and yields to late site controls',()=>{
 const f=fixture(),v=video(),root=player(v);f.enter(root);assert.equal(v.controls,true);root.custom=true;f.mutate();assert.equal(v.controls,false);
});
test('replacement video gets fallback and previous video is restored',()=>{
 const f=fixture(),a=video(),b=video(),root=player(a);f.enter(root);root.videos=[b];f.mutate();assert.equal(a.controls,false);assert.equal(b.controls,true);f.hide();assert.equal(b.controls,false);
});

test('a lone play button does not suppress seek-capable fallback controls',()=>{
 const f=fixture(),v=video(),root=player(v);
 root.querySelector=selector=>selector.split(',').some(s=>s.trim()==='button'||s.trim()==='[role="button"]')?{}:null;
 f.enter(root);assert.equal(v.controls,true);
});
