package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PolygonMapView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();

    private final SectorManager sectorManager;
    private final List<SectorManager.Sector> sectors;

    private final List<Road> roads = new ArrayList<>();

    // Transformación del mapa
    private float mapOffsetX = 0f;
    private float mapOffsetY = 0f;
    private float mapScale = 1f;

    private float dataCenterX = 0f;
    private float dataCenterY = 0f;

    private float initialScale = 1f;

    // Gestos
    private float lastX;
    private float lastY;

    private float lastDistance;
    private float lastMidX;
    private float lastMidY;

    private boolean dragging = false;
    private boolean zooming = false;

    private static final float MIN_ZOOM = 0.35f;
    private static final float MAX_ZOOM = 5.0f;

    public PolygonMapView(Context context) {
        super(context);

        setFocusable(true);

        sectorManager = new SectorManager(context);
        sectors = sectorManager.getSectors();

        loadRoads(context);
        calculateDataBounds();

        // Sectores
        sectorPaint.setStyle(Paint.Style.STROKE);
        sectorPaint.setStrokeWidth(1.6f);
        sectorPaint.setColor(0xFF3E4652);

        // Calles
        roadPaint.setStyle(Paint.Style.STROKE);
        roadPaint.setStrokeCap(Paint.Cap.ROUND);
        roadPaint.setStrokeJoin(Paint.Join.ROUND);

        // Título
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setAntiAlias(true);
    }

    // =========================================================
    // CARRETERAS
    // =========================================================

    private static class Road {

        final String type;
        final String name;
        final float[] points;

        Road(String type, String name, float[] points) {
            this.type = type;
            this.name = name;
            this.points = points;
        }
    }

    private void loadRoads(Context context) {

        try {

            InputStream input =
                    context.getAssets().open("roads_el_tigre.json");

            byte[] bytes =
                    new byte[input.available()];

            input.read(bytes);
            input.close();

            String json =
                    new String(bytes, StandardCharsets.UTF_8);

            JSONObject root =
                    new JSONObject(json);

            JSONArray roadArray =
                    root.getJSONArray("roads");

            for (int i = 0; i < roadArray.length(); i++) {

                JSONObject roadObject =
                        roadArray.getJSONObject(i);

                String type =
                        roadObject.optString(
                                "type",
                                "residential"
                        );

                String name =
                        roadObject.optString(
                                "name",
                                ""
                        );

                JSONArray pointArray =
                        roadObject.getJSONArray("points");

                float[] points =
                        new float[pointArray.length() * 2];

                for (int p = 0; p < pointArray.length(); p++) {

                    JSONArray point =
                            pointArray.getJSONArray(p);

                    points[p * 2] =
                            (float) point.getDouble(0);

                    points[p * 2 + 1] =
                            (float) point.getDouble(1);
                }

                if (points.length >= 4) {

                    roads.add(
                            new Road(
                                    type,
                                    name,
                                    points
                            )
                    );
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================================================
    // LÍMITES DEL MAPA
    // =========================================================

    private void calculateDataBounds() {

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;

        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        boolean found = false;

        // Sectores
        for (SectorManager.Sector sector : sectors) {

            float[] points = sector.points;

            for (int i = 0; i < points.length; i += 2) {

                float x = points[i];
                float y = points[i + 1];

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);

                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                found = true;
            }
        }

        // Calles
        for (Road road : roads) {

            float[] points = road.points;

            for (int i = 0; i < points.length; i += 2) {

                float x = points[i];
                float y = points[i + 1];

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);

                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                found = true;
            }
        }

        if (!found) {
            return;
        }

        dataCenterX =
                (minX + maxX) / 2f;

        dataCenterY =
                (minY + maxY) / 2f;
    }

    // =========================================================
    // ESCALA INICIAL
    // =========================================================

    @Override
    protected void onSizeChanged(
            int width,
            int height,
            int oldWidth,
            int oldHeight
    ) {

        super.onSizeChanged(
                width,
                height,
                oldWidth,
                oldHeight
        );

        calculateInitialScale(
                width,
                height
        );
    }

    private void calculateInitialScale(
            int width,
            int height
    ) {

        if (sectors.isEmpty()) {
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;

        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (SectorManager.Sector sector : sectors) {

            float[] points = sector.points;

            for (int i = 0; i < points.length; i += 2) {

                float x = points[i];
                float y = points[i + 1];

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);

                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        float dataWidth =
                maxX - minX;

        float dataHeight =
                maxY - minY;

        if (dataWidth <= 0 ||
                dataHeight <= 0) {
            return;
        }

        float availableWidth =
                width * 0.88f;

        float availableHeight =
                height * 0.72f;

        float scaleX =
                availableWidth / dataWidth;

        float scaleY =
                availableHeight / dataHeight;

        initialScale =
                Math.min(
                        scaleX,
                        scaleY
                );

        mapScale =
                initialScale;
    }

    // =========================================================
    // DIBUJADO
    // =========================================================

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        backgroundPaint.setColor(
                0xFF05060B
        );

        canvas.drawRect(
                0,
                0,
                getWidth(),
                getHeight(),
                backgroundPaint
        );

        canvas.save();

        canvas.translate(
                getWidth() / 2f +
                        mapOffsetX,

                getHeight() / 2f +
                        mapOffsetY
        );

        canvas.scale(
                mapScale,
                mapScale
        );

        canvas.translate(
                -dataCenterX,
                -dataCenterY
        );

        // Primero las calles
        drawRoads(canvas);

        // Después los sectores
        drawSectors(canvas);

        canvas.restore();

        drawHeader(canvas);
    }

    private void drawRoads(Canvas canvas) {

        for (Road road : roads) {

            if (road.type.equals("motorway") ||
                    road.type.equals("trunk") ||
                    road.type.equals("primary") ||
                    road.type.equals("secondary")) {

                roadPaint.setColor(
                        0xFF78818F
                );

                roadPaint.setStrokeWidth(
                        3.0f
                );

            } else if (
                    road.type.equals("tertiary")
            ) {

                roadPaint.setColor(
                        0xFF555D69
                );

                roadPaint.setStrokeWidth(
                        2.2f
                );

            } else {

                roadPaint.setColor(
                        0xFF303640
                );

                roadPaint.setStrokeWidth(
                        1.15f
                );
            }

            float[] points =
                    road.points;

            if (points.length < 4) {
                continue;
            }

            path.reset();

            path.moveTo(
                    points[0],
                    points[1]
            );

            for (
                    int i = 2;
                    i < points.length;
                    i += 2
            ) {

                path.lineTo(
                        points[i],
                        points[i + 1]
                );
            }

            canvas.drawPath(
                    path,
                    roadPaint
            );
        }
    }

    private void drawSectors(Canvas canvas) {

        sectorPaint.setStyle(
                Paint.Style.STROKE
        );

        sectorPaint.setStrokeWidth(
                1.6f
        );

        sectorPaint.setColor(
                0xFF3E4652
        );

        for (
                SectorManager.Sector sector :
                sectors
        ) {

            float[] points =
                    sector.points;

            if (points.length < 6) {
                continue;
            }

            path.reset();

            path.moveTo(
                    points[0],
                    points[1]
            );

            for (
                    int i = 2;
                    i < points.length;
                    i += 2
            ) {

                path.lineTo(
                        points[i],
                        points[i + 1]
                );
            }

            path.close();

            canvas.drawPath(
                    path,
                    sectorPaint
            );
        }
    }

    private void drawHeader(Canvas canvas) {

        titlePaint.setColor(
                0xFFFFFFFF
        );

        titlePaint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
        );

        titlePaint.setTextSize(
                28
        );

        canvas.drawText(
                "POLYGON",
                getWidth() / 2f,
                105,
                titlePaint
        );

        titlePaint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.NORMAL
                )
        );

        titlePaint.setTextSize(
                15
        );

        canvas.drawText(
                "El Tigre",
                getWidth() / 2f,
                130,
                titlePaint
        );
    }

    // =========================================================
    // GESTOS
    // =========================================================

    @Override
    public boolean onTouchEvent(
            MotionEvent event
    ) {

        switch (
                event.getActionMasked()
        ) {

            case MotionEvent.ACTION_DOWN:

                dragging = true;
                zooming = false;

                lastX =
                        event.getX();

                lastY =
                        event.getY();

                return true;

            case MotionEvent.ACTION_POINTER_DOWN:

                if (
                        event.getPointerCount()
                                >= 2
                ) {

                    dragging = false;
                    zooming = true;

                    lastDistance =
                            distance(event);

                    lastMidX =
                            midpointX(event);

                    lastMidY =
                            midpointY(event);
                }

                return true;

            case MotionEvent.ACTION_MOVE:

                if (
                        zooming &&
                        event.getPointerCount()
                                >= 2
                ) {

                    float newDistance =
                            distance(event);

                    if (
                            lastDistance > 0
                    ) {

                        float factor =
                                newDistance /
                                        lastDistance;

                        mapScale *= factor;

                        mapScale =
                                Math.max(
                                        initialScale *
                                                MIN_ZOOM,

                                        Math.min(
                                                initialScale *
                                                        MAX_ZOOM,
                                                mapScale
                                        )
                                );
                    }

                    float newMidX =
                            midpointX(event);

                    float newMidY =
                            midpointY(event);

                    mapOffsetX +=
                            newMidX -
                                    lastMidX;

                    mapOffsetY +=
                            newMidY -
                                    lastMidY;

                    lastDistance =
                            newDistance;

                    lastMidX =
                            newMidX;

                    lastMidY =
                            newMidY;

                    invalidate();

                    return true;
                }

                if (
                        dragging &&
                        event.getPointerCount()
                                == 1
                ) {

                    float x =
                            event.getX();

                    float y =
                            event.getY();

                    mapOffsetX +=
                            x - lastX;

                    mapOffsetY +=
                            y - lastY;

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

    private float distance(
            MotionEvent event
    ) {

        if (
                event.getPointerCount()
                        < 2
        ) {
            return 0f;
        }

        float dx =
                event.getX(0) -
                        event.getX(1);

        float dy =
                event.getY(0) -
                        event.getY(1);

        return (float) Math.sqrt(
                dx * dx +
                        dy * dy
        );
    }

    private float midpointX(
            MotionEvent event
    ) {

        return (
                event.getX(0) +
                        event.getX(1)
        ) / 2f;
    }

    private float midpointY(
            MotionEvent event
    ) {

        return (
                event.getY(0) +
                        event.getY(1)
        ) / 2f;
    }
            }
