package com.privatecalc.vault;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private static final int PHOTOS = 0, VIDEOS = 1, CONTACTS = 2, NOTES = 3, BROWSER = 4;
    private final int background = Color.rgb(14, 20, 23), panel = Color.rgb(28, 37, 42), raised = Color.rgb(38, 50, 56), mint = Color.rgb(171, 241, 210), mintWash = Color.rgb(30, 54, 48),
        lilac = Color.rgb(190, 198, 255), lilacWash = Color.rgb(40, 44, 70), danger = Color.rgb(255, 138, 128), dangerWash = Color.rgb(64, 32, 32), soft = 0xffa5b3b8, muted = 0xff8b9b9f;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(), thumbnailPool = Executors.newFixedThreadPool(2);
    // Decrypted previews live in memory only and are dropped when the vault locks.
    private final LruCache<String,Bitmap> thumbnails = new LruCache<String,Bitmap>(6*1024*1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private LinearLayout root;
    private TextView display, hint;
    private String expression = "";
    private VaultStore store;
    private android.content.SharedPreferences preferences;
    private boolean unlocked, foreground, busy, scientific, selecting;
    private int session;
    private VideoView video;
    private File exportFile;
    private Dialog vaultDialog;
    private final ArrayList<Uri> pendingImports = new ArrayList<>();
    private final ArrayList<Item> pendingExports = new ArrayList<>();
    // Picked documents whose encrypted copy is already on disk, waiting to be taken out of the gallery.
    // Nothing lands here until the import succeeded, so a failed import can never cost the original.
    private final ArrayList<Uri> pendingRemovals = new ArrayList<>();
    // How many items the open system delete prompt covers, and what was already settled before it opened.
    private int removalAsked, removalDone, removalStuck;
    private final HashSet<String> selection = new HashSet<>();
    private Uri pendingExportTree, pendingContactPick;
    // Decrypted contacts live in memory only, like the thumbnails, and are dropped on lock.
    private ArrayList<Contacts.Entry> contacts;
    // Notes are read and held the same way, in their own file and their own memory copy.
    private ArrayList<Notes.Entry> notes;
    private int tab;
    // The browser view outlives a tab switch so a page is not reloaded, and is destroyed on lock.
    private WebView browser;
    private EditText address;
    private ProgressBar loading;
    private String location = "";
    // Which media tab the file picker was opened from, so an import lands back on it after the PIN.
    private int importTab = PHOTOS;

    /** One vault file with the label the list shows for it. */
    private static final class Item {
        final File file; final boolean video; final String name, meta;
        Item(File file, boolean video, String name, String meta) { this.file=file; this.video=video; this.name=name; this.meta=meta; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
        preferences = getSharedPreferences("access", MODE_PRIVATE); scientific = preferences.getBoolean("scientific",false);
        store = new VaultStore(this); clearPreviews(); dropGrants(); calculator();
        if (!preferences.contains("hash")) intro();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private RippleDrawable surface(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(0x24ffffff), shape(color,radius), shape(Color.WHITE,radius)); }
    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params, int start, int top, int end, int bottom) { params.setMarginStart(dp(start)); params.topMargin=dp(top); params.setMarginEnd(dp(end)); params.bottomMargin=dp(bottom); return params; }
    private String count(int amount, String noun) { return amount + " " + noun + (amount == 1 ? "" : "s"); }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(dp(4),dp(8),dp(4),dp(8)); return t; }
    private TextView label(String value, int size, int color, boolean strong) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (strong) t.setTypeface(medium); return t; }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(mint); b.setBackground(surface(panel,18)); b.setStateListAnimator(null);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private ImageView glyph(int drawable, int color) { ImageView i = new ImageView(this); i.setImageResource(drawable); i.setColorFilter(color); i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return i; }
    // Rounded square holding a centred icon; size is in dp.
    private ImageView tile(int drawable, int color, int wash, int size) { ImageView i = glyph(drawable,color); i.setBackground(shape(wash,size*3/10)); i.setPadding(dp(size/4),dp(size/4),dp(size/4),dp(size/4)); return i; }
    private LinearLayout action(int drawable, String value, int color, int fill, Runnable run) {
        LinearLayout a = new LinearLayout(this); a.setGravity(Gravity.CENTER); a.setPadding(dp(18),0,dp(18),0); a.setBackground(surface(fill,18));
        if (run != null) a.setOnClickListener(v -> run.run()); else a.setClickable(true);
        if (drawable != 0) a.addView(glyph(drawable,color),new LinearLayout.LayoutParams(dp(20),dp(20)));
        a.addView(label(value,16,color,true),margins(new LinearLayout.LayoutParams(-2,-2),drawable != 0 ? 8 : 0,0,0,0)); return a;
    }
    private ImageView round(int drawable, String description, int color, Runnable run) {
        ImageView view = glyph(drawable,color); view.setBackground(surface(panel,22)); view.setPadding(dp(9),dp(9),dp(9),dp(9)); view.setContentDescription(description);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); view.setOnClickListener(v -> run.run()); return view;
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24),dp(24),dp(24),dp(20)); card.setBackground(shape(panel,28)); return card;
    }
    /** Keeps a tall card usable once the keypad covers half the screen. */
    private ScrollView scrollable(View content) {
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(content,new FrameLayout.LayoutParams(-1,-2)); return scroll;
    }
    private LinearLayout sheet(int drawable, int color, int wash, String title, String detail) {
        LinearLayout sheet = new LinearLayout(this); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setPadding(dp(8),dp(10),dp(8),dp(8)); sheet.setBackground(shape(panel,28));
        View grip = new View(this); grip.setBackground(shape(raised,2)); LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(36),dp(4)); gripParams.gravity = Gravity.CENTER_HORIZONTAL; sheet.addView(grip,gripParams);
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(12),dp(14),dp(12),dp(16));
        if (drawable != 0) head.addView(tile(drawable,color,wash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        head.addView(stack(title,detail,Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),drawable != 0 ? 14 : 0,0,0,0)); sheet.addView(head);
        View line = new View(this); line.setBackgroundColor(raised); sheet.addView(line,margins(new LinearLayout.LayoutParams(-1,dp(1)),12,0,12,6)); return sheet;
    }
    private EditText field(String placeholder, int type) {
        EditText entry = new EditText(this); entry.setInputType(type); entry.setSingleLine(true);
        entry.setHint(placeholder); entry.setHintTextColor(muted); entry.setTextColor(Color.WHITE); entry.setTextSize(16);
        entry.setBackground(shape(raised,14)); entry.setPadding(dp(16),dp(14),dp(16),dp(14)); return entry;
    }
    /** The body of a note. It grows with what is typed and the card scrolls, so the keyboard never hides the buttons. */
    private EditText area(String placeholder, int lines) {
        EditText entry = new EditText(this); entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entry.setGravity(Gravity.TOP | Gravity.START); entry.setMinLines(lines);
        entry.setHint(placeholder); entry.setHintTextColor(muted); entry.setTextColor(Color.WHITE); entry.setTextSize(16);
        entry.setBackground(shape(raised,14)); entry.setPadding(dp(16),dp(14),dp(16),dp(14)); return entry;
    }
    private EditText pinField(String placeholder) {
        EditText entry = field(placeholder,InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        entry.setPadding(dp(16),dp(14),dp(48),dp(14)); return entry;   // room for the reveal button
    }
    /** Wraps a PIN field with an eye button that reveals what was typed; the digits stay hidden until it is tapped. */
    private FrameLayout pinRow(EditText entry) {
        FrameLayout row = new FrameLayout(this); row.addView(entry,new FrameLayout.LayoutParams(-1,-2));
        final Typeface face = entry.getTypeface();
        ImageView eye = glyph(R.drawable.ic_view,muted); eye.setBackground(surface(Color.TRANSPARENT,18)); eye.setPadding(dp(11),dp(11),dp(11),dp(11));
        eye.setContentDescription("Show PIN"); eye.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        eye.setOnClickListener(v -> {
            boolean hidden = (entry.getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0;
            entry.setInputType(InputType.TYPE_CLASS_NUMBER | (hidden ? 0 : InputType.TYPE_NUMBER_VARIATION_PASSWORD));
            entry.setTypeface(face); entry.setSelection(entry.getText().length());
            eye.setImageResource(hidden ? R.drawable.ic_hide : R.drawable.ic_view); eye.setColorFilter(hidden ? mint : muted);
            eye.setContentDescription(hidden ? "Hide PIN" : "Show PIN");
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44),dp(44)); params.gravity = Gravity.END | Gravity.CENTER_VERTICAL; params.setMarginEnd(dp(3));
        row.addView(eye,params); return row;
    }
    /** One line that truncates instead of wrapping, so long text never changes the height of the row holding it. */
    private TextView single(TextView view) { view.setMaxLines(1); view.setEllipsize(android.text.TextUtils.TruncateAt.END); return view; }
    /** Header caption that truncates instead of wrapping, so the buttons beside it keep the row one line tall. */
    private TextView banner(String value) { return single(label(value,13,mint,true)); }
    private LinearLayout stack(String title, String detail, int color) {
        LinearLayout s = new LinearLayout(this); s.setOrientation(LinearLayout.VERTICAL);
        s.addView(label(title,16,color,true)); s.addView(label(detail,13,soft,false),margins(new LinearLayout.LayoutParams(-2,-2),0,2,0,0)); return s;
    }
    private Dialog popup(View content, int gravity) { return popup(content,gravity,true); }
    private Dialog popup(View content, int gravity, boolean dismissable) {
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(dismissable); dialog.setCanceledOnTouchOutside(dismissable);
        FrameLayout frame = new FrameLayout(this); content.setClickable(true); frame.addView(content,new FrameLayout.LayoutParams(-1,-2));
        if (dismissable) frame.setOnClickListener(v -> dialog.dismiss());
        frame.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(dp(12),dp(12),dp(12),dp(12)+insets.getSystemWindowInsetBottom()); return insets; });
        frame.setPadding(dp(12),dp(12),dp(12),dp(12)); dialog.setContentView(frame);
        Window window = dialog.getWindow(); window.addFlags(WindowManager.LayoutParams.FLAG_SECURE); window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setDimAmount(0.6f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(480)),-2); window.setGravity(gravity);
        if (gravity == Gravity.BOTTOM) window.setWindowAnimations(android.R.style.Animation_InputMethod);
        dialog.show(); return dialog;
    }
    private void closeDialog() { if (vaultDialog != null) { vaultDialog.dismiss(); vaultDialog = null; } }
    private void base() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(16),dp(20),dp(16)); root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(dp(20),dp(16)+insets.getSystemWindowInsetTop(),dp(20),dp(16)+insets.getSystemWindowInsetBottom()); return insets; });
        setContentView(root); root.requestApplyInsets();
    }
    private void calculator() {
        base(); LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headings = new LinearLayout(this); headings.setOrientation(LinearLayout.VERTICAL);
        headings.addView(label("Calculator",24,Color.WHITE,true));
        TextView caption = label(scientific ? "SCIENTIFIC · DEGREES" : "EVERYDAY, SIMPLIFIED",11,muted,false); caption.setLetterSpacing(0.08f);
        caption.setOnLongClickListener(v -> { if (!preferences.contains("hash")) setupPin(); else toast("Enter your PIN and tap ="); return true; });
        headings.addView(caption,margins(new LinearLayout.LayoutParams(-2,-2),0,2,0,0));
        head.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        head.addView(round(R.drawable.ic_history,"Calculation history",muted,this::history),new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout fx = action(0,"fx",scientific ? background : mint,scientific ? mint : panel,() -> {
            scientific = !scientific; preferences.edit().putBoolean("scientific",scientific).apply(); calculator();
        }); fx.setContentDescription(scientific ? "Hide scientific keys" : "Show scientific keys");
        head.addView(fx,margins(new LinearLayout.LayoutParams(dp(56),dp(40)),8,0,0,0)); root.addView(head);
        display = text(expression.isEmpty() ? "0" : expression,48,Color.WHITE); display.setGravity(Gravity.BOTTOM | Gravity.END); display.setMaxLines(2);
        root.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        hint = label("",18,mint,false); hint.setGravity(Gravity.END); hint.setPadding(dp(4),dp(4),dp(4),dp(8)); root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        if (scientific) for (String[] row : new String[][]{{"(",")","^","√","π"},{"sin","cos","tan","log","ln"}}) keypad(row,dp(52),17);
        for (String[] row : new String[][]{{"AC","⌫","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"±","0",".","="}}) keypad(row,dp(70),24);
        refresh();
    }
    private void keypad(String[] keys, int height, int size) {
        LinearLayout row = new LinearLayout(this); root.addView(row,new LinearLayout.LayoutParams(-1,height));
        for (String key : keys) {
            Button b = button(key, () -> press(key)); b.setTextSize(size); b.setPadding(0,0,0,0);
            if (key.equals("=")) { b.setBackground(surface(mint,22)); b.setTextColor(background); }
            else if (key.matches("[0-9.]")) b.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,-1,1); params.setMargins(dp(4),dp(4),dp(4),dp(4)); row.addView(b,params);
        }
    }
    private void press(String key) {
        if (busy) return;
        switch (key) {
            case "AC": expression = ""; break;
            case "⌫": if (!expression.isEmpty()) expression = expression.substring(0,expression.length()-1); break;
            case "±": expression = expression.startsWith("−") ? expression.substring(1) : "−" + expression; break;
            case "=":
                if (expression.matches("[0-9]{6,12}") && preferences.contains("hash")) { verifyPin(expression); return; }
                String typed = expression; calculate();
                if (!expression.isEmpty() && !typed.equals(expression) && !typed.matches("[0-9.]+")) remember(typed,expression);
                break;
            default: if (expression.length() < 80) expression += key.matches("[a-z]+") ? key + "(" : key;
        }
        refresh();
    }
    private void calculate() { try { expression = Calculator.evaluate(expression); } catch (Exception e) { expression = ""; toast("Check your calculation"); } }
    /** Shows the running result while an expression is being typed; a bare number is left alone so PIN entry stays silent. */
    private void refresh() {
        display.setText(expression.isEmpty() ? "0" : expression); String result = "";
        if (!expression.matches("[0-9.]*")) try { result = "= " + Calculator.evaluate(expression); } catch (Exception ignored) { }
        hint.setText(result);
    }
    private ArrayList<String> historyEntries() {
        ArrayList<String> entries = new ArrayList<>();
        for (String line : preferences.getString("history","").split("\n")) if (!line.trim().isEmpty()) entries.add(line);
        return entries;
    }
    private void remember(String typed, String result) {
        ArrayList<String> entries = historyEntries(); entries.add(0,typed + "=" + result);
        while (entries.size() > 20) entries.remove(entries.size()-1);
        preferences.edit().putString("history",String.join("\n",entries)).apply();
    }
    private void history() {
        ArrayList<String> entries = historyEntries();
        LinearLayout content = sheet(R.drawable.ic_history,mint,mintWash,"Calculation history",entries.isEmpty() ? "Nothing saved yet" : count(entries.size(),"calculation") + " saved on this device");
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        for (String entry : entries) {
            int split = entry.lastIndexOf('='); if (split < 1) continue;
            String typed = entry.substring(0,split), result = entry.substring(split+1);
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackground(surface(panel,16));
            row.addView(label(typed,13,muted,false)); row.addView(label("= " + result,18,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,2,0,0));
            row.setOnClickListener(v -> { closeDialog(); expression = result.startsWith("-") ? "−" + result.substring(1) : result; refresh(); });
            list.addView(row,margins(new LinearLayout.LayoutParams(-1,-2),0,4,0,0));
        }
        if (entries.isEmpty()) list.addView(label("Calculations you run with = are kept here. PIN entries are never saved.",13,soft,false),margins(new LinearLayout.LayoutParams(-1,-2),12,4,12,8));
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(list);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,entries.size() > 5 ? dp(300) : -2));
        if (!entries.isEmpty()) content.addView(option(R.drawable.ic_delete,"Clear history","Removes all saved calculations",true,this::clearHistory));
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    private void clearHistory() { preferences.edit().remove("history").apply(); toast("Calculation history cleared"); }
    private byte[] hash(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(),salt,120000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); } finally { spec.clearPassword(); }
    }
    private void intro() {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_shield,mint,mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label("Your private calculator",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("Set a 6–12 digit PIN. Enter it in the calculator and tap = to open your photo and video vault.\n\nThere is no PIN recovery. Uninstalling the app or clearing its data removes the vault. Export important files first.",14,soft,false);
        body.setLineSpacing(dp(3),1); card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        Dialog dialog = popup(scrollable(card),Gravity.CENTER,false);
        card.addView(action(0,"Set PIN",background,mint,() -> { dialog.dismiss(); setupPin(); }),margins(new LinearLayout.LayoutParams(-1,dp(48)),0,20,0,0));
    }
    private boolean savePin(String pin) throws Exception {
        byte[] salt = new byte[32]; new SecureRandom().nextBytes(salt);
        String encoded = Base64.getEncoder().encodeToString(hash(pin,salt));
        return preferences.edit().putString("salt",Base64.getEncoder().encodeToString(salt)).putString("hash",encoded).commit();
    }
    private void setupPin() {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_key,mint,mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label("Create your PIN",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("6–12 digits. Enter it in the calculator and tap = to open your vault. There is no recovery.",14,soft,false); body.setLineSpacing(dp(2),1);
        card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        EditText first = pinField("PIN (6–12 digits)"), second = pinField("Confirm PIN");
        card.addView(pinRow(first),margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0)); card.addView(pinRow(second),margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0));
        TextView error = label("",13,danger,false); card.addView(error,margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        LinearLayout save = action(0,"Save PIN",background,mint,null); card.addView(save,margins(new LinearLayout.LayoutParams(-1,dp(48)),0,16,0,0));
        Dialog dialog = popup(scrollable(card),Gravity.CENTER,false);
        save.setOnClickListener(v -> {
            String pin = first.getText().toString();
            if (!pin.matches("[0-9]{6,12}") || !pin.equals(second.getText().toString())) { error.setText("Use matching 6–12 digit PINs"); return; }
            save.setEnabled(false); error.setText("Saving…");
            worker.execute(() -> { try {
                if (!savePin(pin)) throw new IOException();
                runOnUiThread(() -> { dialog.dismiss(); toast("PIN saved. Enter it and tap = to unlock."); });
            } catch (Exception e) { runOnUiThread(() -> { save.setEnabled(true); error.setText("Could not save PIN. Try again."); }); } });
        });
    }
    private void changePin() {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_key,mint,mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label("Change PIN",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("Your vault files stay as they are. Only the PIN that opens them changes.",14,soft,false); body.setLineSpacing(dp(2),1);
        card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        EditText current = pinField("Current PIN"), first = pinField("New PIN (6–12 digits)"), second = pinField("Confirm new PIN");
        card.addView(pinRow(current),margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
        card.addView(pinRow(first),margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0)); card.addView(pinRow(second),margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0));
        TextView error = label("",13,danger,false); card.addView(error,margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        LinearLayout buttons = new LinearLayout(this);
        LinearLayout save = action(0,"Save",background,mint,null);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(save,margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
        save.setOnClickListener(v -> {
            String pin = first.getText().toString(), old = current.getText().toString();
            if (!pin.matches("[0-9]{6,12}") || !pin.equals(second.getText().toString())) { error.setText("Use matching 6–12 digit PINs"); return; }
            save.setEnabled(false); error.setText("Checking…");
            worker.execute(() -> {
                boolean matches = false;
                try { matches = MessageDigest.isEqual(hash(old,Base64.getDecoder().decode(preferences.getString("salt",""))),Base64.getDecoder().decode(preferences.getString("hash",""))); } catch (Exception ignored) { }
                boolean saved = false;
                if (matches) try { saved = savePin(pin); } catch (Exception ignored) { }
                final boolean valid = matches, stored = saved;
                runOnUiThread(() -> {
                    save.setEnabled(true);
                    if (stored) { closeDialog(); toast("PIN changed. Use the new PIN from now on."); }
                    else error.setText(valid ? "Could not save the new PIN. Try again." : "Current PIN is not correct");
                });
            });
        });
    }
    private void verifyPin(String pin) {
        if (System.currentTimeMillis() < preferences.getLong("retry",0)) { expression=""; refresh(); toast("Please wait before trying again"); return; }
        busy = true; int token = session;
        worker.execute(() -> {
            boolean matches = false;
            try { matches = MessageDigest.isEqual(hash(pin,Base64.getDecoder().decode(preferences.getString("salt",""))),Base64.getDecoder().decode(preferences.getString("hash",""))); } catch (Exception ignored) { }
            final boolean valid = matches;
            runOnUiThread(() -> {
                busy = false; expression="";
                if (token != session || !foreground) return;
                if (valid) { preferences.edit().putInt("attempts",0).putLong("retry",0).apply(); unlocked=true; vault(); importPending(); }
                else {
                    int attempts = preferences.getInt("attempts",0)+1;
                    preferences.edit().putInt("attempts",attempts).putLong("retry",attempts >= 5 ? System.currentTimeMillis()+30000 : 0).apply();
                    expression=pin; calculate(); refresh();
                }
            });
        });
    }
    /** Items are numbered per type from the newest, then re-ordered for display, so a name never moves with the sort. */
    private ArrayList<Item> items() {
        File[] files = store.list(); boolean[] clips = new boolean[files.length]; int photos = 0, videos = 0;
        for (int i=0;i<files.length;i++) {
            String mime; try { mime=store.mime(files[i]); } catch (IOException e) { mime="unknown"; }
            clips[i] = mime.startsWith("video/"); if (clips[i]) videos++; else photos++;
        }
        ArrayList<Item> items = new ArrayList<>();
        for (int i=0,photo=photos,clip=videos;i<files.length;i++) {
            String meta = android.text.format.Formatter.formatShortFileSize(this,files[i].length()) + "  ·  " + DateUtils.formatDateTime(this,files[i].lastModified(),DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
            items.add(new Item(files[i],clips[i],clips[i] ? "Video " + clip-- : "Photo " + photo--,meta));
        }
        switch (preferences.getInt("sort",0)) {
            case 1: Collections.reverse(items); break;
            case 2: Collections.sort(items,(a,b) -> Long.compare(b.file.length(),a.file.length())); break;
            case 3: Collections.sort(items,(a,b) -> Long.compare(a.file.length(),b.file.length())); break;
        }
        return items;
    }
    /** Every unlocked screen is this shell: the current tab above, the tab bar below. */
    private void vault() {
        if (!unlocked) return;
        base();
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        switch (tab) {
            case CONTACTS: contactsTab(page); break;
            case NOTES: notesTab(page); break;
            case BROWSER: browserTab(page); break;
            default: mediaTab(page); break;
        }
        if (!selecting) root.addView(tabBar(),margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0));
    }
    private LinearLayout tabBar() {
        LinearLayout bar = new LinearLayout(this); bar.setBackground(shape(panel,24)); bar.setPadding(dp(6),dp(6),dp(6),dp(6));
        bar.addView(tabButton(R.drawable.ic_photo,"Photos",PHOTOS),new LinearLayout.LayoutParams(0,dp(54),1));
        bar.addView(tabButton(R.drawable.ic_video,"Videos",VIDEOS),new LinearLayout.LayoutParams(0,dp(54),1));
        bar.addView(tabButton(R.drawable.ic_contact,"Contacts",CONTACTS),new LinearLayout.LayoutParams(0,dp(54),1));
        bar.addView(tabButton(R.drawable.ic_note,"Notes",NOTES),new LinearLayout.LayoutParams(0,dp(54),1));
        bar.addView(tabButton(R.drawable.ic_globe,"Browser",BROWSER),new LinearLayout.LayoutParams(0,dp(54),1));
        return bar;
    }
    private LinearLayout tabButton(int drawable, String name, int index) {
        boolean active = tab == index;
        LinearLayout button = new LinearLayout(this); button.setOrientation(LinearLayout.VERTICAL); button.setGravity(Gravity.CENTER);
        button.setBackground(surface(active ? mintWash : Color.TRANSPARENT,18));
        button.addView(glyph(drawable,active ? mint : muted),new LinearLayout.LayoutParams(dp(22),dp(22)));
        // Five captions share the width, so a large system font size truncates one rather than wrapping it out of the button.
        TextView caption = single(label(name,11,active ? mint : muted,active));
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.addView(caption,margins(new LinearLayout.LayoutParams(-2,-2),0,4,0,0));
        button.setContentDescription(active ? name + ", showing" : name);
        button.setOnClickListener(v -> openTab(index));
        return button;
    }
    /** Contacts and notes are decrypted before their tab opens, so a failed read leaves the current tab on screen. */
    private void openTab(int index) {
        if (!unlocked || tab == index) return;
        selecting = false; selection.clear();
        // A detached browser keeps running scripts and timers, so it is stopped while another tab is open.
        if (tab == BROWSER && browser != null) { browser.onPause(); browser.pauseTimers(); }
        if (index == CONTACTS) { withContacts(() -> { tab = CONTACTS; vault(); }); return; }
        if (index == NOTES) { withNotes(() -> { tab = NOTES; vault(); }); return; }
        tab = index; vault();
    }
    private void mediaTab(LinearLayout page) {
        boolean clips = tab == VIDEOS;
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : items()) if (item.video == clips) items.add(item);
        boolean grid = preferences.getBoolean("grid",false);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); page.addView(top);
        if (selecting) {
            top.addView(round(R.drawable.ic_close,"Leave selection",Color.WHITE,() -> { selecting=false; selection.clear(); vault(); }),new LinearLayout.LayoutParams(dp(40),dp(40)));
            top.addView(label(count(selection.size(),"item") + " selected",14,mint,true),margins(new LinearLayout.LayoutParams(0,-2,1),12,0,8,0));
            boolean all = selection.size() == items.size();
            top.addView(action(0,all ? "Clear" : "Select all",mint,panel,() -> {
                selection.clear(); if (!all) for (Item item : items) selection.add(item.file.getName()); vault();
            }),new LinearLayout.LayoutParams(-2,dp(40)));
        } else {
            top.addView(glyph(R.drawable.ic_shield,mint),new LinearLayout.LayoutParams(dp(16),dp(16)));
            top.addView(banner("Encrypted on device"),margins(new LinearLayout.LayoutParams(0,-2,1),6,0,8,0));
            top.addView(round(R.drawable.ic_tune,"Vault settings",mint,this::settings),margins(new LinearLayout.LayoutParams(dp(40),dp(40)),0,0,8,0));
            top.addView(action(R.drawable.ic_lock,"Lock",mint,panel,this::lock),new LinearLayout.LayoutParams(-2,dp(40)));
        }
        page.addView(label(selecting ? "Choose items" : clips ? "Videos" : "Photos",28,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,14,0,0));
        String summary = items.isEmpty() ? (clips ? "No videos yet" : "No photos yet") : count(items.size(),clips ? "video" : "photo") + " in the vault";
        page.addView(label(selecting ? "Tap items to pick them. Long press works anywhere in the list." : summary,14,soft,false),margins(new LinearLayout.LayoutParams(-1,-2),0,4,0,0));
        if (selecting) {
            LinearLayout bulk = new LinearLayout(this);
            bulk.addView(action(R.drawable.ic_export,"Export",mint,panel,this::exportSelected),new LinearLayout.LayoutParams(0,dp(56),1));
            bulk.addView(action(R.drawable.ic_delete,"Delete",background,danger,this::deleteSelected),margins(new LinearLayout.LayoutParams(0,dp(56),1),12,0,0,0));
            page.addView(bulk,margins(new LinearLayout.LayoutParams(-1,-2),0,20,0,0));
        } else page.addView(action(R.drawable.ic_add,clips ? "Add videos" : "Add photos",background,mint,this::pick),margins(new LinearLayout.LayoutParams(-1,dp(56)),0,20,0,0));
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        page.addView(scroll,margins(new LinearLayout.LayoutParams(-1,0,1),0,12,0,0));
        if (!selecting) {
            LinearLayout note = new LinearLayout(this); note.setPadding(dp(14),dp(12),dp(14),dp(12)); note.setBackground(shape(panel,16));
            TextView tip = label(clearsOriginals()
                ? "An import is copied here first, then the original is taken out of your gallery — Android may ask you first. Cloud backups and your gallery's trash are not touched; clear those yourself."
                : "Imports are copies. After checking them here, remove originals from your gallery and its trash if you want them hidden there.",13,soft,false); tip.setLineSpacing(dp(2),1);
            note.addView(glyph(R.drawable.ic_info,soft),margins(new LinearLayout.LayoutParams(dp(18),dp(18)),0,1,0,0)); note.addView(tip,margins(new LinearLayout.LayoutParams(0,-2,1),12,0,0,0)); list.addView(note);
        }
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER_HORIZONTAL); empty.setPadding(dp(24),dp(48),dp(24),dp(24));
            empty.addView(tile(clips ? R.drawable.ic_video : R.drawable.ic_photo,clips ? lilac : mint,clips ? lilacWash : mintWash,64),new LinearLayout.LayoutParams(dp(64),dp(64)));
            TextView heading = label("A little space, just for you",20,Color.WHITE,true); heading.setGravity(Gravity.CENTER); empty.addView(heading,margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
            TextView start = label((clips ? "Add a video to get started." : "Add a photo to get started.") + "\nYour PIN opens this space from the calculator.",14,soft,false);
            start.setGravity(Gravity.CENTER); start.setLineSpacing(dp(2),1); empty.addView(start,margins(new LinearLayout.LayoutParams(-2,-2),0,6,0,0)); list.addView(empty);
        } else {
            LinearLayout section = new LinearLayout(this); section.setGravity(Gravity.CENTER_VERTICAL);
            TextView order = label(new String[]{"NEWEST FIRST","OLDEST FIRST","LARGEST FIRST","SMALLEST FIRST"}[preferences.getInt("sort",0)],12,muted,true); order.setLetterSpacing(0.1f);
            section.addView(order,margins(new LinearLayout.LayoutParams(0,-2,1),4,0,8,0));
            section.addView(round(R.drawable.ic_sort,"Sort items",muted,this::sortOptions),new LinearLayout.LayoutParams(dp(36),dp(36)));
            section.addView(round(grid ? R.drawable.ic_list : R.drawable.ic_grid,grid ? "List view" : "Grid view",muted,() -> { preferences.edit().putBoolean("grid",!grid).apply(); vault(); }),margins(new LinearLayout.LayoutParams(dp(36),dp(36)),6,0,0,0));
            list.addView(section,margins(new LinearLayout.LayoutParams(-1,-2),0,20,0,4));
            if (grid) {
                int columns = 3, cell = (getResources().getDisplayMetrics().widthPixels - dp(40) - dp(8)*(columns-1)) / columns;
                for (int i=0;i<items.size();i+=columns) {
                    LinearLayout row = new LinearLayout(this); list.addView(row,margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
                    for (int column=0;column<columns;column++) {
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,cell,1); if (column > 0) params.setMarginStart(dp(8));
                        row.addView(i+column < items.size() ? gridCell(items.get(i+column),cell) : new View(this),params);
                    }
                }
            } else for (Item item : items) list.addView(mediaRow(item),margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        }
        list.addView(footer("Keep your PIN safe. Export files before uninstalling."),margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
    }
    /** Closing reminder, kept inside the scrolling list so the tab bar is the only thing pinned to the bottom. */
    private LinearLayout footer(String message) {
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER);
        footer.addView(glyph(R.drawable.ic_lock,muted),new LinearLayout.LayoutParams(dp(12),dp(12)));
        footer.addView(label(message,12,muted,false),margins(new LinearLayout.LayoutParams(-2,-2),6,0,0,0));
        return footer;
    }
    private LinearLayout mediaRow(Item item) {
        boolean chosen = selection.contains(item.file.getName());
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable fill = shape(chosen ? mintWash : panel,20); if (chosen) fill.setStroke(dp(2),mint);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24ffffff),fill,shape(Color.WHITE,20)));
        ImageView thumb = tile(item.video ? R.drawable.ic_video : R.drawable.ic_photo,item.video ? lilac : mint,item.video ? lilacWash : mintWash,48);
        row.addView(thumb,new LinearLayout.LayoutParams(dp(48),dp(48))); if (!item.video) thumbnail(thumb,item.file,dp(48));
        row.addView(stack(item.name,item.meta,Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),14,0,8,0));
        row.addView(mark(chosen),new LinearLayout.LayoutParams(dp(22),dp(22)));
        return touchable(row,item);
    }
    private View mark(boolean chosen) {
        if (!selecting) return glyph(R.drawable.ic_chevron,muted);
        if (chosen) { ImageView check = glyph(R.drawable.ic_check,background); check.setBackground(shape(mint,11)); check.setPadding(dp(3),dp(3),dp(3),dp(3)); return check; }
        View empty = new View(this); GradientDrawable ring = shape(Color.TRANSPARENT,11); ring.setStroke(dp(2),raised); empty.setBackground(ring); return empty;
    }
    private FrameLayout gridCell(Item item, int cell) {
        boolean chosen = selection.contains(item.file.getName());
        FrameLayout frame = new FrameLayout(this); frame.setBackground(shape(item.video ? lilacWash : panel,18)); frame.setClipToOutline(true);
        ImageView thumb = new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER); thumb.setImageResource(item.video ? R.drawable.ic_video : R.drawable.ic_photo);
        thumb.setColorFilter(item.video ? lilac : mint); frame.addView(thumb,new FrameLayout.LayoutParams(-1,-1));
        if (!item.video) thumbnail(thumb,item.file,cell);
        TextView badge = label(item.name,12,Color.WHITE,true); badge.setPadding(dp(8),dp(4),dp(8),dp(6)); badge.setBackgroundColor(0xaa000000);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-1,-2); badgeParams.gravity = Gravity.BOTTOM; frame.addView(badge,badgeParams);
        if (chosen) {
            GradientDrawable ring = shape(Color.TRANSPARENT,18); ring.setStroke(dp(3),mint); frame.setForeground(ring);
            View check = mark(true); FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(22),dp(22));
            checkParams.gravity = Gravity.TOP | Gravity.END; checkParams.topMargin = dp(6); checkParams.setMarginEnd(dp(6)); frame.addView(check,checkParams);
        }
        touchable(frame,item); return frame;
    }
    private <T extends View> T touchable(T view, Item item) {
        view.setOnClickListener(v -> { if (selecting) choose(item); else actions(item); });
        view.setOnLongClickListener(v -> { selecting = true; choose(item); return true; });
        return view;
    }
    private void choose(Item item) {
        if (!selection.remove(item.file.getName())) selection.add(item.file.getName());
        if (selection.isEmpty()) selecting = false;
        vault();
    }
    /** Decrypts a photo in memory for its list or grid tile; nothing is written to disk. */
    private void thumbnail(ImageView view, File file, int pixels) {
        String key = file.getName() + "@" + pixels; Bitmap cached = thumbnails.get(key);
        if (cached != null) { paint(view,cached); return; }
        int token = session; view.setTag(key);
        thumbnailPool.execute(() -> {
            Bitmap bitmap = decodeThumbnail(file,pixels);
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (!unlocked || token != session) { bitmap.recycle(); return; }
                thumbnails.put(key,bitmap); if (key.equals(view.getTag())) paint(view,bitmap);
            });
        });
    }
    private void paint(ImageView view, Bitmap bitmap) {
        view.setColorFilter(null); view.setPadding(0,0,0,0); view.setScaleType(ImageView.ScaleType.CENTER_CROP); view.setClipToOutline(true); view.setImageBitmap(bitmap);
    }
    private Bitmap decodeThumbnail(File file, int pixels) {
        if (file.length() > 40L*1024*1024) return null;
        byte[] data = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); store.decrypt(file,bytes); data = bytes.toByteArray();
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(data,0,data.length,options);
            options.inSampleSize = 1;
            while (options.outWidth/options.inSampleSize > pixels*2 && options.outHeight/options.inSampleSize > pixels*2) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false; Bitmap full = BitmapFactory.decodeByteArray(data,0,data.length,options);
            return full == null ? null : ThumbnailUtils.extractThumbnail(full,pixels,pixels,ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        } catch (Exception e) { return null; }
        finally { if (data != null) Arrays.fill(data,(byte)0); }
    }
    /** Runs an action once the decrypted contact list is in memory; the file is small but the key comes from Keystore. */
    private void withContacts(Runnable action) {
        if (!unlocked) return;
        if (contacts != null) { action.run(); return; }
        int token = session;
        worker.execute(() -> {
            ArrayList<Contacts.Entry> loaded = null;
            try { loaded = store.loadContacts(); } catch (Exception ignored) { }
            final ArrayList<Contacts.Entry> result = loaded;
            runOnUiThread(() -> {
                if (!unlocked || token != session) return;
                if (result == null) { toast("Could not open your contacts"); return; }
                contacts = result; action.run();
            });
        });
    }
    /** Display order only; the stored file keeps the order entries were added. Unnamed entries sort by their number. */
    private ArrayList<Contacts.Entry> sortedContacts() {
        ArrayList<Contacts.Entry> list = new ArrayList<>(contacts);
        Collections.sort(list,(a,b) -> a.title().compareToIgnoreCase(b.title()));
        return list;
    }
    private boolean directCalling() {
        return preferences.getBoolean("direct",false) && checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }
    private void contactsTab(LinearLayout page) {
        if (contacts == null) { withContacts(this::vault); return; }
        ArrayList<Contacts.Entry> list = sortedContacts();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); page.addView(top);
        top.addView(glyph(R.drawable.ic_shield,mint),new LinearLayout.LayoutParams(dp(16),dp(16)));
        top.addView(banner("Encrypted on device"),margins(new LinearLayout.LayoutParams(0,-2,1),6,0,8,0));
        top.addView(round(R.drawable.ic_tune,"Vault settings",mint,this::settings),margins(new LinearLayout.LayoutParams(dp(40),dp(40)),0,0,8,0));
        top.addView(action(R.drawable.ic_lock,"Lock",mint,panel,this::lock),new LinearLayout.LayoutParams(-2,dp(40)));
        page.addView(label("Contacts",28,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,14,0,0));
        page.addView(label(list.isEmpty() ? "No contacts yet" : count(list.size(),"contact") + " saved",14,soft,false),margins(new LinearLayout.LayoutParams(-1,-2),0,4,0,0));
        LinearLayout add = new LinearLayout(this);
        add.addView(action(R.drawable.ic_contact_add,"Add",background,mint,() -> contactEditor(Contacts.entry("","","",""))),new LinearLayout.LayoutParams(0,dp(56),1));
        add.addView(action(R.drawable.ic_contact,"Import",mint,panel,this::pickContact),margins(new LinearLayout.LayoutParams(0,dp(56),1),12,0,0,0));
        page.addView(add,margins(new LinearLayout.LayoutParams(-1,-2),0,20,0,0));
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        page.addView(scroll,margins(new LinearLayout.LayoutParams(-1,0,1),0,12,0,0));
        LinearLayout note = new LinearLayout(this); note.setPadding(dp(14),dp(12),dp(14),dp(12)); note.setBackground(shape(panel,16));
        TextView tip = label("A call still appears in your phone's call log and on your carrier bill. The vault locks while you are on the call.",13,soft,false); tip.setLineSpacing(dp(2),1);
        note.addView(glyph(R.drawable.ic_info,soft),margins(new LinearLayout.LayoutParams(dp(18),dp(18)),0,1,0,0));
        note.addView(tip,margins(new LinearLayout.LayoutParams(0,-2,1),12,0,0,0)); body.addView(note);
        body.addView(callingOption(),margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        if (list.isEmpty()) {
            LinearLayout empty = new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER_HORIZONTAL); empty.setPadding(dp(24),dp(40),dp(24),dp(24));
            empty.addView(tile(R.drawable.ic_contact,lilac,lilacWash,64),new LinearLayout.LayoutParams(dp(64),dp(64)));
            TextView heading = label("Numbers only you can see",20,Color.WHITE,true); heading.setGravity(Gravity.CENTER); empty.addView(heading,margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
            TextView start = label("Add a number here or import one from your phone.\nNothing is added to your phone's contact list.",14,soft,false);
            start.setGravity(Gravity.CENTER); start.setLineSpacing(dp(2),1); empty.addView(start,margins(new LinearLayout.LayoutParams(-2,-2),0,6,0,0)); body.addView(empty);
        } else {
            TextView order = label("BY NAME",12,muted,true); order.setLetterSpacing(0.1f); order.setPadding(dp(4),0,dp(4),0);
            body.addView(order,margins(new LinearLayout.LayoutParams(-1,-2),0,20,0,4));
            for (Contacts.Entry entry : list) body.addView(contactRow(entry),margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        }
        body.addView(footer("Contacts are lost with the vault. Keep a copy elsewhere."),margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
    }
    private LinearLayout callingOption() {
        boolean direct = directCalling();
        return option(R.drawable.ic_call,direct ? "Calls start from the vault" : "Calls open in your dialer",
            direct ? "Tap to hand calls back to the dialer" : "Tap to dial without leaving the vault. Android asks once and the vault locks while it does.",false,this::toggleDirectCall);
    }
    private LinearLayout originalsOption() {
        boolean clears = clearsOriginals();
        return option(clears ? R.drawable.ic_hide : R.drawable.ic_photo,clears ? "Originals leave your gallery" : "Originals stay in your gallery",
            clears ? "Removed once the vault copy is written. Android may ask first. Tap to keep them instead."
                : "Tap to have imports removed from the gallery after the vault copy is written.",false,
            () -> { preferences.edit().putBoolean("clearOriginals",!clears).apply(); vault(); });
    }
    private LinearLayout contactRow(Contacts.Entry entry) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(surface(panel,20));
        TextView initial = label(entry.name.isEmpty() ? "#" : entry.name.substring(0,1).toUpperCase(Locale.US),18,lilac,true);
        initial.setGravity(Gravity.CENTER); initial.setBackground(shape(lilacWash,14));
        row.addView(initial,new LinearLayout.LayoutParams(dp(48),dp(48)));
        row.addView(stack(entry.title(),entry.name.isEmpty() ? (entry.note.isEmpty() ? "No name saved" : entry.note) : entry.number,Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),14,0,8,0));
        ImageView call = glyph(R.drawable.ic_call,background); call.setBackground(surface(mint,20)); call.setPadding(dp(10),dp(10),dp(10),dp(10));
        call.setContentDescription("Call " + entry.title()); call.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        call.setOnClickListener(v -> placeCall(entry)); row.addView(call,new LinearLayout.LayoutParams(dp(40),dp(40)));
        row.setOnClickListener(v -> contactActions(entry)); return row;
    }
    private void contactActions(Contacts.Entry entry) {
        LinearLayout content = sheet(R.drawable.ic_contact,lilac,lilacWash,entry.title(),entry.number);
        if (!entry.note.isEmpty()) {
            TextView note = label(entry.note,13,soft,false); note.setLineSpacing(dp(2),1);
            content.addView(note,margins(new LinearLayout.LayoutParams(-1,-2),12,0,12,10));
        }
        content.addView(option(R.drawable.ic_call,"Call",directCalling() ? "Starts the call and locks the vault" : "Opens your dialer with the number filled in",false,() -> placeCall(entry)));
        content.addView(option(R.drawable.ic_copy,"Copy number","Puts it on the clipboard until you replace it",false,() -> copyNumber(entry)));
        content.addView(option(R.drawable.ic_edit,"Edit","Change the name, number or note",false,() -> contactEditor(entry)));
        content.addView(option(R.drawable.ic_delete,"Delete contact","Removes it from the vault only",true,() -> confirmDeleteContact(entry)));
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    /** An entry with an empty id is a new contact; anything else replaces the saved record with that id. */
    private void contactEditor(Contacts.Entry existing) {
        boolean fresh = existing.id.isEmpty();
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_contact,mint,mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label(fresh ? "New contact" : "Edit contact",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("Kept encrypted in the vault. It is not added to your phone's contact list.",14,soft,false); body.setLineSpacing(dp(2),1);
        card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        EditText name = field("Name",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText number = field("Phone number",InputType.TYPE_CLASS_PHONE);
        EditText note = field("Note (optional)",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(existing.name); number.setText(existing.number); note.setText(existing.note);
        card.addView(name,margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
        card.addView(number,margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0));
        card.addView(note,margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0));
        TextView error = label("",13,danger,false); card.addView(error,margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        LinearLayout buttons = new LinearLayout(this);
        LinearLayout save = action(0,"Save",background,mint,null);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(save,margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
        save.setOnClickListener(v -> {
            Contacts.Entry edited = Contacts.entry(fresh ? UUID.randomUUID().toString() : existing.id,
                name.getText().toString(),number.getText().toString(),note.getText().toString());
            if (Contacts.dialable(edited.number).isEmpty()) { error.setText("Enter a phone number"); return; }
            closeDialog();
            if (!unlocked || contacts == null) return;
            int at = -1; for (int i=0;i<contacts.size();i++) if (contacts.get(i).id.equals(edited.id)) at = i;
            if (at < 0) contacts.add(edited); else contacts.set(at,edited);
            tab = CONTACTS; vault(); commitContacts(fresh ? "Contact saved in the vault" : "Contact updated");
        });
    }
    private void confirmDeleteContact(Contacts.Entry entry) {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_delete,danger,dangerWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label("Delete " + entry.title() + "?",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("This removes the contact from the vault. Your phone's own contact list is not changed.",14,soft,false);
        body.setLineSpacing(dp(2),1); card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(action(0,"Delete",background,danger,() -> {
            closeDialog();
            if (!unlocked || contacts == null) return;
            for (Iterator<Contacts.Entry> each = contacts.iterator(); each.hasNext(); ) if (each.next().id.equals(entry.id)) each.remove();
            vault(); commitContacts("Contact deleted");
        }),margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,24,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
    }
    /** Re-encrypts the whole list. A failed write leaves the file alone, so the memory copy is dropped and re-read. */
    private void commitContacts(String message) {
        ArrayList<Contacts.Entry> snapshot = new ArrayList<>(contacts); int token = session;
        worker.execute(() -> {
            boolean ok = true;
            try { store.saveContacts(snapshot); } catch (Exception e) { ok = false; }
            final boolean saved = ok;
            runOnUiThread(() -> {
                if (token != session) return;
                if (saved) { toast(message); return; }
                contacts = null; toast("Could not save contacts. Nothing was changed.");
                if (tab == CONTACTS) { tab = PHOTOS; vault(); }
            });
        });
    }
    // The permission is re-checked here rather than trusted from the setting, so a revoked grant falls back to the dialer.
    private void placeCall(Contacts.Entry entry) {
        String number = Contacts.dialable(entry.number);
        if (number.isEmpty()) { toast("This contact has no number to call"); return; }
        Uri target = Uri.fromParts("tel",number,null);
        try {
            if (preferences.getBoolean("direct",false) && checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                startActivity(new Intent(Intent.ACTION_CALL,target));
            else startActivity(new Intent(Intent.ACTION_DIAL,target));
        } catch (SecurityException e) { toast("Calling permission was withdrawn. Turn direct calling on again."); }
        catch (Exception e) { toast("No app on this device can place calls"); }
    }
    /** The clip is flagged sensitive on Android 13 and newer, so the system does not preview what was copied. */
    private void copy(String description, String value, String message) {
        ClipData clip = ClipData.newPlainText(description,value);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle(); extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true); clip.getDescription().setExtras(extras);
        }
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        toast(message);
    }
    private void copyNumber(Contacts.Entry entry) {
        copy("Phone number",entry.number,"Number copied. It stays on the clipboard until something replaces it.");
    }
    private void toggleDirectCall() {
        if (preferences.getBoolean("direct",false)) {
            preferences.edit().putBoolean("direct",false).apply();
            toast("Calls will open in your phone dialer"); if (tab == CONTACTS) vault(); return;
        }
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            preferences.edit().putBoolean("direct",true).apply();
            toast("Calls will start straight from the vault"); if (tab == CONTACTS) vault(); return;
        }
        requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE},20);
    }
    // The picker runs in another app, so it locks the vault; the chosen contact is read once the PIN comes back.
    private void pickContact() {
        try { startActivityForResult(new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI),13); }
        catch (Exception e) { toast("No contacts app to pick from"); }
    }
    private void importContact(Uri picked) {
        worker.execute(() -> {
            String[] columns = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
            String name = "", number = "";
            try (android.database.Cursor cursor = getContentResolver().query(picked,columns,null,null,null)) {
                if (cursor != null && cursor.moveToFirst()) { name = cursor.getString(0); number = cursor.getString(1); }
            } catch (Exception ignored) { }
            final String pickedName = name, pickedNumber = number;
            runOnUiThread(() -> {
                if (!unlocked) return;
                if (Contacts.dialable(pickedNumber).isEmpty()) { toast("Could not read a number from that contact"); return; }
                withContacts(() -> { tab = CONTACTS; vault(); contactEditor(Contacts.entry("",pickedName,pickedNumber,"")); });
            });
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request != 20) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        preferences.edit().putBoolean("direct",granted).apply();
        toast(granted ? "Direct calling is on. Unlock the vault to use it." : "Permission declined. Calls will open in your phone dialer.");
    }
    private void withNotes(Runnable action) {
        if (!unlocked) return;
        if (notes != null) { action.run(); return; }
        int token = session;
        worker.execute(() -> {
            ArrayList<Notes.Entry> loaded = null;
            try { loaded = store.loadNotes(); } catch (Exception ignored) { }
            final ArrayList<Notes.Entry> result = loaded;
            runOnUiThread(() -> {
                if (!unlocked || token != session) return;
                if (result == null) { toast("Could not open your notes"); return; }
                notes = result; action.run();
            });
        });
    }
    /** Display order only; the stored file keeps the order notes were added. The one edited last is shown first. */
    private ArrayList<Notes.Entry> sortedNotes() {
        ArrayList<Notes.Entry> list = new ArrayList<>(notes);
        Collections.sort(list,(a,b) -> Long.compare(b.updated,a.updated));
        return list;
    }
    private void notesTab(LinearLayout page) {
        if (notes == null) { withNotes(this::vault); return; }
        ArrayList<Notes.Entry> list = sortedNotes();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); page.addView(top);
        top.addView(glyph(R.drawable.ic_shield,mint),new LinearLayout.LayoutParams(dp(16),dp(16)));
        top.addView(banner("Encrypted on device"),margins(new LinearLayout.LayoutParams(0,-2,1),6,0,8,0));
        top.addView(round(R.drawable.ic_tune,"Vault settings",mint,this::settings),margins(new LinearLayout.LayoutParams(dp(40),dp(40)),0,0,8,0));
        top.addView(action(R.drawable.ic_lock,"Lock",mint,panel,this::lock),new LinearLayout.LayoutParams(-2,dp(40)));
        page.addView(label("Notes",28,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,14,0,0));
        page.addView(label(list.isEmpty() ? "No notes yet" : count(list.size(),"note") + " saved",14,soft,false),margins(new LinearLayout.LayoutParams(-1,-2),0,4,0,0));
        page.addView(action(R.drawable.ic_add,"New note",background,mint,() -> noteEditor(Notes.entry("","","",0L))),margins(new LinearLayout.LayoutParams(-1,dp(56)),0,20,0,0));
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        page.addView(scroll,margins(new LinearLayout.LayoutParams(-1,0,1),0,12,0,0));
        if (list.isEmpty()) {
            LinearLayout empty = new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER_HORIZONTAL); empty.setPadding(dp(24),dp(40),dp(24),dp(24));
            empty.addView(tile(R.drawable.ic_note,lilac,lilacWash,64),new LinearLayout.LayoutParams(dp(64),dp(64)));
            TextView heading = label("Words only you can read",20,Color.WHITE,true); heading.setGravity(Gravity.CENTER); empty.addView(heading,margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
            TextView start = label("An address, a hint, anything you would rather not leave in a notes app.\nYour PIN opens this space from the calculator.",14,soft,false);
            start.setGravity(Gravity.CENTER); start.setLineSpacing(dp(2),1); empty.addView(start,margins(new LinearLayout.LayoutParams(-2,-2),0,6,0,0)); body.addView(empty);
        } else {
            TextView order = label("LAST EDITED FIRST",12,muted,true); order.setLetterSpacing(0.1f); order.setPadding(dp(4),0,dp(4),0);
            body.addView(order,margins(new LinearLayout.LayoutParams(-1,-2),0,12,0,4));
            for (Notes.Entry entry : list) body.addView(noteRow(entry),margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        }
        body.addView(footer("Notes are lost with the vault. Keep a copy elsewhere."),margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
    }
    /** The heading and preview are truncated to one line each, so a long note does not stretch its row. */
    private LinearLayout noteRow(Notes.Entry entry) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(surface(panel,20));
        row.addView(tile(R.drawable.ic_note,lilac,lilacWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout written = new LinearLayout(this); written.setOrientation(LinearLayout.VERTICAL);
        written.addView(single(label(entry.heading(),16,Color.WHITE,true)));
        written.addView(single(label(entry.preview(),13,soft,false)),margins(new LinearLayout.LayoutParams(-2,-2),0,2,0,0));
        written.addView(label(edited(entry),12,muted,false),margins(new LinearLayout.LayoutParams(-2,-2),0,4,0,0));
        row.addView(written,margins(new LinearLayout.LayoutParams(0,-2,1),14,0,8,0));
        row.addView(glyph(R.drawable.ic_chevron,muted),new LinearLayout.LayoutParams(dp(22),dp(22)));
        row.setOnClickListener(v -> noteActions(entry)); return row;
    }
    private String edited(Notes.Entry entry) {
        return "Edited " + DateUtils.getRelativeTimeSpanString(entry.updated,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS);
    }
    private void noteActions(Notes.Entry entry) {
        LinearLayout content = sheet(R.drawable.ic_note,lilac,lilacWash,entry.heading(),edited(entry));
        content.addView(option(R.drawable.ic_view,"Read","Opens the whole note inside the vault",false,() -> noteViewer(entry)));
        content.addView(option(R.drawable.ic_edit,"Edit","Change the title or the text",false,() -> noteEditor(entry)));
        content.addView(option(R.drawable.ic_copy,"Copy text","Puts it on the clipboard until you replace it",false,() -> copyNote(entry)));
        content.addView(option(R.drawable.ic_delete,"Delete note","Removes it from the vault only",true,() -> confirmDeleteNote(entry)));
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    /** Reading without the keyboard in the way; editing is one tap further on. */
    private void noteViewer(Notes.Entry entry) {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_note,lilac,lilacWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label(entry.heading(),20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        card.addView(label(edited(entry),13,muted,false),margins(new LinearLayout.LayoutParams(-2,-2),0,4,0,0));
        TextView written = label(entry.body.isEmpty() ? "This note has no text." : entry.body,15,entry.body.isEmpty() ? muted : Color.WHITE,false);
        written.setLineSpacing(dp(4),1); written.setTextIsSelectable(true);
        card.addView(written,margins(new LinearLayout.LayoutParams(-1,-2),0,14,0,0));
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(action(0,"Close",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(action(0,"Edit",background,mint,() -> { closeDialog(); noteEditor(entry); }),margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,20,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
    }
    /** An entry with an empty id is a new note; anything else replaces the saved record with that id. */
    private void noteEditor(Notes.Entry existing) {
        boolean fresh = existing.id.isEmpty();
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_note,mint,mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label(fresh ? "New note" : "Edit note",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("Kept encrypted in the vault. Nothing is written to your phone's own notes app.",14,soft,false); body.setLineSpacing(dp(2),1);
        card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        EditText title = field("Title (optional)",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText written = area("Write your note",6);
        title.setText(existing.title); written.setText(existing.body);
        card.addView(title,margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
        card.addView(written,margins(new LinearLayout.LayoutParams(-1,-2),0,10,0,0));
        TextView error = label("",13,danger,false); card.addView(error,margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        LinearLayout buttons = new LinearLayout(this);
        LinearLayout save = action(0,"Save",background,mint,null);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(save,margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,16,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
        save.setOnClickListener(v -> {
            Notes.Entry edited = Notes.entry(fresh ? UUID.randomUUID().toString() : existing.id,
                title.getText().toString(),written.getText().toString(),System.currentTimeMillis());
            if (Notes.blank(edited)) { error.setText("Write a title or some text first"); return; }
            closeDialog();
            if (!unlocked || notes == null) return;
            int at = -1; for (int i=0;i<notes.size();i++) if (notes.get(i).id.equals(edited.id)) at = i;
            if (at < 0) notes.add(edited); else notes.set(at,edited);
            tab = NOTES; vault(); commitNotes(fresh ? "Note saved in the vault" : "Note updated");
        });
    }
    private void confirmDeleteNote(Notes.Entry entry) {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_delete,danger,dangerWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label("Delete this note?",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("“" + entry.heading() + "” is permanently removed from the vault. Copy anything you still need first.",14,soft,false);
        body.setLineSpacing(dp(2),1); card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(action(0,"Delete",background,danger,() -> {
            closeDialog();
            if (!unlocked || notes == null) return;
            for (Iterator<Notes.Entry> each = notes.iterator(); each.hasNext(); ) if (each.next().id.equals(entry.id)) each.remove();
            vault(); commitNotes("Note deleted");
        }),margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,24,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
    }
    /** Re-encrypts every note. A failed write leaves the file alone, so the memory copy is dropped and re-read. */
    private void commitNotes(String message) {
        ArrayList<Notes.Entry> snapshot = new ArrayList<>(notes); int token = session;
        worker.execute(() -> {
            boolean ok = true;
            try { store.saveNotes(snapshot); } catch (Exception e) { ok = false; }
            final boolean saved = ok;
            runOnUiThread(() -> {
                if (token != session) return;
                if (saved) { toast(message); return; }
                notes = null; toast("Could not save notes. Nothing was changed.");
                if (tab == NOTES) { tab = PHOTOS; vault(); }
            });
        });
    }
    private void copyNote(Notes.Entry entry) {
        String written = entry.title.isEmpty() ? entry.body : entry.body.isEmpty() ? entry.title : entry.title + "\n" + entry.body;
        copy("Note",written,"Note copied. It stays on the clipboard until something replaces it.");
    }
    private void browserTab(LinearLayout page) {
        WebView view = web(); view.onResume(); view.resumeTimers();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); page.addView(top);
        top.addView(glyph(R.drawable.ic_shield,mint),new LinearLayout.LayoutParams(dp(16),dp(16)));
        top.addView(banner("Private browsing · cleared on lock"),margins(new LinearLayout.LayoutParams(0,-2,1),6,0,8,0));
        top.addView(round(R.drawable.ic_tune,"Browsing options",mint,this::browserOptions),margins(new LinearLayout.LayoutParams(dp(40),dp(40)),0,0,8,0));
        top.addView(action(R.drawable.ic_lock,"Lock",mint,panel,this::lock),new LinearLayout.LayoutParams(-2,dp(40)));
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(bar,margins(new LinearLayout.LayoutParams(-1,-2),0,12,0,0));
        address = field("Search or enter address",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v,code,event) -> {
            if (code != EditorInfo.IME_ACTION_GO) return false;
            open(address.getText().toString()); return true;
        });
        // Focused, the field holds the whole address so it can be edited; at rest it shows only the site.
        address.setOnFocusChangeListener((v,focused) -> {
            if (!focused) { showAddress(location); return; }
            address.setText(location); address.selectAll();
        });
        bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(round(R.drawable.ic_search,"Open",mint,() -> open(address.getText().toString())),margins(new LinearLayout.LayoutParams(dp(44),dp(44)),8,0,0,0));
        LinearLayout nav = new LinearLayout(this); page.addView(nav,margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        nav.addView(round(R.drawable.ic_back,"Back",mint,() -> { if (browser != null && browser.canGoBack()) browser.goBack(); else toast("Nothing to go back to"); }),new LinearLayout.LayoutParams(0,dp(40),1));
        nav.addView(round(R.drawable.ic_forward,"Forward",mint,() -> { if (browser != null && browser.canGoForward()) browser.goForward(); else toast("Nothing to go forward to"); }),margins(new LinearLayout.LayoutParams(0,dp(40),1),8,0,0,0));
        nav.addView(round(R.drawable.ic_refresh,"Reload",mint,() -> { if (browser != null) browser.reload(); }),margins(new LinearLayout.LayoutParams(0,dp(40),1),8,0,0,0));
        nav.addView(round(R.drawable.ic_globe,"Home page",mint,() -> open(Browser.HOME)),margins(new LinearLayout.LayoutParams(0,dp(40),1),8,0,0,0));
        nav.addView(round(R.drawable.ic_delete,"Clear browsing now",danger,this::resetBrowser),margins(new LinearLayout.LayoutParams(0,dp(40),1),8,0,0,0));
        FrameLayout holder = new FrameLayout(this); holder.setBackground(shape(panel,20)); holder.setClipToOutline(true);
        if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);
        holder.addView(view,new FrameLayout.LayoutParams(-1,-1));
        loading = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100); loading.setProgress(view.getProgress()); loading.setProgressTintList(ColorStateList.valueOf(mint)); loading.setBackgroundColor(Color.TRANSPARENT);
        loading.setVisibility(view.getProgress() < 100 ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1,dp(3)); progressParams.gravity = Gravity.TOP; holder.addView(loading,progressParams);
        page.addView(holder,margins(new LinearLayout.LayoutParams(-1,0,1),0,10,0,0));
        showAddress(location);
    }
    /**
     * One browser for the unlocked session. Nothing is cached, no history or form data is kept,
     * third-party cookies are refused, and everything it holds is erased when the vault locks.
     */
    private WebView web() {
        if (browser != null) return browser;
        WebView.setWebContentsDebuggingEnabled(false);
        browser = new WebView(this); browser.setBackgroundColor(background);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSaveFormData(false);
        settings.setGeolocationEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);                      // so a sign-in lasts as long as the unlocked session
        cookies.setAcceptThirdPartyCookies(browser,false);  // but trackers get nothing to write
        browser.setWebViewClient(new WebViewClient() {
            // Only https is followed, so no intent:, market:, tel: or file: link can hand the page to another app.
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://")) return false;
                // A link is retried over https; a redirect is not, so a site bouncing http and https cannot loop.
                if (url.startsWith("http://") && !request.isRedirect()) { view.loadUrl("https://" + url.substring(7)); return true; }
                toast("Only https pages open here. That link was not followed."); return true;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) { showAddress(url); }
            @Override public void onPageFinished(WebView view, String url) {
                showAddress(url); if (loading != null) loading.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) toast("Could not open that page");
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                if (loading == null) return;
                loading.setProgress(progress); loading.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin,false,false); }
        });
        // A download would write a plaintext file outside the vault, so it is refused rather than saved.
        browser.setDownloadListener((url,agent,disposition,mime,size) -> toast("Downloads are off in the private browser"));
        browser.loadUrl(Browser.HOME);
        return browser;
    }
    private void open(String typed) {
        WebView view = web(); String url = Browser.target(typed);
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null && address != null) keyboard.hideSoftInputFromWindow(address.getWindowToken(),0);
        if (address != null) address.clearFocus();
        view.loadUrl(url); showAddress(url);
    }
    /** At rest the field shows the site only, so a long address cannot push the real host out of view. */
    private void showAddress(String url) {
        location = Browser.blank(url) ? "" : url;
        if (address == null || address.hasFocus()) return;
        address.setText(location.isEmpty() ? "" : Browser.host(location));
    }
    private void browserOptions() {
        LinearLayout content = sheet(R.drawable.ic_globe,mint,mintWash,"Private browsing",location.isEmpty() ? "No page open" : Browser.host(location));
        TextView note = label("Pages open over https only. Cookies last as long as this unlocked session and third-party cookies are blocked. Nothing is cached, no history is kept, and downloads are off. It is all erased when the vault locks, including when you leave the app.",13,soft,false);
        note.setLineSpacing(dp(2),1); content.addView(note,margins(new LinearLayout.LayoutParams(-1,-2),12,0,12,10));
        content.addView(option(R.drawable.ic_globe,"Home page","Opens " + Browser.host(Browser.HOME),false,() -> open(Browser.HOME)));
        content.addView(option(R.drawable.ic_delete,"Clear browsing now","Erases cookies and the open page without locking",true,this::resetBrowser));
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    private void resetBrowser() { clearBrowser(); vault(); toast("Browsing data cleared"); }
    /** Leaves nothing behind. Runs on every lock, so it also runs whenever the app goes to the background. */
    private void clearBrowser() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null); cookies.removeSessionCookies(null); cookies.flush();
        WebStorage.getInstance().deleteAllData();
        location = ""; address = null; loading = null;
        if (browser == null) return;
        WebView view = browser; browser = null;
        view.stopLoading(); view.clearHistory(); view.clearCache(true); view.clearFormData(); view.clearSslPreferences();
        if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);
        view.destroy();
    }
    private void settings() {
        int sort = preferences.getInt("sort",0); boolean grid = preferences.getBoolean("grid",false);
        LinearLayout content = sheet(R.drawable.ic_tune,mint,mintWash,"Vault settings","Encrypted on this device · not backed up");
        content.addView(option(R.drawable.ic_key,"Change PIN","Asks for your current PIN first",false,this::changePin));
        content.addView(option(R.drawable.ic_globe,"Private browsing",browser == null ? "Opens in its own tab and is cleared on lock" : "A page is open. It is cleared on lock.",false,() -> openTab(BROWSER)));
        content.addView(callingOption());
        content.addView(originalsOption());
        content.addView(option(R.drawable.ic_sort,"Sort items",new String[]{"Newest first","Oldest first","Largest first","Smallest first"}[sort],false,this::sortOptions));
        content.addView(option(grid ? R.drawable.ic_list : R.drawable.ic_grid,grid ? "Switch to list view" : "Switch to grid view",grid ? "One item per row with details" : "Three thumbnails per row",false,
            () -> { preferences.edit().putBoolean("grid",!grid).apply(); vault(); }));
        content.addView(option(R.drawable.ic_history,"Clear calculator history",count(historyEntries().size(),"calculation") + " saved",true,this::clearHistory));
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    private void sortOptions() {
        int current = preferences.getInt("sort",0);
        String[] names = {"Newest first","Oldest first","Largest first","Smallest first"};
        LinearLayout content = sheet(R.drawable.ic_sort,mint,mintWash,"Sort items","Names stay with their item");
        for (int i=0;i<names.length;i++) {
            int choice = i;
            content.addView(option(choice == current ? R.drawable.ic_check : R.drawable.ic_sort,names[i],choice == current ? "Using this order" : "Tap to use",false,
                () -> { preferences.edit().putInt("sort",choice).apply(); vault(); }));
        }
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    private ArrayList<Item> selected() {
        ArrayList<Item> chosen = new ArrayList<>();
        for (Item item : items()) if (selection.contains(item.file.getName())) chosen.add(item);
        return chosen;
    }
    private void exportSelected() {
        ArrayList<Item> chosen = selected();
        if (chosen.isEmpty()) { toast("Pick at least one item first"); return; }
        pendingExports.clear(); pendingExports.addAll(chosen);
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),12);
    }
    private void deleteSelected() {
        ArrayList<Item> chosen = selected();
        if (chosen.isEmpty()) { toast("Pick at least one item first"); return; }
        ArrayList<File> files = new ArrayList<>(); for (Item item : chosen) files.add(item.file);
        confirmDelete("Delete " + count(files.size(),"item") + "?",files);
    }
    private void pick() {
        importTab = tab == VIDEOS ? VIDEOS : PHOTOS;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(importTab == VIDEOS ? "video/*" : "image/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(intent,10);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        // The gallery's delete prompt answers with no data, and a refusal is an answer rather than a failure,
        // so it is settled before the check that treats an empty result as a cancelled picker.
        if (request == 14) {
            int asked = removalAsked, done = removalDone, stuck = removalStuck;
            removalAsked = 0; removalDone = 0; removalStuck = 0; dropGrants();
            if (asked > 0) report(result == RESULT_OK ? done + asked : done,done + asked + stuck);
            return;
        }
        if (result != RESULT_OK || data == null) { exportFile=null; pendingExports.clear(); return; }
        if (request == 10) {
            if (data.getClipData()!=null) for (int i=0;i<data.getClipData().getItemCount();i++) pendingImports.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData()!=null) pendingImports.add(data.getData());
            toast("Enter your PIN to finish importing");
        } else if (request == 11 && data.getData()!=null) {
            pendingExportUri=data.getData(); toast("Enter your PIN to finish exporting");
        } else if (request == 12 && data.getData()!=null) {
            pendingExportTree=data.getData(); toast("Enter your PIN to finish exporting");
        } else if (request == 13 && data.getData()!=null) {
            pendingContactPick=data.getData(); toast("Enter your PIN to finish importing");
        }
    }
    private Uri pendingExportUri;
    private void importPending() {
        removePending();   // an import that finished after the vault locked leaves its originals for the next unlock
        if (pendingContactPick != null) { Uri picked = pendingContactPick; pendingContactPick = null; importContact(picked); }
        if (pendingExportUri!=null && exportFile!=null) {
            Uri destination=pendingExportUri; File file=exportFile; pendingExportUri=null; exportFile=null;
            worker.execute(() -> { try (OutputStream out=getContentResolver().openOutputStream(destination,"w")) {
                if (out==null) throw new IOException(); store.decrypt(file,out); runOnUiThread(() -> toast("Exported to your selected location"));
            } catch (Exception e) { runOnUiThread(() -> toast("Export failed. Remove any incomplete exported file.")); } });
        }
        if (pendingExportTree != null && !pendingExports.isEmpty()) {
            Uri tree = pendingExportTree; ArrayList<Item> batch = new ArrayList<>(pendingExports); pendingExportTree = null; pendingExports.clear();
            selecting = false; selection.clear(); vault(); toast("Exporting " + count(batch.size(),"item") + "…");
            worker.execute(() -> {
                int done = 0; Uri folder = DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                for (Item item : batch) try {
                    String mime = store.mime(item.file); String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                    Uri target = DocumentsContract.createDocument(getContentResolver(),folder,mime,item.name.toLowerCase(Locale.US).replace(' ','-') + "." + (ext == null ? "bin" : ext));
                    if (target == null) continue;
                    try (OutputStream out = getContentResolver().openOutputStream(target,"w")) { if (out == null) throw new IOException(); store.decrypt(item.file,out); }
                    done++;
                } catch (Exception ignored) { }
                int exported = done;
                runOnUiThread(() -> toast(exported + " of " + batch.size() + " items exported as unencrypted copies"));
            });
        }
        if (pendingImports.isEmpty()) return;
        ArrayList<Uri> uris=new ArrayList<>(pendingImports); pendingImports.clear(); toast("Importing media…");
        worker.execute(() -> {
            ArrayList<Uri> copied = new ArrayList<>();
            for (Uri uri:uris) try { String type=getContentResolver().getType(uri); if(type==null || !(type.startsWith("image/") || type.startsWith("video/"))) continue; store.importMedia(uri,type); copied.add(uri); } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (unlocked) { tab=importTab; vault(); }
                if (!clearsOriginals()) { toast(copied.size() + " of " + uris.size() + " items imported. Originals remain in your gallery."); return; }
                toast(copied.size() + " of " + uris.size() + " items imported");
                pendingRemovals.addAll(copied); removePending();
            });
        });
    }
    private boolean clearsOriginals() { return preferences.getBoolean("clearOriginals",true); }
    /**
     * Hands the originals of already-imported items to the gallery so they stop showing up there.
     * Android 11 and newer answer with one system prompt for the whole batch, which is the only way an app
     * without the storage permission is allowed to delete media it did not create.
     */
    private void removePending() {
        if (pendingRemovals.isEmpty() || removalAsked > 0 || !foreground) return;
        ArrayList<Uri> rows = new ArrayList<>(); int removed = 0, stuck = 0;
        for (Uri picked : pendingRemovals) {
            Uri row = galleryRow(picked);
            if (row != null) { rows.add(row); continue; }
            if (deleteDocument(picked)) removed++; else stuck++;
        }
        pendingRemovals.clear();
        if (rows.isEmpty()) { dropGrants(); report(removed,removed + stuck); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) try {
            removalAsked = rows.size(); removalDone = removed; removalStuck = stuck;
            startIntentSenderForResult(MediaStore.createDeleteRequest(getContentResolver(),rows).getIntentSender(),14,null,0,0,0);
            return;
        } catch (Exception e) { removalAsked = 0; removalDone = 0; removalStuck = 0; }
        // Android 10 and older have no such prompt, so the row only goes if the picker handed write access over with it.
        for (Uri row : rows) try { if (getContentResolver().delete(row,null,null) > 0) removed++; else stuck++; } catch (Exception e) { stuck++; }
        dropGrants(); report(removed,removed + stuck);
    }
    /**
     * Best-effort translation of a picked document to the gallery row behind it.
     * Returns null for a file the gallery does not list, which is left alone.
     */
    private Uri galleryRow(Uri picked) {
        if (MediaStore.AUTHORITY.equals(picked.getAuthority())) return picked;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getMediaUri carries the caller's access over to the media row, but it looks for that access in the
            // persisted list only, and a picker grant is not persisted. Without this the row comes back unusable
            // and the delete prompt has nothing to act on. dropGrants() hands it straight back once this settles.
            try { getContentResolver().takePersistableUriPermission(picked,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            try { Uri row = MediaStore.getMediaUri(this,picked); if (row != null) return row; } catch (Exception ignored) { }
        }
        // The media documents provider spells the gallery row id into the document id, so no lookup is needed.
        if ("com.android.providers.media.documents".equals(picked.getAuthority())) try {
            String[] parts = DocumentsContract.getDocumentId(picked).split(":");
            if (parts.length == 2) return ContentUris.withAppendedId("image".equals(parts[0]) ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                : "video".equals(parts[0]) ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Files.getContentUri("external"),Long.parseLong(parts[1]));
        } catch (Exception ignored) { }
        return null;
    }
    /** Last resort for a file the gallery does not list: ask whichever provider handed it over to delete it. */
    private boolean deleteDocument(Uri picked) {
        try { return DocumentsContract.isDocumentUri(this,picked) && DocumentsContract.deleteDocument(getContentResolver(),picked); }
        catch (Exception e) { return false; }
    }
    /**
     * Gives back every persisted grant the app holds. Removal is the only thing that takes one, it is wanted for
     * the length of one prompt, and a vault has no business keeping standing access to the gallery afterwards.
     * Called on launch too, so a grant outlives the app at most until it is next opened.
     */
    private void dropGrants() {
        for (UriPermission held : getContentResolver().getPersistedUriPermissions())
            try { getContentResolver().releasePersistableUriPermission(held.getUri(),Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
    }
    /** Says plainly what is still in the gallery, because the vault copy is safe either way. */
    private void report(int removed, int asked) {
        if (asked <= 0) return;
        if (removed >= asked) { toast(count(removed,"original") + " removed from your gallery"); return; }
        // A file the gallery never listed reaches this too, so the wording does not claim it is sitting there.
        if (removed == 0) { toast("Imported. The originals were left in place — remove them yourself if you want them gone."); return; }
        toast(removed + " of " + asked + " originals removed. Remove the rest yourself.");
    }
    private void actions(Item item) {
        LinearLayout content = sheet(item.video ? R.drawable.ic_video : R.drawable.ic_photo,item.video ? lilac : mint,item.video ? lilacWash : mintWash,item.name,item.meta);
        content.addView(option(R.drawable.ic_view,item.video ? "Play" : "View","Opens only inside the vault",false,() -> preview(item.file,item.name)));
        content.addView(option(R.drawable.ic_export,"Export a copy","Saves an unencrypted copy where you choose",false,() -> export(item.file)));
        content.addView(option(R.drawable.ic_check,"Select","Pick several items to export or delete",false,() -> { selecting = true; choose(item); }));
        content.addView(option(R.drawable.ic_delete,"Delete from vault","Permanently removes the vault copy",true,() -> confirmDelete("Delete " + item.name + "?",Collections.singletonList(item.file))));
        vaultDialog = popup(content,Gravity.BOTTOM);
    }
    private LinearLayout option(int drawable, String title, String detail, boolean destructive, Runnable run) {
        LinearLayout option = new LinearLayout(this); option.setGravity(Gravity.CENTER_VERTICAL); option.setPadding(dp(12),dp(12),dp(12),dp(12)); option.setBackground(surface(panel,18));
        boolean insideVault = unlocked;   // vault actions must not fire if the sheet outlives an unlock
        option.setOnClickListener(v -> { closeDialog(); if (unlocked || !insideVault) run.run(); });
        option.addView(glyph(drawable,destructive ? danger : mint),new LinearLayout.LayoutParams(dp(24),dp(24)));
        option.addView(stack(title,detail,destructive ? danger : Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),16,0,0,0)); return option;
    }
    private void export(File file) {
        try {
            exportFile=file; String mime=store.mime(file); String ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,"vault-export."+(ext==null?"bin":ext)); startActivityForResult(intent,11);
        } catch (Exception e) { toast("Cannot export this file"); }
    }
    private void confirmDelete(String title, List<File> files) {
        LinearLayout card = card();
        card.addView(tile(R.drawable.ic_delete,danger,dangerWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label(title,20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label(files.size() == 1 ? "This permanently deletes the vault copy. Export it first if you might need it."
            : "This permanently deletes " + files.size() + " vault copies. Export them first if you might need them.",14,soft,false);
        body.setLineSpacing(dp(2),1); card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(action(0,"Delete",background,danger,() -> {
            closeDialog();
            if (!unlocked) return;
            int failed = 0; for (File file : files) if (!file.delete()) failed++;
            if (failed > 0) toast("Could not delete " + count(failed,"item"));
            selecting = false; selection.clear(); vault();
        }),margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,24,0,0));
        vaultDialog = popup(scrollable(card),Gravity.CENTER);
    }
    private void preview(File file, String name) {
        int token=session; toast("Opening…");
        worker.execute(() -> {
            File temporary=null;
            try {
                String mime=store.mime(file); temporary=File.createTempFile("preview-",".media",getCacheDir());
                try(OutputStream out=new FileOutputStream(temporary)) { store.decrypt(file,out); }
                File ready=temporary;
                runOnUiThread(() -> { if (!unlocked || token!=session) { ready.delete(); return; } showMedia(ready,mime,name); });
            } catch(Exception e) { if(temporary!=null) temporary.delete(); runOnUiThread(() -> toast("Could not open this media file")); }
        });
    }
    private void showMedia(File file,String mime,String name) {
        base(); LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = tile(R.drawable.ic_back,Color.WHITE,panel,44); back.setBackground(surface(panel,22)); back.setContentDescription("Back to vault"); back.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        back.setOnClickListener(v -> { stopVideo(); clearPreviews(); vault(); });
        bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44))); bar.addView(label(name,18,Color.WHITE,true),margins(new LinearLayout.LayoutParams(0,-2,1),14,0,0,0)); root.addView(bar);
        LinearLayout.LayoutParams area = margins(new LinearLayout.LayoutParams(-1,0,1),0,16,0,0);
        if(mime.startsWith("video/")) {
            video=new VideoView(this); root.addView(video,area); MediaController controls=new MediaController(this); controls.setAnchorView(video); video.setMediaController(controls);
            video.setVideoPath(file.getAbsolutePath()); video.setOnPreparedListener(mp -> video.start()); video.setOnErrorListener((mp,what,extra) -> { toast("This video format cannot be played on this device. You can export it."); return true; });
        } else {
            BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true; BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            options.inSampleSize=1; while(options.outWidth/options.inSampleSize>2048 || options.outHeight/options.inSampleSize>2048) options.inSampleSize*=2;
            options.inJustDecodeBounds=false; Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            ImageView photo=new ImageView(this); photo.setScaleType(ImageView.ScaleType.FIT_CENTER); photo.setImageBitmap(bitmap); root.addView(photo,area);
            if(bitmap==null) toast("This image format cannot be displayed. You can export it.");
        }
    }
    private void stopVideo() { if(video!=null) { video.stopPlayback(); video=null; } }
    private void clearPreviews() { File[] files=getCacheDir().listFiles((d,n) -> n.startsWith("preview-")); if(files!=null) for(File file:files) file.delete(); }
    private void lock() {
        unlocked=false; session++; expression=""; selecting=false; selection.clear(); thumbnails.evictAll();
        contacts=null; notes=null; tab=PHOTOS; clearBrowser();
        closeDialog(); stopVideo(); clearPreviews(); calculator();
    }
    @Override protected void onResume() { super.onResume(); foreground=true; }
    @Override protected void onPause() { foreground=false; lock(); super.onPause(); }
    @Override public void onBackPressed() {
        if (unlocked && tab == BROWSER && browser != null && browser.canGoBack()) { browser.goBack(); return; }
        if (unlocked) lock(); else super.onBackPressed();
    }
    @Override protected void onDestroy() { stopVideo(); clearBrowser(); worker.shutdown(); thumbnailPool.shutdownNow(); super.onDestroy(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
