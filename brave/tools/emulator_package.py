"""Private x86-64 harness. Not a distributable or proof of ARM64 runtime acceptance."""
from pathlib import Path
import zipfile
BASE=Path(__file__).resolve().parents[1]
with zipfile.ZipFile(BASE/'compiled'/'AniBrave-unsigned.apk') as app, zipfile.ZipFile(BASE/'downloads'/'BraveMonox64.apk') as engine, zipfile.ZipFile(BASE/'compiled'/'emulator-unsigned.apk','w') as out:
    for item in app.infolist():
        name=item.filename
        if name.startswith('lib/') or ('snapshot' in name and name.startswith('assets/')):continue
        out.writestr(item,app.read(item))
    for item in engine.infolist():
        if item.filename.startswith('lib/x86_64/') or ('snapshot' in item.filename and item.filename.startswith('assets/')):
            out.writestr(item,engine.read(item))
print('Private emulator harness assembled with upstream x86-64 native engine and snapshots')
