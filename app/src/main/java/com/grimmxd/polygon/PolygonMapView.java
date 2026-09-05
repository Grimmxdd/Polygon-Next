package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

public class PolygonMapView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path hexPath = new Path();

    private final SectorManager sectorManager;
    private final List<SectorManager.Sector> sectors;

    private float mapOffsetX = 0f;
    private float mapOffsetY = 0f;
    private float mapScale = 1f;

    private float lastX;
    private float lastY;

    private float lastDistance;
    private float lastMidX;
    private float lastMidY;

    private boolean dragging = false;
    private boolean zooming = false;

    public PolygonMapView(Context context) {
        super(context);

        setFocusable(true);

        sectorManager = new SectorManager(context);
        sectors = sectorManager.getSectors();

        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(2.0f);
        hexPaint.setColor(0xFF4A4F5A);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        backgroundPaint.setColor(0xFF05060B);

        canvas.drawRect(
                0,
                0,
                getWidth(),
                getHeight(),
                backgroundPaint
        );

        // =========================
        // MAPA REAL
        // =========================

        canvas.save();

        canvas.translate(
                getWidth() / 2f + mapOffsetX,
                getHeight() / 2f + mapOffsetY
        );

        canvas.scale(mapScale, mapScale);

        drawRealSectors(canvas);

        canvas.restore();

        // =========================
        // ENCABEZADO
        // =========================

        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
        titlePaint.setTextSize(28);

        canvas.drawText(
                "POLYGON",
                getWidth() / 2f,
                105,
                titlePaint
        );

        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        );
        titlePaint.setTextSize(15);

        canvas.drawText(
                "El Tigre",
                getWidth() / 2f,
                130,
                titlePaint
        );
    }

    private void drawRealSectors(Canvas canvas) {

        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(2.0f);
        hexPaint.setColor(0xFF4A4F5A);

        for (SectorManager.Sector sector : sectors) {

            float[] points = sector.points;

            if (points.length < 6) {
                continue;
            }

            hexPath.reset();

            for (int i = 0; i < points.length; i += 2) {

                float x = points[i];
                float y = points[i + 1];

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
    }

    // =========================
    // GESTOS
    // =========================

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                dragging = true;
                zooming = false;

                lastX = event.getX();
                lastY = event.getY();

                return true;

            case MotionEvent.ACTION_POINTER_DOWN:

                if (event.getPointerCount() >= 2) {

                    dragging = false;
                    zooming = true;

                    lastDistance = distance(event);

                    lastMidX = midpointX(event);
                    lastMidY = midpointY(event);
                }

                return true;

            case MotionEvent.ACTION_MOVE:

                if (zooming && event.getPointerCount() >= 2) {

                    float newDistance = distance(event);

                    if (lastDistance > 0) {

                        float scaleFactor =
                                newDistance / lastDistance;

                        mapScale *= scaleFactor;

                        mapScale = Math.max(
                                0.45f,
                                Math.min(4.0f, mapScale)
                        );
                    }

                    float newMidX = midpointX(event);
                    float newMidY = midpointY(event);

                    mapOffsetX +=
                            newMidX - lastMidX;

                    mapOffsetY +=
                            newMidY - lastMidY;

                    lastDistance = newDistance;
                    lastMidX = newMidX;
                    lastMidY = newMidY;

                    invalidate();

                    return true;
                }

                if (dragging &&
                        event.getPointerCount() == 1) {

                    float x = event.getX();
                    float y = event.getY();

                    mapOffsetX += x - lastX;
                    mapOffsetY += y - lastY;

                    lastX = x;
                    lastY = y;

                    invalidate();

                    return true;
                }

                return true;

            case MotionEvent.ACTION_POINTER_UP:

                zooming = false;
                dragging = false;

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                dragging = false;
                zooming = false;

                return true;
        }

        return true;
    }

    private float distance(MotionEvent event) {

        if (event.getPointerCount() < 2) {
            return 0f;
        }

        float dx =
                event.getX(0) - event.getX(1);

        float dy =
                event.getY(0) - event.getY(1);

        return (float) Math.sqrt(
                dx * dx + dy * dy
        );
    }

    private float midpointX(MotionEvent event) {

        return (
                event.getX(0) +
                event.getX(1)
        ) / 2f;
    }

    private float midpointY(MotionEvent event) {

        return (
                event.getY(0) +
                event.getY(1)
        ) / 2f;
    }
}
