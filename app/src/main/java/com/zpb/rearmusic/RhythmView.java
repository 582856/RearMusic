package com.zpb.rearmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

// Original drawing implementation: fixed symmetric bars with an audio-driven light envelope.
public final class RhythmView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile float target;
    private float envelope;
    private long lastFrame;
    private float phase;
    private final float[] hsv = {265, 0.75f, 1};

    public RhythmView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
    }
    void level(float value) { target = value; }

    @Override protected void onDraw(Canvas canvas) {
        long now = android.os.SystemClock.uptimeMillis();
        float delta = lastFrame == 0 ? 0.033f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float decay = target > envelope ? 18 : 5;
        envelope += (target - envelope) * (1 - (float) Math.exp(-decay * delta));
        phase = (phase + delta * 0.65f) % 1;
        float step = getWidth() / 25f;
        float width = Math.max(1, step * 0.52f);
        for (int i = -12; i <= 12; i++) {
            float x = (i + 12) * step + (step - width) / 2;
            float height = getHeight() * RhythmMath.height(i);
            float y = (getHeight() - height) / 2;
            paint.setColor(0x32FFFFFF);
            canvas.drawRoundRect(x, y, x + width, y + height, width / 2, width / 2, paint);
            float distance = Math.abs(i) / 12f;
            if (envelope > 0.005f && distance <= envelope) {
                float sweep = 0.5f + 0.5f * (float) Math.cos((distance - phase) * Math.PI * 2);
                hsv[0] = 252 + 32 * sweep;
                hsv[1] = 0.25f + 0.6f * distance;
                paint.setColor(Color.HSVToColor(210 + (int) (45 * sweep), hsv));
                canvas.drawRoundRect(x, y, x + width, y + height, width / 2, width / 2, paint);
            }
        }
        if (isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(33);
    }
}
