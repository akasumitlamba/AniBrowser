from pathlib import Path
import subprocess,zipfile,hashlib,json,struct,zlib

BASE=Path(__file__).resolve().parents[1]
ROOT=BASE.parent
APK=ROOT/'output'/'anibrave'/'AniBrave-0.1.0-arm64-v8a.apk'
BUILD=ROOT/'.tools'/'sdk'/'build-tools'/'37.0.0'
def digest(zip,name):
    h=hashlib.sha256()
    with zip.open(name) as stream:
        while block:=stream.read(1024*1024):h.update(block)
    return h.hexdigest()
with zipfile.ZipFile(APK) as app,zipfile.ZipFile(BASE/'downloads'/'Bravearm64Universal.apk') as upstream:
    native=[n for n in upstream.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assert all(digest(app,n)==digest(upstream,n) for n in native)
    dex=[n for n in app.namelist() if n.startswith('classes') and n.endswith('.dex')]
    assert len(dex)==8
    for name in dex:
        data=app.read(name)
        assert data[:4]==b'dex\n'
        assert data[12:32]==hashlib.sha1(data[32:]).digest(),name
        assert struct.unpack_from('<I',data,8)[0]==zlib.adler32(data[12:])&0xffffffff,name
    assert app.read('assets/anibrave/playback.js')==(BASE/'src'/'playback.js').read_bytes()
    assert app.testzip() is None
badging=subprocess.check_output([str(BUILD/'aapt2.exe'),'dump','badging',str(APK)],encoding='utf-8')
assert "name='app.anibrave.main'" in badging
assert "application-label:'AniBrave'" in badging
assert "native-code: 'arm64-v8a'" in badging
assert "minSdkVersion:'29'" in badging
report={'package':'app.anibrave.main','name':'AniBrave','version':'0.1.0','brave':'1.97.56','architecture':'arm64-v8a','minimumAndroid':10,'bytes':APK.stat().st_size,'sha256':hashlib.sha256(APK.read_bytes()).hexdigest(),'unchangedNativeLibraries':len(native),'verifiedDexFiles':len(dex),'controllerChecks':23,'deviceQA':'See brave/qa-output for checks recorded after installation; package verification does not verify runtime behavior.'}
(APK.parent/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
(APK.parent/'SHA256SUMS.txt').write_text(report['sha256']+'  '+APK.name+'\n')
print(json.dumps(report,indent=2))
