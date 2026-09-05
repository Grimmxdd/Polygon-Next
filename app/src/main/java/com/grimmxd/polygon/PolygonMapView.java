package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PolygonMapView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path reusablePath = new Path();

    private final SectorManager sectorManager;
    private final List<SectorManager.Sector> sectors;
    private final List<Road> roads = new ArrayList<>();
    private final List<RoadLabel> roadLabels = new ArrayList<>();

    private float mapOffsetX = 0f;
    private float mapOffsetY = 0f;
    private float mapScale = 1f;

    private float dataCenterX = 0f;
    private float dataCenterY = 0f;
    private float initialScale = 1f;

    private float lastX;
    private float lastY;

    private float lastDistance;
    private float lastMidX;
    private float lastMidY;

    private boolean dragging = false;
    private boolean zooming = false;

    private static final float MIN_ZOOM = 0.35f;
    private static final float MAX_ZOOM = 5.0f;

    // Only major named roads receive labels.
    private static final Set<String> LABEL_TYPES = new HashSet<>();

    static {
        LABEL_TYPES.add("motorway");
        LABEL_TYPES.add("trunk");
        LABEL_TYPES.add("primary");
        LABEL_TYPES.add("secondary");
        LABEL_TYPES.add("tertiary");
    }

    private static class Road {
        final String type;
        final String name;
        final Path path = new Path();
        final RectF bounds = new RectF();

        Road(String type, String name, float[] points) {
            this.type = type;
            this.name = name;

            if (points.length >= 4) {
                path.moveTo(points[0], points[1]);
                for (int i = 2; i < points.length; i += 2) {
                    path.lineTo(points[i], points[i + 1]);
                }
                path.computeBounds(bounds, true);
            }
        }
    }

    private static class RoadLabel {
        final String name;
        final float x;
        final float y;
        final float angleDegrees;
        final float length;

        RoadLabel(String name, float x, float y, float angleDegrees, float length) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.angleDegrees = angleDegrees;
            this.length = length;
        }
    }

    public PolygonMapView(Context context) {
        super(context);

        setFocusable(true);
        setBackgroundColor(Color.TRANSPARENT);

        sectorManager = new SectorManager(context);
        sectors = sectorManager.getSectors();

        loadRoads(context);
        calculateDataBounds();
        buildRoadLabels();

        roadPaint.setStyle(Paint.Style.STROKE);
        roadPaint.setStrokeCap(Paint.Cap.ROUND);
        roadPaint.setStrokeJoin(Paint.Join.ROUND);
        roadPaint.setAntiAlias(true);

        sectorPaint.setStyle(Paint.Style.STROKE);
        sectorPaint.setStrokeWidth(1.6f);
        sectorPaint.setColor(0x553E4652);
        sectorPaint.setAntiAlias(true);

        roadLabelPaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
        roadLabelPaint.setTextAlign(Paint.Align.CENTER);
        roadLabelPaint.setAntiAlias(true);
        roadLabelPaint.setColor(0xBDE9EDF2);
        roadLabelPaint.setTextSize(12f);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setAntiAlias(true);
    }

    private void loadRoads(Context context) {
        try (InputStream input = context.getAssets().open("roads_el_tigre.json")) {

            byte[] bytes = new byte[input.available()];
            int offset = 0;
            int read;

            while (offset < bytes.length &&
                    (read = input.read(bytes, offset, bytes.length - offset)) > 0) {
                offset += read;
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            JSONArray roadArray = root.getJSONArray("roads");

            for (int i = 0; i < roadArray.length(); i++) {
                JSONObject roadObject = roadArray.getJSONObject(i);

                String type = roadObject.optString("type", "residential");
                String name = roadObject.optString("name", "").trim();

                JSONArray pointArray = roadObject.getJSONArray("points");
                float[] points = new float[pointArray.length() * 2];

                for (int p = 0; p < pointArray.length(); p++) {
                    JSONArray point = pointArray.getJSONArray(p);
                    points[p * 2] = (float) point.getDouble(0);
                    points[p * 2 + 1] = (float) point.getDouble(1);
                }

                if (points.length >= 4) {
                    roads.add(new Road(type, name, points));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void calculateDataBounds() {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;

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

        for (Road road : roads) {
            RectF b = road.bounds;
            minX = Math.min(minX, b.left);
            maxX = Math.max(maxX, b.right);
            minY = Math.min(minY, b.top);
            maxY = Math.max(maxY, b.bottom);
            found = true;
        }

        if (found) {
            dataCenterX = (minX + maxX) / 2f;
            dataCenterY = (minY + maxY) / 2f;
        }
    }

    private void buildRoadLabels() {
        Set<String> alreadyPlaced = new HashSet<>();

        for (Road road : roads) {
            if (!LABEL_TYPES.contains(road.type) || road.name.isEmpty()) {
                continue;
            }

            if (alreadyPlaced.contains(road.name)) {
                continue;
            }

            float[] longestSegment = findLongestSegment(road.path);

            if (longestSegment == null) {
                continue;
            }

            float x1 = longestSegment[0];
            float y1 = longestSegment[1];
            float x2 = longestSegment[2];
            float y2 = longestSegment[3];

            float x = (x1 + x2) * 0.5f;
            float y = (y1 + y2) * 0.5f;

            double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));

            // Keep labels roughly readable instead of upside-down.
            if (angle > 90 || angle < -90) {
                angle += 180;
            }

            float length = (float) Math.hypot(x2 - x1, y2 - y1);

            roadLabels.add(
                    new RoadLabel(
                            road.name,
                            x,
                            y,
                            (float) angle,
                            length
                    )
            );

            alreadyPlaced.add(road.name);
        }
    }

    private float[] findLongestSegment(Path path) {
        // Android Path is iterable only through PathMeasure, so use PathMeasure
        // to recover a representative long segment.
        android.graphics.PathMeasure measure =
                new android.graphics.PathMeasure(path, false);

        float total = measure.getLength();

        if (total <= 0f) {
            return null;
        }

        float bestLength = 0f;
        float bestX1 = 0f;
        float bestY1 = 0f;
        float bestX2 = 0f;
        float bestY2 = 0f;

        float[] p1 = new float[2];
        float[] p2 = new float[2];

        final int samples = Math.max(2, Math.min(24, (int) (total / 35f)));

        measure.getPosTan(0f, p1, null);

        for (int i = 1; i <= samples; i++) {
            float d = total * i / samples;
            measure.getPosTan(d, p2, null);

            float segment = (float) Math.hypot(
                    p2[0] - p1[0],
                    p2[1] - p1[1]
            );

            if (segment > bestLength) {
                bestLength = segment;
                bestX1 = p1[0];
                bestY1 = p1[1];
                bestX2 = p2[0];
                bestY2 = p2[1];
            }

            p1[0] = p2[0];
            p1[1] = p2[1];
        }

        return new float[]{
                bestX1, bestY1,
                bestX2, bestY2
        };
    }

    @Override
    protected void onSizeChanged(
            int width,
            int height,
            int oldWidth,
            int oldHeight
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        calculateInitialScale(width, height);
    }

    private void calculateInitialScale(int width, int height) {
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
                minX = Math.min(minX, points[i]);
                maxX = Math.max(maxX, points[i]);
                minY = Math.min(minY, points[i + 1]);
                maxY = Math.max(maxY, points[i + 1]);
            }
        }

        float dataWidth = maxX - minX;
        float dataHeight = maxY - minY;

        if (dataWidth <= 0f || dataHeight <= 0f) {
            return;
        }

        float availableWidth = width * 0.88f;
        float availableHeight = height * 0.72f;

        float scaleX = availableWidth / dataWidth;
        float scaleY = availableHeight / dataHeight;

        initialScale = Math.min(scaleX, scaleY);
        mapScale = initialScale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        backgroundPaint.setColor(0xFF05060B);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        canvas.save();

        canvas.translate(
                getWidth() / 2f + mapOffsetX,
                getHeight() / 2f + mapOffsetY
        );

        canvas.scale(mapScale, mapScale);
        canvas.translate(-dataCenterX, -dataCenterY);

        RectF visible = getVisibleDataRect();

        drawRoads(canvas, visible);
        drawSectors(canvas, visible);
        drawRoadLabels(canvas, visible);

        canvas.restore();

        drawHeader(canvas);
    }

    private RectF getVisibleDataRect() {
        float left = (0f - getWidth() / 2f - mapOffsetX) / mapScale + dataCenterX;
        float right = (getWidth() - getWidth() / 2f - mapOffsetX) / mapScale + dataCenterX;
        float top = (0f - getHeight() / 2f - mapOffsetY) / mapScale + dataCenterY;
        float bottom = (getHeight() - getHeight() / 2f - mapOffsetY) / mapScale + dataCenterY;

        // Small margin avoids pop-in while panning.
        float marginX = Math.abs(right - left) * 0.08f;
        float marginY = Math.abs(bottom - top) * 0.08f;

        return new RectF(
                left - marginX,
                top - marginY,
                right + marginX,
                bottom + marginY
        );
    }

    private void drawRoads(Canvas canvas, RectF visible) {
        for (Road road : roads) {
            if (!RectF.intersects(road.bounds, visible)) {
                continue;
            }

            switch (road.type) {
                case "motorway":
                case "trunk":
                case "primary":
                case "secondary":
                    roadPaint.setColor(0xFF78818F);
                    roadPaint.setStrokeWidth(3.0f);
                    break;

                case "tertiary":
                    roadPaint.setColor(0xFF555D69);
                    roadPaint.setStrokeWidth(2.2f);
                    break;

                default:
                    roadPaint.setColor(0xFF303640);
                    roadPaint.setStrokeWidth(1.15f);
                    break;
            }

            canvas.drawPath(road.path, roadPaint);
        }
    }

    private void drawSectors(Canvas canvas, RectF visible) {
        sectorPaint.setStyle(Paint.Style.STROKE);
        sectorPaint.setStrokeWidth(1.6f);
        sectorPaint.setColor(0x553E4652);

        for (SectorManager.Sector sector : sectors) {
            float[] points = sector.points;

            if (points.length < 6) {
                continue;
            }

            reusablePath.reset();
            reusablePath.moveTo(points[0], points[1]);

            float minX = points[0];
            float maxX = points[0];
            float minY = points[1];
            float maxY = points[1];

            for (int i = 2; i < points.length; i += 2) {
                float x = points[i];
                float y = points[i + 1];

                reusablePath.lineTo(x, y);

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }

            reusablePath.close();

            if (maxX < visible.left ||
                    minX > visible.right ||
                    maxY < visible.top ||
                    minY > visible.bottom) {
                continue;
            }

            canvas.drawPath(reusablePath, sectorPaint);
        }
    }

    private void drawRoadLabels(Canvas canvas, RectF visible) {
        if (mapScale < initialScale * 0.72f) {
            return;
        }

        for (RoadLabel label : roadLabels) {
            if (!visible.contains(label.x, label.y)) {
                continue;
            }

            float textSize = Math.max(
                    9f,
                    Math.min(
                            15f,
                            12f / Math.max(0.8f, (initialScale / mapScale))
                    )
            );

            roadLabelPaint.setTextSize(textSize);

            canvas.save();
            canvas.rotate(
                    label.angleDegrees,
                    label.x,
                    label.y
            );

            canvas.drawText(
                    label.name.toUpperCase(Locale.ROOT),
                    label.x,
                    label.y,
                    roadLabelPaint
            );

            canvas.restore();
        }
    }

    private void drawHeader(Canvas canvas) {
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
        titlePaint.setTextSize(28f);

        canvas.drawText(
                "POLYGON",
                getWidth() / 2f,
                105f,
                titlePaint
        );

        titlePaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        );
        titlePaint.setTextSize(15f);

        canvas.drawText(
                "El Tigre",
                getWidth() / 2f,
                130f,
                titlePaint
        );
    }

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

                    float oldDistance = lastDistance;
                    float newDistance = distance(event);

                    float oldMidX = lastMidX;
                    float oldMidY = lastMidY;

                    float newMidX = midpointX(event);
                    float newMidY = midpointY(event);

                    if (oldDistance > 1f && newDistance > 1f) {

                        float oldScale = mapScale;
                        float scaleFactor = newDistance / oldDistance;

                        float newScale = oldScale * scaleFactor;
                        newScale = Math.max(
                                initialScale * MIN_ZOOM,
                                Math.min(
                                        initialScale * MAX_ZOOM,
                                        newScale
                                )
                        );

                        // Keep the point under the fingers fixed while zooming.
                        float contentX =
                                (oldMidX - getWidth() / 2f - mapOffsetX)
                                        / oldScale
                                        + dataCenterX;

                        float contentY =
                                (oldMidY - getHeight() / 2f - mapOffsetY)
                                        / oldScale
                                        + dataCenterY;

                        mapScale = newScale;

                        mapOffsetX =
                                newMidX
                                        - getWidth() / 2f
                                        - (contentX - dataCenterX) * newScale;

                        mapOffsetY =
                                newMidY
                                        - getHeight() / 2f
                                        - (contentY - dataCenterY) * newScale;
                    }

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

        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);

        return (float) Math.hypot(dx, dy);
    }

    private float midpointX(MotionEvent event) {
        return (event.getX(0) + event.getX(1)) * 0.5f;
    }

    private float midpointY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) * 0.5f;
    }
}
