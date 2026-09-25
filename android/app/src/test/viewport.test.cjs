/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const code = fs.readFileSync(require('node:path').join(__dirname,'../main/assets/extensions/anibrowser/viewport.js'),'utf8');
function fixture({scale=1,iframe=false,inset=84}={}) {
 const events={}, viewportEvents={}, frames=[]; let receive, reply;
 class Element {
  constructor(base=12) { this.base=base; this.isConnected=true; this.values=new Map(); this.clientHeight=600; this.scrollHeight=1200; this.style={getPropertyValue:k=>this.values.get(k)?.value||'',getPropertyPriority:k=>this.values.get(k)?.priority||'',setProperty:(k,value,priority)=>this.values.set(k,{value,priority}),removeProperty:k=>this.values.delete(k)}; }
  getBoundingClientRect(){return {height:600,bottom:800};}
 }
 const root=new Element();root.style.setProperty('padding-bottom','12px','');
 const document={scrollingElement:root,documentElement:root,fullscreenElement:null,addEventListener:(k,f)=>events[k]=f};
 const window={visualViewport:{scale,addEventListener:(k,f)=>viewportEvents[k]=f},addEventListener:(k,f)=>events[k]=f};window.top=iframe?{}:window;
 vm.runInNewContext(code,{window,document,requestAnimationFrame:f=>{frames.push(f);return frames.length;},innerHeight:800,HTMLElement:Element,getComputedStyle:e=>({paddingBottom:e.base+'px'}),browser:{runtime:{onMessage:{addListener:f=>receive=f},sendMessage:()=>new Promise(resolve=>reply=resolve)}}});
 if(receive && inset) {receive({type:'chromeInset',bottom:inset});while(frames.length)frames.shift()();}
 return {root,document,window,events,Element,frames,reply:message=>reply(message),flush:()=>{while(frames.length)frames.shift()();},get receive(){return receive;}};
}

test('native toolbar clearance does not modify website layout or fixed navigation',()=>{
 const f=fixture({inset:0});
 for(let i=0;i<10;i++)f.events.resize();
 f.flush();
 assert.equal(f.root.style.getPropertyValue('padding-bottom'),'12px');
 const panel=new f.Element(4);f.events.scroll({target:panel});f.flush();
 assert.equal(panel.style.getPropertyValue('padding-bottom'),'');
 assert.equal(f.frames.length,0);
});
test('floating dock space preserves existing padding and never accumulates on resize',()=>{
 const f=fixture();assert.equal(f.root.style.getPropertyValue('padding-bottom'),'96px');
 for(let i=0;i<10;i++) f.events.resize();
 assert.equal(f.frames.length,1);f.flush();
 assert.equal(f.root.style.getPropertyValue('padding-bottom'),'96px');
});
test('fullscreen and shortcut mode restore original padding; normal browsing restores clearance',()=>{
 const f=fixture();f.document.fullscreenElement={};f.events.fullscreenchange();f.flush();
 assert.equal(f.root.style.getPropertyValue('padding-bottom'),'12px');
 f.document.fullscreenElement=null;f.events.fullscreenchange();
 f.receive({type:'chromeInset',bottom:0});f.flush();assert.equal(f.root.style.getPropertyValue('padding-bottom'),'12px');
 f.receive({type:'chromeInset',bottom:84});f.flush();assert.equal(f.root.style.getPropertyValue('padding-bottom'),'96px');
});
test('desktop viewport scale and full-height scrolling panels receive correct clearance',()=>{
 const f=fixture({scale:.5});assert.equal(f.root.style.getPropertyValue('padding-bottom'),'180px');
 const panel=new f.Element(4);f.events.scroll({target:panel});f.flush();assert.equal(panel.style.getPropertyValue('padding-bottom'),'172px');
 f.receive({type:'chromeInset',bottom:0});f.flush();assert.equal(panel.style.getPropertyValue('padding-bottom'),'');
});
test('embedded frames remain untouched and invalid messages are ignored',()=>{
 const embedded=fixture({iframe:true});assert.equal(embedded.root.style.getPropertyValue('padding-bottom'),'12px');
 const f=fixture();f.receive({type:'chromeInset',bottom:NaN});assert.equal(f.root.style.getPropertyValue('padding-bottom'),'96px');
});

test('late initial inset reply cannot restore obsolete toolbar padding',async()=>{
 const f=fixture();
 f.receive({type:'chromeInset',bottom:0});f.flush();
 f.reply({bottom:84});await new Promise(setImmediate);
 assert.equal(f.root.style.getPropertyValue('padding-bottom'),'12px');
});
