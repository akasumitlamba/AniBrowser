const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const base=path.join(__dirname,'../main/assets/extensions/anibrowser');
async function backgroundFixture(){
 let native,message;const event=()=>({addListener(){}});
 const port={onMessage:{addListener:f=>native=f},onDisconnect:event(),postMessage(){}};
 vm.runInNewContext(fs.readFileSync(path.join(base,'background.js'),'utf8'),{URL,Map,Set,Promise,setTimeout,clearTimeout,browser:{storage:{local:{get:async()=>({}),set:async()=>{}}},tabs:{query:async()=>[],sendMessage:async()=>{},onCreated:event(),onRemoved:event()},runtime:{onMessage:{addListener:f=>message=f},connectNative:()=>port},webRequest:{onBeforeRequest:event(),onCompleted:event(),onErrorOccurred:event()}}});
 await new Promise(setImmediate);return {set:state=>native({type:'mediaPreferences',...state}),policy:url=>message({type:'mediaPolicy'},{tab:{url}})};
}
test('global background switch dominates site choices',async()=>{
 const f=await backgroundFixture();
 for(const global of [false,true])for(const site of [false,true]){
  f.set({background:global,sites:{'video.example':site}});
  assert.equal((await f.policy('https://video.example/watch')).enabled,global&&site);
 }
 f.set({background:true,sites:{'video.example':false}});
 assert.equal((await f.policy('https://other.example')).enabled,true);
 assert.equal((await f.policy('https://video.example.other.test')).enabled,true);
});
test('disabling background play restores native visibility and pauses media',async()=>{
 let receive;let paused=0;const media={tagName:'VIDEO',paused:false,ended:false,isConnected:true,playbackRate:1,defaultPlaybackRate:1,addEventListener(){},pause(){paused++;this.paused=true;}};
 class Document {get hidden(){return true;}get visibilityState(){return 'hidden';}}
 const document=Object.assign(new Document(),{querySelectorAll:()=>[media],addEventListener(){}});
 vm.runInNewContext(fs.readFileSync(path.join(base,'playback.js'),'utf8'),{document,Document,Map,WeakMap,WeakSet,performance,setTimeout,setInterval(){return 1;},clearInterval(){},browser:{runtime:{onMessage:{addListener:f=>receive=f},sendMessage:async()=>({})}}});
 await new Promise(setImmediate);
 receive({type:'mediaPolicy',enabled:true,background:true});assert.equal(document.hidden,false);assert.equal(paused,0);
 receive({type:'mediaPolicy',enabled:false,background:true});assert.equal(document.hidden,true);assert.equal(Object.hasOwn(document,'hidden'),false);assert.equal(paused,1);
 receive({type:'mediaPolicy',enabled:false,background:false});assert.equal(paused,1);
});
test('ad overlay cleanup is bounded, never periodic, and does not select site menus',()=>{
 let ready;let reads=0;let removed=0;
 const nodes=Array.from({length:10000},()=>({matches:()=>false,querySelector:()=>null,closest:()=>null,getBoundingClientRect:()=>({width:100,height:100}),remove(){removed++}}));
 vm.runInNewContext(fs.readFileSync(path.join(base,'adblocker.js'),'utf8'),{Date,Math,parseInt,parseFloat,setInterval(){assert.fail('No repeated overlay scan');},window:{open(){},addEventListener(){},innerWidth:100,innerHeight:100,getComputedStyle(){reads++;return {position:'fixed',zIndex:'10000',opacity:'0',backgroundColor:'transparent'}}},document:{readyState:'loading',querySelectorAll:selector=>{assert.equal(selector,'.ad-overlay, #ad-overlay');return nodes},getElementById:()=>true,addEventListener:(_,f)=>ready=f}});
 ready();assert.equal(reads,64);assert.equal(removed,64);
});
