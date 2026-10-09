"""Read-only controller diagnostics through an explicitly forwarded device socket."""
import json, sys, urllib.request
import websocket

port = 9227
pages = json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json/list'))
page = next(p for p in pages if p['type'] == 'page')
ws = websocket.create_connection(page['webSocketDebuggerUrl'], suppress_origin=True, timeout=3)
sequence = 0
contexts = []
def command(method, params=None):
    global sequence
    sequence += 1
    ws.send(json.dumps({'id': sequence, 'method': method, 'params': params or {}}))
    while True:
        result = json.loads(ws.recv())
        if result.get('method') == 'Runtime.executionContextCreated':
            contexts.append(result['params']['context'])
        if result.get('id') == sequence:
            return result

command('Runtime.enable')
print(json.dumps({'contexts': [{'id': c['id'], 'name': c['name']} for c in contexts]}))
world = next((c['id'] for c in contexts if c.get('name') == 'AniBrave'), None)
params = {'expression': sys.argv[1] if len(sys.argv)>1 else 'JSON.stringify({controller:!!globalThis.__aniBrave,media:[...document.querySelectorAll("video,audio")].map(v=>({rate:v.playbackRate,defaultRate:v.defaultPlaybackRate})),width:innerWidth,ua:navigator.userAgent})', 'returnByValue': True}
if world is not None: params['contextId'] = world
print(json.dumps(command('Runtime.evaluate', params)))
ws.close()
