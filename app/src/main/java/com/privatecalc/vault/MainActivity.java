package com.privatecalc.vault;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private final int background = Color.rgb(14, 20, 23), panel = Color.rgb(28, 37, 42), raised = Color.rgb(38, 50, 56), mint = Color.rgb(171, 241, 210), mintWash = Color.rgb(30, 54, 48),
        lilac = Color.rgb(190, 198, 255), lilacWash = Color.rgb(40, 44, 70), danger = Color.rgb(255, 138, 128), dangerWash = Color.rgb(64, 32, 32), soft = 0xffa5b3b8, muted = 0xff8b9b9f;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private TextView display;
    private String expression = "";
    private VaultStore store;
    private android.content.SharedPreferences preferences;
    private boolean unlocked, foreground, busy;
    private int session;
    private VideoView video;
    private File exportFile;
    private Dialog vaultDialog;
    private final ArrayList<Uri> pendingImports = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
        preferences = getSharedPreferences("access", MODE_PRIVATE);
        store = new VaultStore(this); clearPreviews(); calculator();
        if (!preferences.contains("hash")) new AlertDialog.Builder(this).setTitle("Your private calculator")
            .setMessage("Set a 6–12 digit PIN. Enter it in the calculator and tap = to open your photo and video vault.\n\nThere is no PIN recovery. Uninstalling the app or clearing its data removes the vault. Export important files first.")
            .setPositiveButton("Set PIN", (d,w) -> setupPin()).setCancelable(false).show();
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
        LinearLayout a = new LinearLayout(this); a.setGravity(Gravity.CENTER); a.setPadding(dp(18),0,dp(18),0); a.setBackground(surface(fill,18)); a.setOnClickListener(v -> run.run());
        if (drawable != 0) a.addView(glyph(drawable,color),new LinearLayout.LayoutParams(dp(20),dp(20)));
        a.addView(label(value,16,color,true),margins(new LinearLayout.LayoutParams(-2,-2),drawable != 0 ? 8 : 0,0,0,0)); return a;
    }
    private LinearLayout stack(String title, String detail, int color) {
        LinearLayout s = new LinearLayout(this); s.setOrientation(LinearLayout.VERTICAL);
        s.addView(label(title,16,color,true)); s.addView(label(detail,13,soft,false),margins(new LinearLayout.LayoutParams(-2,-2),0,2,0,0)); return s;
    }
    private Dialog popup(View content, int gravity) {
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout frame = new FrameLayout(this); content.setClickable(true); frame.addView(content,new FrameLayout.LayoutParams(-1,-2)); frame.setOnClickListener(v -> dialog.dismiss());
        frame.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(dp(12),dp(12),dp(12),dp(12)+insets.getSystemWindowInsetBottom()); return insets; });
        frame.setPadding(dp(12),dp(12),dp(12),dp(12)); dialog.setContentView(frame);
        Window window = dialog.getWindow(); window.addFlags(WindowManager.LayoutParams.FLAG_SECURE); window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setDimAmount(0.6f);
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
        base(); root.addView(text("Calculator",24,Color.WHITE));
        TextView caption = text("EVERYDAY, SIMPLIFIED",11,muted); root.addView(caption);
        caption.setOnLongClickListener(v -> { if (!preferences.contains("hash")) setupPin(); else toast("Enter your PIN and tap ="); return true; });
        display = text(expression.isEmpty() ? "0" : expression,48,Color.WHITE); display.setGravity(Gravity.BOTTOM | Gravity.END); display.setMaxLines(2);
        root.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(text(" ",14,mint));
        String[][] keys = {{"AC","⌫","%","÷"},{"7","8","9","×"},{"4","5","6","−"},{"1","2","3","+"},{"±","0",".","="}};
        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this); root.addView(row,new LinearLayout.LayoutParams(-1,dp(70)));
            for (String key : rowKeys) {
                Button b = button(key, () -> press(key)); b.setTextSize(24);
                if (key.equals("=")) { b.setBackground(surface(mint,22)); b.setTextColor(background); }
                else if (key.matches("[0-9.]")) b.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,-1,1); params.setMargins(dp(4),dp(4),dp(4),dp(4)); row.addView(b,params);
            }
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
                calculate(); break;
            default: if (expression.length() < 80) expression += key;
        }
        display.setText(expression.isEmpty() ? "0" : expression);
    }
    private void calculate() { try { expression = Calculator.evaluate(expression); } catch (Exception e) { expression = ""; toast("Check your calculation"); } }
    private byte[] hash(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(),salt,120000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); } finally { spec.clearPassword(); }
    }
    private void setupPin() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(24),dp(8),dp(24),dp(8));
        EditText first = new EditText(this), second = new EditText(this);
        for (EditText entry : new EditText[]{first,second}) { entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD); form.addView(entry); }
        first.setHint("PIN (6–12 digits)"); second.setHint("Confirm PIN");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Create your PIN").setView(form).setPositiveButton("Save PIN",null).setCancelable(false).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String pin = first.getText().toString();
            if (!pin.matches("[0-9]{6,12}") || !pin.equals(second.getText().toString())) { second.setError("Use matching 6–12 digit PINs"); return; }
            dialog.getButton(-1).setEnabled(false);
            worker.execute(() -> { try {
                byte[] salt = new byte[32]; new SecureRandom().nextBytes(salt);
                String encoded = Base64.getEncoder().encodeToString(hash(pin,salt));
                boolean saved = preferences.edit().putString("salt",Base64.getEncoder().encodeToString(salt)).putString("hash",encoded).commit();
                if (!saved) throw new IOException();
                runOnUiThread(() -> { dialog.dismiss(); toast("PIN saved. Enter it and tap = to unlock."); });
            } catch (Exception e) { runOnUiThread(() -> { dialog.getButton(-1).setEnabled(true); toast("Could not save PIN. Try again."); }); } });
        })); dialog.show();
    }
    private void verifyPin(String pin) {
        if (System.currentTimeMillis() < preferences.getLong("retry",0)) { expression=""; display.setText("0"); toast("Please wait before trying again"); return; }
        busy = true; int token = session;
        worker.execute(() -> {
            boolean matches = false;
            try { matches = MessageDigest.isEqual(hash(pin,Base64.getDecoder().decode(preferences.getString("salt",""))),Base64.getDecoder().decode(preferences.getString("hash",""))); } catch (Exception ignored) { }
            final boolean valid = matches;
            runOnUiThread(() -> {
                busy = false; expression="";
                if (token != session || !foreground) return;
                if (valid) { preferences.edit().putInt("attempts",0).putLong("retry",0).apply(); unlocked=true; interceptBack(true); vault(); importPending(); }
                else {
                    int attempts = preferences.getInt("attempts",0)+1;
                    preferences.edit().putInt("attempts",attempts).putLong("retry",attempts >= 5 ? System.currentTimeMillis()+30000 : 0).apply();
                    expression=pin; calculate(); display.setText(expression);
                }
            });
        });
    }
    private void vault() {
        if (!unlocked) return;
        base(); File[] files = store.list(); String[] types = new String[files.length]; int photos = 0, videos = 0;
        for (int i=0;i<files.length;i++) { try { types[i]=store.mime(files[i]); } catch (IOException e) { types[i]="unknown"; } if (types[i].startsWith("video/")) videos++; else photos++; }
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(glyph(R.drawable.ic_shield,mint),new LinearLayout.LayoutParams(dp(16),dp(16)));
        top.addView(label("Encrypted on this device",13,mint,true),margins(new LinearLayout.LayoutParams(0,-2,1),6,0,8,0));
        top.addView(action(R.drawable.ic_lock,"Lock",mint,panel,this::lock),new LinearLayout.LayoutParams(-2,dp(40))); root.addView(top);
        root.addView(label("Your private space",28,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,14,0,0));
        String summary = files.length == 0 ? "No items yet" : videos == 0 ? count(photos,"photo") : photos == 0 ? count(videos,"video") : count(photos,"photo") + "  ·  " + count(videos,"video");
        root.addView(label(summary,14,soft,false),margins(new LinearLayout.LayoutParams(-2,-2),0,4,0,0));
        root.addView(action(R.drawable.ic_add,"Add photos or videos",background,mint,this::pick),margins(new LinearLayout.LayoutParams(-1,dp(56)),0,20,0,0));
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); LinearLayout items = new LinearLayout(this); items.setOrientation(LinearLayout.VERTICAL); scroll.addView(items);
        root.addView(scroll,margins(new LinearLayout.LayoutParams(-1,0,1),0,12,0,0));
        LinearLayout note = new LinearLayout(this); note.setPadding(dp(14),dp(12),dp(14),dp(12)); note.setBackground(shape(panel,16));
        TextView tip = label("Imports are copies. After checking them here, remove originals from your gallery and its trash if you want them hidden there.",13,soft,false); tip.setLineSpacing(dp(2),1);
        note.addView(glyph(R.drawable.ic_info,soft),margins(new LinearLayout.LayoutParams(dp(18),dp(18)),0,1,0,0)); note.addView(tip,margins(new LinearLayout.LayoutParams(0,-2,1),12,0,0,0)); items.addView(note);
        if (files.length == 0) {
            LinearLayout empty = new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER_HORIZONTAL); empty.setPadding(dp(24),dp(48),dp(24),dp(24));
            empty.addView(tile(R.drawable.ic_photo,mint,mintWash,64),new LinearLayout.LayoutParams(dp(64),dp(64)));
            TextView heading = label("A little space, just for you",20,Color.WHITE,true); heading.setGravity(Gravity.CENTER); empty.addView(heading,margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
            TextView hint = label("Add a photo or video to get started.\nYour PIN opens this space from the calculator.",14,soft,false); hint.setGravity(Gravity.CENTER); hint.setLineSpacing(dp(2),1);
            empty.addView(hint,margins(new LinearLayout.LayoutParams(-2,-2),0,6,0,0)); items.addView(empty);
        } else {
            TextView section = label("RECENTLY ADDED",12,muted,true); section.setLetterSpacing(0.1f); items.addView(section,margins(new LinearLayout.LayoutParams(-2,-2),4,24,0,4));
        }
        // Numbered per type, newest highest, so the list reads Photo 3, Photo 2, Video 1, Photo 1.
        for (int i=0,photo=photos,clip=videos;i<files.length;i++) {
            File file = files[i]; boolean isVideo = types[i].startsWith("video/"); String name = isVideo ? "Video " + clip-- : "Photo " + photo--;
            String meta = android.text.format.Formatter.formatShortFileSize(this,file.length()) + "  ·  " + DateUtils.formatDateTime(this,file.lastModified(),DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
            items.addView(mediaRow(file,isVideo,name,meta),margins(new LinearLayout.LayoutParams(-1,-2),0,8,0,0));
        }
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER);
        footer.addView(glyph(R.drawable.ic_lock,muted),new LinearLayout.LayoutParams(dp(12),dp(12)));
        footer.addView(label("Keep your PIN safe. Export files before uninstalling.",12,muted,false),margins(new LinearLayout.LayoutParams(-2,-2),6,0,0,0));
        root.addView(footer,margins(new LinearLayout.LayoutParams(-1,-2),0,12,0,0));
    }
    private LinearLayout mediaRow(File file, boolean isVideo, String name, String meta) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(surface(panel,20));
        row.setOnClickListener(v -> actions(file,isVideo,name,meta));
        row.addView(tile(isVideo ? R.drawable.ic_video : R.drawable.ic_photo,isVideo ? lilac : mint,isVideo ? lilacWash : mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        row.addView(stack(name,meta,Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),14,0,8,0));
        row.addView(glyph(R.drawable.ic_chevron,muted),new LinearLayout.LayoutParams(dp(20),dp(20))); return row;
    }
    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}); intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(intent,10);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null) { exportFile=null; return; }
        if (request == 10) {
            if (data.getClipData()!=null) for (int i=0;i<data.getClipData().getItemCount();i++) pendingImports.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData()!=null) pendingImports.add(data.getData());
            toast("Enter your PIN to finish importing");
        } else if (request == 11 && data.getData()!=null) {
            pendingExportUri=data.getData(); toast("Enter your PIN to finish exporting");
        }
    }
    private Uri pendingExportUri;
    private void importPending() {
        if (pendingExportUri!=null && exportFile!=null) {
            Uri destination=pendingExportUri; File file=exportFile; pendingExportUri=null; exportFile=null;
            worker.execute(() -> { try (OutputStream out=getContentResolver().openOutputStream(destination,"w")) {
                if (out==null) throw new IOException(); store.decrypt(file,out); runOnUiThread(() -> toast("Exported to your selected location"));
            } catch (Exception e) { runOnUiThread(() -> toast("Export failed. Remove any incomplete exported file.")); } });
        }
        if (pendingImports.isEmpty()) return;
        ArrayList<Uri> uris=new ArrayList<>(pendingImports); pendingImports.clear(); toast("Importing media…");
        worker.execute(() -> {
            int success=0;
            for (Uri uri:uris) try { String type=getContentResolver().getType(uri); if(type==null || !(type.startsWith("image/") || type.startsWith("video/"))) continue; store.importMedia(uri,type); success++; } catch (Exception ignored) { }
            int count=success; runOnUiThread(() -> { if (unlocked) vault(); toast(count + " of " + uris.size() + " items imported. Originals remain in your gallery."); });
        });
    }
    private void actions(File file, boolean isVideo, String name, String meta) {
        LinearLayout sheet = new LinearLayout(this); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setPadding(dp(8),dp(10),dp(8),dp(8)); sheet.setBackground(shape(panel,28));
        View grip = new View(this); grip.setBackground(shape(raised,2)); LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(36),dp(4)); gripParams.gravity = Gravity.CENTER_HORIZONTAL; sheet.addView(grip,gripParams);
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(12),dp(14),dp(12),dp(16));
        head.addView(tile(isVideo ? R.drawable.ic_video : R.drawable.ic_photo,isVideo ? lilac : mint,isVideo ? lilacWash : mintWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        head.addView(stack(name,meta,Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),14,0,0,0)); sheet.addView(head);
        View line = new View(this); line.setBackgroundColor(raised); sheet.addView(line,margins(new LinearLayout.LayoutParams(-1,dp(1)),12,0,12,6));
        sheet.addView(option(R.drawable.ic_view,isVideo ? "Play" : "View","Opens only inside the vault",false,() -> preview(file,name)));
        sheet.addView(option(R.drawable.ic_export,"Export a copy","Saves an unencrypted copy where you choose",false,() -> export(file)));
        sheet.addView(option(R.drawable.ic_delete,"Delete from vault","Permanently removes the vault copy",true,() -> confirmDelete(file,name)));
        vaultDialog = popup(sheet,Gravity.BOTTOM);
    }
    private LinearLayout option(int drawable, String title, String detail, boolean destructive, Runnable run) {
        LinearLayout option = new LinearLayout(this); option.setGravity(Gravity.CENTER_VERTICAL); option.setPadding(dp(12),dp(12),dp(12),dp(12)); option.setBackground(surface(panel,18));
        option.setOnClickListener(v -> { closeDialog(); if (unlocked) run.run(); });
        option.addView(glyph(drawable,destructive ? danger : mint),new LinearLayout.LayoutParams(dp(24),dp(24)));
        option.addView(stack(title,detail,destructive ? danger : Color.WHITE),margins(new LinearLayout.LayoutParams(0,-2,1),16,0,0,0)); return option;
    }
    private void export(File file) {
        try {
            exportFile=file; String mime=store.mime(file); String ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,"vault-export."+(ext==null?"bin":ext)); startActivityForResult(intent,11);
        } catch (Exception e) { toast("Cannot export this file"); }
    }
    private void confirmDelete(File file, String name) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24),dp(24),dp(24),dp(20)); card.setBackground(shape(panel,28));
        card.addView(tile(R.drawable.ic_delete,danger,dangerWash,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(label("Delete " + name + "?",20,Color.WHITE,true),margins(new LinearLayout.LayoutParams(-2,-2),0,16,0,0));
        TextView body = label("This permanently deletes the vault copy. Export it first if you might need it.",14,soft,false); body.setLineSpacing(dp(2),1);
        card.addView(body,margins(new LinearLayout.LayoutParams(-1,-2),0,6,0,0));
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(action(0,"Cancel",Color.WHITE,raised,this::closeDialog),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(action(0,"Delete",background,danger,() -> { closeDialog(); if (unlocked) { if (!file.delete()) toast("Could not delete item"); vault(); } }),margins(new LinearLayout.LayoutParams(0,dp(48),1),12,0,0,0));
        card.addView(buttons,margins(new LinearLayout.LayoutParams(-1,-2),0,24,0,0));
        vaultDialog = popup(card,Gravity.CENTER);
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
    private void lock() { unlocked=false; session++; expression=""; interceptBack(false); closeDialog(); stopVideo(); clearPreviews(); calculator(); }
    @Override protected void onResume() { super.onResume(); foreground=true; }
    @Override protected void onPause() { foreground=false; lock(); super.onPause(); }
    // Android 13+ delivers back through OnBackInvokedDispatcher; onBackPressed covers older versions. Held as Object so older devices never load the class.
    private Object backCallback;
    private void interceptBack(boolean enable) {
        if (Build.VERSION.SDK_INT < 33 || enable == (backCallback != null)) return;
        if (enable) { backCallback = (android.window.OnBackInvokedCallback) this::lock; getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,(android.window.OnBackInvokedCallback) backCallback); }
        else { getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback); backCallback = null; }
    }
    @Override public void onBackPressed() { if(unlocked) lock(); else super.onBackPressed(); }
    @Override protected void onDestroy() { stopVideo(); worker.shutdown(); super.onDestroy(); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
