/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../main/assets/extensions/anibrowser/background.js'), 'utf8');
async function fixture(rules) {
  let before, respond;
  const event = () => ({addListener(){}});
  const port = {onMessage:{addListener:f=>respond=f},onDisconnect:event(),postMessage:m=>{
    if(m.type==='navigation') queueMicrotask(()=>respond({type:'navigationResult',id:m.id,allowed:rules[new URL(m.destination).hostname]===true}));
  }};
  vm.runInNewContext(code,{URL,Map,Set,Promise,setTimeout,clearTimeout,browser:{
    storage:{local:{get:async()=>({speed:1.5}),set:async()=>{}}},
    tabs:{query:async()=>[],sendMessage:async()=>{},onCreated:event(),onRemoved:event()},
    runtime:{onMessage:event(),connectNative:()=>port},
    webRequest:{onBeforeRequest:{addListener:f=>before=f},onCompleted:event(),onErrorOccurred:event()}
  }});
  await new Promise(setImmediate);
  return {before};
}
test('Block preference still permits direct visits and ordinary cross-site links',async()=>{
  const f=await fixture({});
  const result=await f.before({tabId:1,requestId:'new',url:'https://b.example/',originUrl:'https://a.example/'});
  assert.equal(result.cancel,undefined);
});
test('Block does not cancel YouTube mobile endpoint redirects',async()=>{
  const f=await fixture({});
  await f.before({tabId:1,requestId:'chain',url:'https://www.youtube.com/'});
  assert.equal((await f.before({tabId:1,requestId:'chain',url:'https://m.youtube.com/'})).cancel,undefined);
});
test('Allow resumes redirects without changing request method or body',async()=>{
  const f=await fixture({'a.example':true});
  await f.before({tabId:1,requestId:'chain',url:'https://a.example/'});
  const result=await f.before({tabId:1,requestId:'chain',url:'https://b.example/',method:'POST'});
  assert.equal(Object.keys(result).length,0);
});
test('consent and authentication redirect chains retain the original request',async()=>{
  const f=await fixture({'a.example':true,'b.example':false});
  await f.before({tabId:1,requestId:'chain',url:'https://a.example/'});
  await f.before({tabId:1,requestId:'chain',url:'https://b.example/'});
  const request={tabId:1,requestId:'chain',url:'https://c.example/',method:'POST',requestBody:{formData:{token:['sample']}}};
  const before=JSON.stringify(request);
  assert.equal((await f.before(request)).cancel,undefined);
  assert.equal(JSON.stringify(request),before);
});

test('known ad-network destinations remain blocked during redirect chains',async()=>{
  const f=await fixture({});
  await f.before({tabId:1,requestId:'ad',url:'https://a.example/'});
  assert.equal((await f.before({tabId:1,requestId:'ad',url:'https://popads.net/trap'})).cancel,true);
});

test('ad blocking matches domain boundaries without blocking lookalike sites',async()=>{
  const f=await fixture({});
  for (const url of ['https://popads.net.example.org/', 'https://example.org/?next=popads.net', 'https://1xbet.example.org/']) {
    assert.equal((await f.before({tabId:1,url})).cancel,undefined,url);
  }
  for (const url of ['https://popads.net/', 'https://ads.popads.net/', 'https://popads.net./', 'https://1xbet.com/']) {
    assert.equal((await f.before({tabId:1,url})).cancel,true,url);
  }
});
