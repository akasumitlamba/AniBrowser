/* SPDX-License-Identifier: MPL-2.0 */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const code=fs.readFileSync(path.join(__dirname,'../main/assets/extensions/anibrowser/background.js'),'utf8');
const flush=()=>new Promise(setImmediate);
async function fixture({saved=2,get,set}={}) {
  let native,message,connected=false;const sent=[],writes=[];
  const event=()=>({addListener(){}});
  const port={onMessage:{addListener:f=>native=f},onDisconnect:event(),postMessage(){}};
  vm.runInNewContext(code,{URL,Map,Set,Promise,setTimeout,clearTimeout,browser:{
    storage:{local:{get:get|| (async()=>({speed:saved})),set:set|| (async value=>writes.push(value.speed))}},
    tabs:{query:async()=>[{id:1},{id:2}],sendMessage:async(...args)=>sent.push(args),onCreated:event(),onRemoved:event()},
    runtime:{onMessage:{addListener:f=>message=f},connectNative:()=>{connected=true;return port;}},
    webRequest:{onBeforeRequest:event(),onCompleted:event(),onErrorOccurred:event()}
  }});
  await flush();
  return {sent,writes,connected,update:async speed=>{native({speed});await flush();},speed:async()=> (await message({type:'speed'},{})).speed};
}
test('reselecting the saved speed broadcasts again to every tab without a frame restriction',async()=>{
  const f=await fixture();await f.update(2);
  assert.equal(f.sent.length,2);assert.deepEqual(f.sent.map(args=>args[0]),[1,2]);
  for(const args of f.sent){assert.equal(args.length,2);assert.equal(args[1].speed,2);}
  assert.equal(f.writes.length,0);
});
test('speed reaches players even if extension storage fails or stalls',async()=>{
  for(const set of [async()=>{throw Error('storage unavailable');},()=>new Promise(()=>{})]){
    const f=await fixture({set});await f.update(1.5);
    assert.equal(f.sent.length,2);assert.equal(f.sent[0][1].speed,1.5);assert.equal(await f.speed(),1.5);
  }
});
test('failure reading extension storage does not prevent native connection or speed commands',async()=>{
  const f=await fixture({get:async()=>{throw Error('storage unavailable');}});
  assert.equal(f.connected,true);await f.update(1.75);assert.equal(f.sent[0][1].speed,1.75);
});
test('rapid rate selections persist in selection order while playback updates immediately',async()=>{
  const writes=[],release=[];
  const f=await fixture({set:value=>{writes.push(value.speed);return new Promise(resolve=>release.push(resolve));}});
  await f.update(1.25);await f.update(1.75);
  assert.deepEqual(writes,[1.25]);assert.equal(f.sent.at(-1)[1].speed,1.75);
  release.shift()();await flush();assert.deepEqual(writes,[1.25,1.75]);release.shift()();
});
