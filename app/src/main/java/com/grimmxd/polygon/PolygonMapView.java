package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public class PolygonMapView extends View {

    private final Paint backgroundPaint = new Paint();
    private final Paint textPaint = new Paint();

    public PolygonMapView(Context context) {
        super(context);

        backgroundPaint.setStyle(Paint.Style.FILL);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        backgroundPaint.setColor(0xFF05060B);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(42);

        canvas.drawText(
                "POLYGON",
                getWidth() / 2f,
                getHeight() / 2f,
                textPaint
        );

        textPaint.setTextSize(20);
        canvas.drawText(
                "El Tigre",
                getWidth() / 2f,
                getHeight() / 2f + 40,
                textPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }
}
