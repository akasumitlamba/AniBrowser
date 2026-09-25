/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../main/assets/extensions/anibrowser/adblocker.js'), 'utf8');
function fixture() {
  const opened=[],events={};
  const window={open:url=>{opened.push(url);return {url};},addEventListener:(event,callback)=>events[event]=callback};
  const document={baseURI:'https://example.org/',readyState:'complete',querySelectorAll:()=>[],getElementById:()=>true};
  vm.runInNewContext(code,{window,document,URL,Date});
  return {window,opened,events};
}
test('popup filtering permits legitimate URLs containing ad-network words',()=>{
  const f=fixture();
  for (const url of ['/search?q=doubleclick','https://popads.net.example.org/','https://example.org/adservice']) {
    assert.ok(f.window.open(url),url);
  }
});
test('popup filtering blocks known ad hosts and their subdomains',()=>{
  const f=fixture();f.events.click();
  for (const url of ['https://popads.net/', 'https://cdn.doubleclick.net/', 'https://popads.net./']) {
    assert.equal(f.window.open(url),null,url);
  }
  assert.equal(f.opened.length,0);
});
