package com.rokkystudio.qrscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws the scanner frame over the camera preview and keeps the QR target area visually centered.
 */
public class ScannerOverlayView extends View {
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();

    public ScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Configures paints used for the dimmed preview layer and the bright scanner brackets.
     */
    private void init() {
        dimPaint.setColor(0x88000000);
        dimPaint.setStyle(Paint.Style.FILL);

        framePaint.setColor(0xFF38D996);
        framePaint.setStrokeWidth(dp(4f));
        framePaint.setStrokeCap(Paint.Cap.ROUND);
        framePaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Renders a centered square scanning window and corner brackets over the preview.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float frameSize = Math.min(width, height) * 0.68f;
        float left = (width - frameSize) / 2f;
        float top = (height - frameSize) / 2f;
        float right = left + frameSize;
        float bottom = top + frameSize;
        float radius = dp(18f);
        float bracket = frameSize * 0.22f;

        frameRect.set(left, top, right, bottom);

        canvas.drawRect(0f, 0f, width, top, dimPaint);
        canvas.drawRect(0f, bottom, width, height, dimPaint);
        canvas.drawRect(0f, top, left, bottom, dimPaint);
        canvas.drawRect(right, top, width, bottom, dimPaint);

        canvas.drawRoundRect(frameRect, radius, radius, framePaint);

        canvas.drawLine(left, top, left + bracket, top, framePaint);
        canvas.drawLine(left, top, left, top + bracket, framePaint);
        canvas.drawLine(right, top, right - bracket, top, framePaint);
        canvas.drawLine(right, top, right, top + bracket, framePaint);
        canvas.drawLine(left, bottom, left + bracket, bottom, framePaint);
        canvas.drawLine(left, bottom, left, bottom - bracket, framePaint);
        canvas.drawLine(right, bottom, right - bracket, bottom, framePaint);
        canvas.drawLine(right, bottom, right, bottom - bracket, framePaint);
    }

    /**
     * Converts density-independent pixels to the device pixel size used by Canvas drawing.
     */
    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
