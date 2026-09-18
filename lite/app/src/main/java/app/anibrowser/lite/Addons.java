/* SPDX-License-Identifier: MPL-2.0 */
package app.anibrowser.lite;

import android.app.AlertDialog;
import android.widget.EditText;
import org.mozilla.geckoview.*;
import java.util.*;

/** Loaded on demand; no add-on catalog downloads or periodic background updates. */
final class Addons implements WebExtension.ActionDelegate {
    private final BrowserActivity activity;
    private final WebExtensionController controller;
    private final Map<String, WebExtension.Action> defaults = new HashMap<>(), actions = new HashMap<>();
    private GeckoSession popup;
    private AlertDialog popupDialog;
    Addons(BrowserActivity activity) {
        this.activity=activity; controller=activity.runtime.getWebExtensionController();
        controller.setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(WebExtension extension,String[] permissions,String[] origins,String[] data) {
                GeckoResult<WebExtension.PermissionPromptResponse> result=new GeckoResult<>();
                activity.ask("Install " + extension.metaData.name + "?", permissions(permissions,origins,data), allowed -> result.complete(new WebExtension.PermissionPromptResponse(allowed,false,false)));
                return result;
            }
            @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension extension,String[] permissions,String[] origins,String[] data) { return permission(extension,permissions,origins,data); }
            @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(WebExtension extension,String[] permissions,String[] origins,String[] data) { return permission(extension,permissions,origins,data); }
            private GeckoResult<AllowOrDeny> permission(WebExtension e,String[] p,String[] o,String[] d) {
                GeckoResult<AllowOrDeny> result=new GeckoResult<>();
                activity.ask(e.metaData.name + " permissions",permissions(p,o,d),allowed -> result.complete(allowed ? AllowOrDeny.ALLOW : AllowOrDeny.DENY)); return result;
            }
        });
    }
    private String permissions(String[] p,String[] o,String[] d) { return "Permissions:\n" + String.join("\n",p) + "\n\nWebsites:\n" + String.join("\n",o) + "\n\nData permissions:\n" + String.join("\n",d); }
    void bindInstalled() { controller.list().accept(list -> list.forEach(this::bind), error -> activity.toast("Could not read extensions")); }
    private void bind(WebExtension extension) {
        if (extension.isBuiltIn) return;
        extension.setActionDelegate(this); activity.session.getWebExtensionController().setActionDelegate(extension,this);
        extension.setTabDelegate(new WebExtension.TabDelegate() {
            @Override public void onOpenOptionsPage(WebExtension e) { options(e); }
            @Override public GeckoResult<GeckoSession> onNewTab(WebExtension e, WebExtension.CreateTabDetails details) {
                GeckoSession target = extensionPage(); return GeckoResult.fromValue(target);
            }
        });
    }
    void show() {
        controller.list().accept(all -> {
            List<WebExtension> list=new ArrayList<>(); for(WebExtension e:all) if(!e.isBuiltIn) list.add(e);
            String[] labels=new String[list.size()+1]; labels[0]="Install from signed XPI link";
            for(int i=0;i<list.size();i++) labels[i+1]=list.get(i).metaData.name + (list.get(i).metaData.enabled ? "" : " (disabled)");
            activity.showDialog(new AlertDialog.Builder(activity).setTitle("Extensions").setItems(labels,(d,index) -> { if(index==0) install(); else manage(list.get(index-1)); }).setNegativeButton("Close",null).create());
        }, error -> activity.toast("Could not read extensions"));
    }
    private void install() {
        EditText input=new EditText(activity); input.setHint("https://…/extension.xpi"); input.setSingleLine();
        activity.showDialog(new AlertDialog.Builder(activity).setTitle("Install extension").setMessage("Paste a Mozilla-signed extension download link. You will review its permissions before installation.").setView(input).setPositiveButton("Continue",(d,w) -> {
            String url=input.getText().toString().trim();
            if(!url.startsWith("https://") || !SitePolicy.web(url)) { activity.toast("Use an HTTPS extension download link"); return; }
            controller.install(url,WebExtensionController.INSTALLATION_METHOD_MANAGER).accept(extension -> { bind(extension); activity.toast("Extension installed"); },error -> activity.toast("Extension could not be installed: " + error.getMessage()));
        }).setNegativeButton("Cancel",null).create());
    }
    private void manage(WebExtension extension) {
        String[] labels={"Open extension", "Options", extension.metaData.enabled ? "Disable" : "Enable", "Check for update", "Remove"};
        activity.showDialog(new AlertDialog.Builder(activity).setTitle(extension.metaData.name).setItems(labels,(d,index) -> {
            switch(index) {
                case 0: WebExtension.Action action=actions.getOrDefault(extension.id,defaults.get(extension.id)); if(action!=null) action.click(); else options(extension); break;
                case 1: options(extension); break;
                case 2: (extension.metaData.enabled ? controller.disable(extension,WebExtensionController.EnableSource.USER) : controller.enable(extension,WebExtensionController.EnableSource.USER)).accept(e -> { bind(e); activity.toast("Extension updated"); },error -> activity.toast("Could not change extension")); break;
                case 3: controller.update(extension).accept(e -> { if(e!=null) bind(e); activity.toast(e==null ? "No update available" : "Extension updated"); },error -> activity.toast("Could not update extension")); break;
                case 4: controller.uninstall(extension).accept(unused -> { actions.remove(extension.id); defaults.remove(extension.id); activity.toast("Extension removed"); },error -> activity.toast("Could not remove extension")); break;
            }
        }).setNegativeButton("Close",null).create());
    }
    private void options(WebExtension extension) {
        String url=extension.metaData.optionsPageUrl;
        if(url==null || url.isEmpty()) { activity.toast("This extension has no options page"); return; }
        GeckoSession target=extensionPage(); target.open(activity.runtime); target.loadUri(url);
    }
    private GeckoSession extensionPage() {
        closePopup();
        popup=new GeckoSession(new GeckoSessionSettings.Builder().suspendMediaWhenInactive(true).build());
        GeckoView view=new GeckoView(activity);
        popupDialog=new AlertDialog.Builder(activity).setTitle("Extension").setView(view).setNegativeButton("Close",(d,w) -> {}).create();
        GeckoSession created=popup;
        activity.showDialog(popupDialog,() -> { view.releaseSession(); created.close(); if(popup==created) popup=null; });
        popupDialog.getWindow().setLayout(-1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.8f));
        view.setSession(created);
        created.setPromptDelegate(new PagePrompts(activity));
        return created;
    }
    @Override public void onBrowserAction(WebExtension e,GeckoSession session,WebExtension.Action action) {
        if(session==null) defaults.put(e.id,action); else if(session==activity.session) actions.put(e.id,defaults.containsKey(e.id) ? action.withDefault(defaults.get(e.id)) : action);
    }
    @Override public void onPageAction(WebExtension e,GeckoSession session,WebExtension.Action action) { onBrowserAction(e,session,action); }
    @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension e,WebExtension.Action action) { return GeckoResult.fromValue(extensionPage()); }
    @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension e,WebExtension.Action action) { return GeckoResult.fromValue(extensionPage()); }
    private void closePopup() { if(popupDialog!=null) { popupDialog.dismiss(); popupDialog=null; } }
    void close() { closePopup(); controller.setPromptDelegate(null); }
}
