package com.privatecalc.vault;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import java.util.concurrent.Executor;

/**
 * One AdMob native ad, drawn in the app's own style and shared by every screen that asks for a slot.
 *
 * The screens here are rebuilt from scratch on each redraw, so a slot cannot own its ad: one request per
 * redraw would mean a new ad on every keypress. Instead a single loaded ad is kept here and re-bound to a
 * fresh view each time a slot is asked for, and only ever replaced once it is older than {@link #STALE}.
 */
final class NativeAds {
    /** How long a loaded ad is reused before the next slot asks for a new one. AdMob's floor for a refresh is 60s. */
    private static final long STALE = 60_000L;
    /** How long to wait after a failed request before trying again, so a no-fill does not turn into a request loop. */
    private static final long BACKOFF = 30_000L;
    /** The two shapes a slot comes in: the tall card with a media panel, and the short row for the calculator. */
    static final boolean MEDIA = true, COMPACT = false;

    private final Activity activity;
    private final int surface, accent, onAccent, title, detail;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private NativeAd ad;
    private long loadedAt, refusedAt;
    // `starting` guards against initialising twice; `ready` means the SDK has finished and will accept a request.
    private boolean loading, starting, ready;
    /** The slot on screen right now. Only one screen is ever visible, so an arriving ad belongs to this one. */
    private FrameLayout slot;
    private boolean slotMedia;

    NativeAds(Activity activity, int surface, int accent, int onAccent, int title, int detail) {
        this.activity = activity; this.surface = surface; this.accent = accent; this.onAccent = onAccent; this.title = title; this.detail = detail;
    }

    /** Initialising touches disk and the network, so it is kept off the thread drawing the calculator. */
    void start(Executor worker) {
        if (starting) return;
        starting = true;
        // The first screen is drawn before this finishes, so its slot finds nothing to show and the
        // request below is what fills it. Asking the SDK for an ad before it is initialised is not safe.
        worker.execute(() -> MobileAds.initialize(activity, status -> { ready = true; request(); }));
    }

    /**
     * An empty container to drop into a layout. It stays empty and takes no height until an ad is loaded,
     * so a screen with no fill looks exactly as it did before ads existed.
     */
    View slot(boolean media) {
        FrameLayout holder = new FrameLayout(activity);
        slot = holder; slotMedia = media;
        if (fresh()) holder.addView(card(ad, media));
        request();
        return holder;
    }

    private boolean fresh() { return ad != null && System.currentTimeMillis() - loadedAt < STALE; }

    /** Asks for an ad only when there is nothing usable to show and nothing already in flight. */
    private void request() {
        if (loading || fresh() || !ready) return;
        if (System.currentTimeMillis() - refusedAt < BACKOFF) return;
        loading = true;
        new AdLoader.Builder(activity, BuildConfig.NATIVE_AD_UNIT)
            .forNativeAd(loaded -> {
                loading = false;
                // A redraw may have happened while the request was out; the newest view is the one to fill.
                if (activity.isDestroyed() || activity.isFinishing()) { loaded.destroy(); return; }
                // The view goes before the ad it is bound to, so nothing on screen outlives its ad even briefly.
                if (slot != null) slot.removeAllViews();
                if (ad != null) ad.destroy();
                ad = loaded; loadedAt = System.currentTimeMillis();
                if (slot != null) slot.addView(card(ad, slotMedia));
            })
            .withAdListener(new AdListener() {
                @Override public void onAdFailedToLoad(LoadAdError error) { loading = false; refusedAt = System.currentTimeMillis(); }
            })
            .withNativeAdOptions(new NativeAdOptions.Builder()
                // Video creatives start silent: this app is opened in company, and a vault that suddenly talks is the opposite of discreet.
                .setVideoOptions(new VideoOptions.Builder().setStartMuted(true).build())
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .build())
            .build()
            .loadAd(new AdRequest.Builder().build());
    }

    /**
     * Builds the view for an ad. Every asset view must be handed to the {@link NativeAdView} before
     * {@link NativeAdView#setNativeAd} is called, or the SDK will not count an impression or a click.
     */
    private NativeAdView card(NativeAd loaded, boolean media) {
        NativeAdView view = new NativeAdView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL); body.setBackground(shape(surface, 20)); body.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.addView(body, new FrameLayout.LayoutParams(-1, -2));

        if (media && loaded.getMediaContent() != null) {
            MediaView panel = new MediaView(activity);
            panel.setImageScaleType(ImageView.ScaleType.CENTER_CROP);
            panel.setBackground(shape(0x22ffffff, 14)); panel.setClipToOutline(true);
            body.addView(panel, new LinearLayout.LayoutParams(-1, dp(150)));
            view.setMediaView(panel);
        }

        LinearLayout head = new LinearLayout(activity); head.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(head, margins(new LinearLayout.LayoutParams(-1, -2), 0, media ? 12 : 0, 0, 0));
        if (loaded.getIcon() != null && loaded.getIcon().getDrawable() != null) {
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(loaded.getIcon().getDrawable());
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP); icon.setBackground(shape(0x22ffffff, 13)); icon.setClipToOutline(true);
            head.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            view.setIconView(icon);
        }

        LinearLayout written = new LinearLayout(activity); written.setOrientation(LinearLayout.VERTICAL);
        head.addView(written, margins(new LinearLayout.LayoutParams(0, -2, 1), loaded.getIcon() != null ? 12 : 0, 0, 0, 0));

        LinearLayout line = new LinearLayout(activity); line.setGravity(Gravity.CENTER_VERTICAL);
        // Required by AdMob: the ad has to read as an ad, not as part of the vault.
        TextView badge = new TextView(activity);
        badge.setText(activity.getString(R.string.ad_badge)); badge.setTextSize(10); badge.setTextColor(accent); badge.setTypeface(medium);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1)); badge.setBackground(shape((accent & 0x00ffffff) | 0x33000000, 4));
        line.addView(badge);
        TextView headline = single(label(loaded.getHeadline(), 15, title, true));
        line.addView(headline, margins(new LinearLayout.LayoutParams(0, -2, 1), 8, 0, 0, 0));
        written.addView(line);
        view.setHeadlineView(headline);

        if (loaded.getBody() != null && !loaded.getBody().isEmpty()) {
            TextView text = single(label(loaded.getBody(), 13, detail, false));
            written.addView(text, margins(new LinearLayout.LayoutParams(-1, -2), 0, 2, 0, 0));
            view.setBodyView(text);
        }

        if (loaded.getCallToAction() != null && !loaded.getCallToAction().isEmpty()) {
            Button cta = new Button(activity);
            cta.setText(loaded.getCallToAction()); cta.setAllCaps(true); cta.setTextSize(15); cta.setTextColor(onAccent); cta.setTypeface(medium);
            cta.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24000000), shape(accent, 24), shape(Color.WHITE, 24)));
            cta.setStateListAnimator(null); cta.setPadding(0, 0, 0, 0);
            body.addView(cta, margins(new LinearLayout.LayoutParams(-1, dp(48)), 0, 12, 0, 0));
            view.setCallToActionView(cta);
        }

        view.setNativeAd(loaded);
        return view;
    }

    /** Called when the activity goes away. An ad left undestroyed holds on to its creative and leaks the activity. */
    void destroy() {
        slot = null;
        if (ad != null) { ad.destroy(); ad = null; }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params, int start, int top, int end, int bottom) {
        params.setMarginStart(dp(start)); params.topMargin = dp(top); params.setMarginEnd(dp(end)); params.bottomMargin = dp(bottom); return params;
    }
    private TextView label(String value, int size, int color, boolean strong) {
        TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (strong) t.setTypeface(medium); return t;
    }
    private TextView single(TextView view) { view.setMaxLines(1); view.setEllipsize(android.text.TextUtils.TruncateAt.END); return view; }
}
