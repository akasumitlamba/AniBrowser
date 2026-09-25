/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../main/assets/extensions/anibrowser/pip.js'), 'utf8');
function fixture() {
  let receive;
  const videos = [];
  vm.runInNewContext(code, {
    window:{addEventListener(){}}, document:{querySelectorAll:()=>videos},
    browser:{runtime:{onMessage:{addListener:f=>receive=f}}},
  });
  const add = (paused=false) => {
    const media = {tagName:'AUDIO',paused,ended:false,isConnected:true,currentTime:30,duration:100,
      pause(){this.paused=true;},play(){this.paused=false;return Promise.resolve();}};
    videos.push(media);return media;
  };
  return {add, command:command=>receive({type:'mediaCommand',command})};
}
test('media controls resume the same audio after pausing outside presentation mode',()=>{
  const f=fixture(), audio=f.add();
  f.command('toggle');assert.equal(audio.paused,true);
  f.command('toggle');assert.equal(audio.paused,false);
});
test('seek controls work after pausing and clamp to media boundaries',()=>{
  const f=fixture(), audio=f.add();
  f.command('toggle');f.command('back');assert.equal(audio.currentTime,20);
  audio.currentTime=5;f.command('back');assert.equal(audio.currentTime,0);
  audio.currentTime=95;f.command('forward');assert.equal(audio.currentTime,100);
});
test('media commands ignore idle frames and detached media',()=>{
  const f=fixture(), idle=f.add(true);
  f.command('toggle');assert.equal(idle.paused,true);
  const playing=f.add();f.command('toggle');playing.isConnected=false;
  f.command('toggle');assert.equal(playing.paused,true);assert.equal(idle.paused,true);
});
