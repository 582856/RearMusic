package com.zephyr.rearmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

// Draws only the audio rhythm over the native rear-screen music card.
public final class RhythmView extends View {
    private static final int[] DEFAULT_PALETTE = {0xFF52E7FF, 0xFF9B6CFF, 0xFFFF5FC8};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile int[] palette = DEFAULT_PALETTE;
    private boolean frozen;
    private static final int[] BAR_ALPHA = {38, 51, 77, 102, 128, 153, 166, 179, 191, 204, 217, 230, 255};
    private int activeSpan = -1;
    private int previousLow;
    private android.animation.AnimatorSet pulse;
    private long lastFrame;
    private float cameraWidth;

    void cameraWidth(float value) { cameraWidth = value; }

    public RhythmView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
    }

    void level(float value) {
        long sampledAt = android.os.SystemClock.uptimeMillis();
        post(() -> {
            if (BuildConfig.DIAGNOSTIC) DebugLog.sample("render-input", "level=" + value + " attached=" + isAttachedToWindow() + " frozen=" + frozen + " alpha=" + getAlpha() + " shown=" + isShown());
            if (!isAttachedToWindow() || frozen) return;
            if (lastFrame != 0 && sampledAt - lastFrame < 320) return;
            long interval = lastFrame == 0 ? 0 : sampledAt - lastFrame;
            lastFrame = sampledAt;
            if (value <= 0.001f) {
                if (pulse != null) pulse.cancel();
                activeSpan = -1;
                previousLow = 0;
                invalidate();
            } else if (interval > 0) {
                animatePulse(Math.min(12, Math.round(value * 12)), interval);
            }
        });
    }

    void freeze() { frozen = true; if (pulse != null) pulse.cancel(); }

    void palette(int[] colors) {
        if (colors == null || colors.length < 3) return;
        if (palette[0] == colors[0] && palette[1] == colors[1] && palette[2] == colors[2]) return;
        palette = new int[] {colors[0], colors[1], colors[2]};
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {

        if (BuildConfig.DIAGNOSTIC) DebugLog.sample("draw", "span=" + activeSpan + " size=" + getWidth() + "x" + getHeight() + " alpha=" + getAlpha());
        float width = cameraWidth > 0 ? cameraWidth : getWidth();
        float height = getHeight();
        float capsuleWidth = width * 0.96f;
        float capsuleHeight = height * 0.94f;
        RectF capsule = new RectF((getWidth() - capsuleWidth) / 2, (height - capsuleHeight) / 2,
            (getWidth() + capsuleWidth) / 2, (height + capsuleHeight) / 2);
        int[] colors = palette.clone();
        for (int i = 0; i < colors.length; i++) {
            float[] hsv = new float[3];
            Color.colorToHSV(colors[i], hsv);
            hsv[2] = Math.max(0.85f, hsv[2]);
            colors[i] = Color.HSVToColor(hsv);
        }
        paint.setShader(null);
        paint.setColor(Color.BLACK);
        canvas.drawRoundRect(capsule, capsuleWidth / 2, capsuleWidth / 2, paint);
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), height);
        drawBars(canvas, capsule.centerX(), capsule.centerY(), width * 0.917f,
            capsuleWidth, colors);
        canvas.restore();

    }

    private void drawBars(Canvas canvas, float centerX, float centerY, float bandWidth,
                          float width, int[] colors) {
        float step = bandWidth / 24.58f;
        float thickness = Math.max(3, step * 0.58f);
        Shader barShader = new LinearGradient(centerX - bandWidth / 2, centerY,
            centerX + bandWidth / 2, centerY, colors, null, Shader.TileMode.CLAMP);
        for (int i = -12; i <= 12; i++) {
            float barHeight = RhythmMath.cameraGap(i * step * (0.84f / 0.917f), cameraWidth * 0.42f, getHeight() * 0.5f, 12f) * 0.72f;
            float x = centerX + i * step;
            float left = x - thickness / 2;
            float top = centerY - barHeight / 2;
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setAlpha(30);
            canvas.drawRoundRect(left, top, left + thickness, top + barHeight,
                thickness / 2, thickness / 2, paint);
            if (Math.abs(i) <= activeSpan) {
                paint.setColor(Color.WHITE);
                paint.setAlpha(255);
                canvas.drawRoundRect(left, top, left + thickness, top + barHeight,
                    thickness / 2, thickness / 2, paint);
                paint.setShader(barShader);
                paint.setAlpha(BAR_ALPHA[Math.abs(i)]);
                canvas.drawRoundRect(left, top, left + thickness, top + barHeight,
                    thickness / 2, thickness / 2, paint);
            }
        }
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void animatePulse(int peak, long interval) {
        if (pulse != null) pulse.cancel();
        int low = java.util.concurrent.ThreadLocalRandom.current().nextInt(peak + 1);
        android.animation.ValueAnimator rise = android.animation.ValueAnimator.ofInt(previousLow, peak);
        android.animation.ValueAnimator fall = android.animation.ValueAnimator.ofInt(peak, low);
        previousLow = low;
        rise.setDuration(interval / 2);
        fall.setDuration(interval / 2);
        rise.setInterpolator(new android.view.animation.AnticipateOvershootInterpolator());
        fall.setInterpolator(new android.view.animation.DecelerateInterpolator());
        android.animation.ValueAnimator.AnimatorUpdateListener update = animation -> {
            activeSpan = Math.max(0, Math.min(12, (Integer) animation.getAnimatedValue()));
            invalidate();
        };
        rise.addUpdateListener(update);
        fall.addUpdateListener(update);
        pulse = new android.animation.AnimatorSet();
        pulse.playSequentially(rise, fall);
        pulse.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (pulse != null) pulse.cancel();
        lastFrame = 0;
        super.onDetachedFromWindow();
    }
}
