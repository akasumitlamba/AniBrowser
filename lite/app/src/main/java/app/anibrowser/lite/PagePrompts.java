/* SPDX-License-Identifier: MPL-2.0 */
package app.anibrowser.lite;

import android.app.AlertDialog;
import android.widget.*;
import org.mozilla.geckoview.*;
import java.util.*;

/** Native page dialogs, including the select menus used by video players. */
final class PagePrompts implements GeckoSession.PromptDelegate {
    private final BrowserActivity activity;
    PagePrompts(BrowserActivity activity) { this.activity = activity; }
    private interface Response { PromptResponse get(); }
    private GeckoResult<PromptResponse> show(BasePrompt prompt, java.util.function.BiConsumer<AlertDialog.Builder, java.util.function.Consumer<PromptResponse>> setup) {
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        boolean[] done = {false};
        java.util.function.Consumer<PromptResponse> complete = response -> { if (!done[0]) { done[0]=true; result.complete(response); } };
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(prompt.title == null ? SitePolicy.host(activity.currentUrl()) : prompt.title);
        setup.accept(builder, complete);
        activity.showDialog(builder.create(), () -> { if (!done[0]) complete.accept(prompt.dismiss()); });
        return result;
    }
    @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, AlertPrompt p) {
        return show(p,(b,c) -> b.setMessage(p.message).setPositiveButton("OK",(d,w) -> c.accept(p.dismiss())));
    }
    @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, ButtonPrompt p) {
        return show(p,(b,c) -> b.setMessage(p.message).setPositiveButton("OK",(d,w) -> c.accept(p.confirm(ButtonPrompt.Type.POSITIVE))).setNegativeButton("Cancel",(d,w) -> c.accept(p.confirm(ButtonPrompt.Type.NEGATIVE))));
    }
    @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, TextPrompt p) {
        EditText input = new EditText(activity); input.setText(p.defaultValue);
        return show(p,(b,c) -> b.setMessage(p.message).setView(input).setPositiveButton("OK",(d,w) -> c.accept(p.confirm(input.getText().toString()))).setNegativeButton("Cancel",null));
    }
    @Override public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession s, AuthPrompt p) {
        LinearLayout fields = new LinearLayout(activity); fields.setOrientation(LinearLayout.VERTICAL);
        EditText username = new EditText(activity); username.setHint("Username");
        EditText password = new EditText(activity); password.setHint("Password"); password.setInputType(129);
        fields.addView(username); fields.addView(password);
        return show(p,(b,c) -> b.setMessage(p.message).setView(fields).setPositiveButton("Sign in",(d,w) -> c.accept(p.confirm(username.getText().toString(),password.getText().toString()))).setNegativeButton("Cancel",null));
    }
    private void flatten(ChoicePrompt.Choice[] choices, List<ChoicePrompt.Choice> out) {
        for (ChoicePrompt.Choice choice : choices) {
            if (choice.items != null) flatten(choice.items,out);
            else if (!choice.separator && !choice.disabled) out.add(choice);
        }
    }
    @Override public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession s, ChoicePrompt p) {
        List<ChoicePrompt.Choice> choices = new ArrayList<>(); flatten(p.choices,choices);
        String[] labels = choices.stream().map(choice -> choice.label).toArray(String[]::new);
        if (p.type == ChoicePrompt.Type.MULTIPLE) {
            boolean[] checked = new boolean[choices.size()]; for(int i=0;i<checked.length;i++) checked[i]=choices.get(i).selected;
            return show(p,(b,c) -> b.setMultiChoiceItems(labels,checked,(d,i,value) -> checked[i]=value).setPositiveButton("OK",(d,w) -> {
                List<String> ids = new ArrayList<>(); for(int i=0;i<checked.length;i++) if(checked[i]) ids.add(choices.get(i).id);
                c.accept(p.confirm(ids.toArray(new String[0])));
            }).setNegativeButton("Cancel",null));
        }
        return show(p,(b,c) -> b.setItems(labels,(d,i) -> c.accept(p.confirm(choices.get(i)))).setNegativeButton("Cancel",null));
    }
    @Override public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession s, PopupPrompt p) {
        // NavigationDelegate asks before the resulting new top-level request.
        return GeckoResult.fromValue(p.confirm(AllowOrDeny.ALLOW));
    }
    @Override public GeckoResult<PromptResponse> onRedirectPrompt(GeckoSession s, RedirectPrompt p) {
        return GeckoResult.fromValue(p.confirm(AllowOrDeny.ALLOW));
    }
    @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession s, BeforeUnloadPrompt p) {
        return GeckoResult.fromValue(p.confirm(AllowOrDeny.ALLOW));
    }
    @Override public GeckoResult<PromptResponse> onRepostConfirmPrompt(GeckoSession s, RepostConfirmPrompt p) {
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        activity.ask("Resend form?", "This page needs to send the form again.", allowed -> result.complete(p.confirm(allowed ? AllowOrDeny.ALLOW : AllowOrDeny.DENY)));
        return result;
    }
}
