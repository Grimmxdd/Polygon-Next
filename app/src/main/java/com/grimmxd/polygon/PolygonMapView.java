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

    // Posición y escala del mapa
    private float mapOffsetX = 0f;
    private float mapOffsetY = 0f;
    private float mapScale = 1f;

    // Gestos
    private float lastX;
    private float lastY;

    private float lastDistance;
    private float lastMidX;
    private float lastMidY;

    private boolean dragging = false;
    private boolean zooming = false;

    // Tamaño de los hexágonos
    private static final float HEX_RADIUS = 38f;

    public PolygonMapView(Context context) {
        super(context);

        setFocusable(true);

        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(2.0f);
        hexPaint.setColor(0xFF4A4F5A);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setAntiAlias(true);
        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
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

        // =========================
        // MAPA
        // =========================

        canvas.save();

        canvas.translate(
                getWidth() / 2f + mapOffsetX,
                getHeight() / 2f + mapOffsetY
        );

        canvas.scale(mapScale, mapScale);

        drawHexGrid(canvas);

        canvas.restore();

        // =========================
        // ENCABEZADO FIJO
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

    private void drawHexGrid(Canvas canvas) {

        float horizontalSpacing = HEX_RADIUS * 1.73f;
        float verticalSpacing = HEX_RADIUS * 1.50f;

        // Malla deliberadamente grande.
        // Se extiende mucho más allá de la pantalla
        // para probar pan y zoom.

        for (int row = -18; row <= 18; row++) {

            for (int col = -18; col <= 18; col++) {

                float x = col * horizontalSpacing;
                float y = row * verticalSpacing;

                if (row % 2 != 0) {
                    x += horizontalSpacing / 2f;
                }

                drawHexagon(
                        canvas,
                        x,
                        y,
                        HEX_RADIUS
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

                        float newScale =
                                mapScale * scaleFactor;

                        // Límites del zoom
                        newScale = Math.max(
                                0.45f,
                                Math.min(4.0f, newScale)
                        );

                        mapScale = newScale;
                    }

                    float newMidX = midpointX(event);
                    float newMidY = midpointY(event);

                    mapOffsetX += newMidX - lastMidX;
                    mapOffsetY += newMidY - lastMidY;

                    lastDistance = newDistance;
                    lastMidX = newMidX;
                    lastMidY = newMidY;

                    invalidate();

                    return true;
                }

                if (dragging && event.getPointerCount() == 1) {

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

                if (event.getPointerCount() <= 2) {
                    dragging = false;
                }

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

        if (event.getPointerCount() < 2) {
            return 0f;
        }

        return (
                event.getX(0) +
                event.getX(1)
        ) / 2f;
    }

    private float midpointY(MotionEvent event) {

        if (event.getPointerCount() < 2) {
            return 0f;
        }

        return (
                event.getY(0) +
                event.getY(1)
        ) / 2f;
    }
        }
