"""Version-pinned integration hooks. Fail rather than guessing changed upstream bytecode."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parents[1]
DECODED = BASE / 'decoded' / 'smali_classes5'
PATCH = BASE / 'compiled' / 'patches'
RES = BASE / 'resources'
NS = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', NS)

def rewrite(relative, transform):
    source = DECODED / relative
    text = source.read_text(encoding='utf-8')
    changed = transform(text)
    if changed == text:
        raise RuntimeError(f'Expected upstream hook missing: {relative}')
    target = PATCH / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(changed, encoding='utf-8')

def replace_method(text, signature, body):
    pattern = r'(?m)^\.method ' + re.escape(signature) + r'\n.*?^\.end method'
    changed, count = re.subn(pattern, '.method '+signature+'\n'+body+'\n.end method', text, flags=re.S|re.M)
    if count != 1: raise RuntimeError(f'Expected exactly one method: {signature}, got {count}')
    return changed

def inject(text, signature, instructions):
    pattern = r'(?m)(^\.method ' + re.escape(signature) + r'\n\s+\.locals \d+\n)'
    changed, count = re.subn(pattern, lambda m:m[1]+instructions+'\n', text)
    if count != 1: raise RuntimeError(f'Missing integration method: {signature}')
    return changed

def devtools(text):
    text = replace_method(text, 'public static checkDebugPermission(II)Z', '''    .locals 1
    invoke-static {}, Landroid/os/Process;->myUid()I
    move-result v0
    if-ne p1, v0, :denied
    const/4 v0, 0x1
    return v0
    :denied
    const/4 v0, 0x0
    return v0''')
    return replace_method(text, 'public final a()V', '''    .locals 2
    iget-wide v0, p0, Lorg/chromium/chrome/browser/DevToolsServer;->b:J
    invoke-static {p0, v0, v1}, Lapp/anibrave/AniRuntime;->server(Ljava/lang/Object;J)V
    return-void''')

rewrite('org/chromium/chrome/browser/DevToolsServer.smali', devtools)
def prefix(text):
    before='const-string v4, "chrome"'
    if text.count(before)!=1:raise RuntimeError('DevTools socket prefix changed')
    return text.replace(before,'const-string v4, "anibrave"')
rewrite('ukf.smali',prefix)

def activity(text):
    text=inject(text,'public final onResume()V','    invoke-static/range {p0 .. p0}, Lapp/anibrave/AniRuntime;->resume(Landroid/app/Activity;)V')
    return inject(text,'public final onNewIntent(Landroid/content/Intent;)V','    invoke-static/range {p0 .. p1}, Lapp/anibrave/AniRuntime;->intent(Landroid/app/Activity;Landroid/content/Intent;)V')
rewrite('org/chromium/chrome/browser/ChromeTabbedActivity.smali',activity)
rewrite('org/chromium/content_public/browser/LoadUrlParams.smali',lambda text:inject(text,'public constructor <init>(ILjava/lang/String;)V','''    invoke-static/range {p2 .. p2}, Lapp/anibrave/AniRuntime;->blankNewTab(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p2'''))
rewrite('org/chromium/chrome/browser/customtabs/FullScreenCustomTabActivity.smali',lambda text:inject(text,'public final O3()V','    invoke-static/range {p0 .. p0}, Lapp/anibrave/AniRuntime;->resume(Landroid/app/Activity;)V'))

# Preserve classes/component names; change install identity, permissions, provider authorities.
manifest=RES/'AndroidManifest.xml'
tree=ET.parse(manifest);root=tree.getroot()
root.set('package','app.anibrave.main')
root.set('{'+NS+'}versionName','0.1.0-brave-1.97.56')
app=root.find('application')
app.set('{'+NS+'}label','AniBrave')
app.set('{'+NS+'}icon','@drawable/anibrave_icon')
app.set('{'+NS+'}roundIcon','@drawable/anibrave_icon')
if not any(node.get('{'+NS+'}name')=='app.anibrave.ShortcutLaunchActivity' for node in app.findall('activity')):
    ET.SubElement(app,'activity',{'{'+NS+'}name':'app.anibrave.ShortcutLaunchActivity','{'+NS+'}exported':'true','{'+NS+'}theme':'@android:style/Theme.NoDisplay','{'+NS+'}excludeFromRecents':'true'})
for node in root.iter():
    for key,value in list(node.attrib.items()):
        if value.startswith('com.brave.browser'):
            node.set(key,value.replace('com.brave.browser','app.anibrave.main'))
tree.write(manifest,encoding='utf-8',xml_declaration=True)
for file in list((RES/'res').glob('values*/strings.xml')) + list((RES/'res'/'xml').glob('*.xml')):
    text=file.read_text(encoding='utf-8')
    changed=text.replace('com.brave.browser','app.anibrave.main')
    changed=re.sub(r'(<string name="APKTOOL_RENAMED_0x7f140450">).*?(</string>)',r'\1AniBrave\2',changed)
    if changed!=text:file.write_text(changed,encoding='utf-8')

(RES/'res'/'drawable'/'anibrave_icon.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:fillColor="#141414" android:pathData="M0,0h108v108h-108z"/>
<path android:fillColor="#FF7638" android:pathData="M54,19L87,78H72L63,60H45L36,78H21Z"/>
<path android:fillColor="#141414" android:pathData="M54,38L47,50H61Z"/>
</vector>''',encoding='utf-8')
assets=RES/'assets'/'anibrave';assets.mkdir(exist_ok=True)
(assets/'playback.js').write_bytes((BASE/'src'/'playback.js').read_bytes())
# Reserve native toolbar space beside the tab switcher, so Brave's layout and
# scrolling own the feature button instead of overlaying website content.
for name,style in [('APKTOOL_RENAMED_0x7f0e04d8.xml','APKTOOL_RENAMED_0x7f15069e'),('APKTOOL_RENAMED_0x7f0e04d4.xml','APKTOOL_RENAMED_0x7f15069d')]:
    path=RES/'res'/'layout'/name
    layout=ET.parse(path); element=layout.getroot()
    if not any(node.get('{'+NS+'}id')=='@+id/anibrave_features' for node in element.iter()):
        matches=[(parent,node) for parent in element.iter() for node in parent if node.get('{'+NS+'}id')=='@id/tab_switcher_button']
        if len(matches)!=1:raise RuntimeError('Expected native tab switcher in '+name)
        parent,tabs=matches[0]
        icon=ET.Element('ImageButton',{'{'+NS+'}id':'@+id/anibrave_features','{'+NS+'}src':'@drawable/anibrave_features','{'+NS+'}tint':'?android:attr/textColorPrimary','{'+NS+'}contentDescription':'AniBrave features','{'+NS+'}tooltipText':'AniBrave features','{'+NS+'}padding':'12dp','style':'@style/'+style})
        parent.insert(list(parent).index(tabs),icon)
        layout.write(path,encoding='utf-8',xml_declaration=True)
(RES/'res'/'drawable'/'anibrave_features.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
<path android:strokeColor="#FFFFFF" android:strokeWidth="2" android:strokeLineCap="round" android:pathData="M3,6H7M11,6H21M3,12H13M17,12H21M3,18H7M11,18H21M9,3V9M15,9V15M9,15V21"/>
</vector>''',encoding='utf-8')
print('Prepared five upstream bytecode hooks, separate package, AniBrave artwork and playback asset')
