from pathlib import Path
import zipfile, hashlib, json

BASE=Path(__file__).resolve().parents[1]
compiled=BASE/'compiled'
out=compiled/'AniBrave-unsigned.apk'
with zipfile.ZipFile(compiled/'resources.apk') as source, zipfile.ZipFile(out,'w') as target:
    for item in source.infolist():
        if item.filename.startswith('META-INF/') or (item.filename.startswith('classes') and item.filename.endswith('.dex')):
            continue
        target.writestr(item,source.read(item))
    for path in sorted((compiled/'dex').glob('classes*.dex')):
        target.write(path,path.name,compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(BASE/'downloads'/'Bravearm64Universal.apk') as original,zipfile.ZipFile(out) as result:
    native=[name for name in original.namelist() if name.startswith('lib/') and name.endswith('.so')]
    for name in native:
        assert hashlib.sha256(original.read(name)).digest()==hashlib.sha256(result.read(name)).digest(),f'Native library changed: {name}'
    assert 'classes8.dex' in result.namelist()
    assert result.testzip() is None
print(f'Packaged {out.name}; verified {len(native)} unchanged Brave native libraries')
