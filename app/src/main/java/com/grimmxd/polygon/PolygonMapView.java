package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public class PolygonMapView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path hexPath = new Path();

    public PolygonMapView(Context context) {
        super(context);

        setFocusable(true);

        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(2.2f);

        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
        titlePaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Fondo
        backgroundPaint.setColor(0xFF05060B);
        canvas.drawRect(
                0,
                0,
                getWidth(),
                getHeight(),
                backgroundPaint
        );

        // Título
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(28);

        canvas.drawText(
                "POLYGON",
                getWidth() / 2f,
                55,
                titlePaint
        );

        titlePaint.setTextSize(15);
        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        );

        canvas.drawText(
                "El Tigre",
                getWidth() / 2f,
                80,
                titlePaint
        );

        // Primera malla de prueba
        drawTestHexGrid(canvas);
    }

    private void drawTestHexGrid(Canvas canvas) {

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        float radius = 45f;
        float horizontalSpacing = radius * 1.73f;
        float verticalSpacing = radius * 1.50f;

        hexPaint.setColor(0xFF4A4F5A);
        hexPaint.setStrokeWidth(2.0f);

        for (int row = -3; row <= 3; row++) {

            for (int col = -3; col <= 3; col++) {

                float x = centerX + col * horizontalSpacing;

                float y = centerY + row * verticalSpacing;

                // Desplazar filas alternas
                if (row % 2 != 0) {
                    x += horizontalSpacing / 2f;
                }

                drawHexagon(
                        canvas,
                        x,
                        y,
                        radius
                );
            }
        }
    }

    private void drawHexagon(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius
    ) {

        hexPath.reset();

        for (int i = 0; i < 6; i++) {

            double angle =
                    Math.toRadians(60 * i - 30);

            float x =
                    centerX +
                    (float) Math.cos(angle) * radius;

            float y =
                    centerY +
                    (float) Math.sin(angle) * radius;

            if (i == 0) {
                hexPath.moveTo(x, y);
            } else {
                hexPath.lineTo(x, y);
            }
        }

        hexPath.close();

        canvas.drawPath(
                hexPath,
                hexPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }
}
