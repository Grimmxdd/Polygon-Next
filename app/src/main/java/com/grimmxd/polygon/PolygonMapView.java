package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PolygonMapView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SectorManager sectorManager;
    private final List<SectorManager.Sector> sectors;
    private final List<Road> roads = new ArrayList<>();
    private final List<RoadLabel> roadLabels = new ArrayList<>();

    private final SpatialGrid<Road> roadGrid = new SpatialGrid<>();
    private final SpatialGrid<SectorManager.Sector> sectorGrid = new SpatialGrid<>();

    // Reused label collision rectangles. No per-frame RectF allocations.
    private final ArrayList<RectF> occupiedLabelBounds = new ArrayList<>();

    private float mapOffsetX = 0f;
    private float mapOffsetY = 0f;
    private float mapScale = 1f;

    private float dataCenterX = 0f;
    private float dataCenterY = 0f;
    private float dataMinX = 0f;
    private float dataMinY = 0f;
    private float dataMaxX = 0f;
    private float dataMaxY = 0f;

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

    /*
     * LOD is based on zoom relative to the initial map fit.
     *
     * < 0.55 : sectors only
     * 0.55-0.90 : main roads
     * 0.90-1.80 : main + tertiary
     * > 1.80 : all roads
     */
    private static final float LOD_MAIN_ROADS = 0.55f;
    private static final float LOD_TERTIARY = 0.90f;
    private static final float LOD_ALL_ROADS = 1.80f;

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

        int priority() {
            if ("motorway".equals(type)) return 5;
            if ("trunk".equals(type)) return 4;
            if ("primary".equals(type)) return 3;
            if ("secondary".equals(type)) return 2;
            if ("tertiary".equals(type)) return 1;
            return 0;
        }
    }

    private static class RoadLabel {
        final String name;
        final float x;
        final float y;
        final float angleDegrees;
        final float length;
        final int priority;

        RoadLabel(
                String name,
                float x,
                float y,
                float angleDegrees,
                float length,
                int priority
        ) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.angleDegrees = angleDegrees;
            this.length = length;
            this.priority = priority;
        }
    }

    /**
     * Small in-memory spatial index.
     * It behaves like internal map tiles: only objects in cells touched by
     * the visible rectangle are considered for drawing.
     */
    private static class SpatialGrid<T> {

        private final Map<Long, ArrayList<T>> cells = new HashMap<>();
        private final ArrayList<T> results = new ArrayList<>();
        private final HashSet<T> seen = new HashSet<>();

        private float minX;
        private float minY;
        private float maxX;
        private float maxY;
        private float cellWidth;
        private float cellHeight;
        private int columns;
        private int rows;
        private boolean ready = false;

        void build(
                List<T> objects,
                float minX,
                float minY,
                float maxX,
                float maxY,
                int columns,
                int rows,
                BoundsProvider<T> provider
        ) {
            cells.clear();
            results.clear();
            seen.clear();

            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.columns = Math.max(1, columns);
            this.rows = Math.max(1, rows);

            float width = Math.max(1f, maxX - minX);
            float height = Math.max(1f, maxY - minY);

            cellWidth = width / this.columns;
            cellHeight = height / this.rows;

            for (T object : objects) {
                RectF b = provider.bounds(object);

                int startX = cellX(b.left);
                int endX = cellX(b.right);
                int startY = cellY(b.top);
                int endY = cellY(b.bottom);

                for (int y = startY; y <= endY; y++) {
                    for (int x = startX; x <= endX; x++) {
                        long key = key(x, y);
                        ArrayList<T> list = cells.get(key);

                        if (list == null) {
                            list = new ArrayList<>();
                            cells.put(key, list);
                        }

                        list.add(object);
                    }
                }
            }

            ready = true;
        }

        ArrayList<T> query(RectF visible, BoundsProvider<T> provider) {
            results.clear();
            seen.clear();

            if (!ready) {
                return results;
            }

            int startX = cellX(visible.left);
            int endX = cellX(visible.right);
            int startY = cellY(visible.top);
            int endY = cellY(visible.bottom);

            for (int y = startY; y <= endY; y++) {
                for (int x = startX; x <= endX; x++) {
                    ArrayList<T> list = cells.get(key(x, y));

                    if (list == null) {
                        continue;
                    }

                    for (T object : list) {
                        if (seen.add(object) &&
                                RectF.intersects(provider.bounds(object), visible)) {
                            results.add(object);
                        }
                    }
                }
            }

            return results;
        }

        private int cellX(float x) {
            if (x <= minX) return 0;
            if (x >= maxX) return columns - 1;

            int value = (int) ((x - minX) / cellWidth);
            return Math.max(0, Math.min(columns - 1, value));
        }

        private int cellY(float y) {
            if (y <= minY) return 0;
            if (y >= maxY) return rows - 1;

            int value = (int) ((y - minY) / cellHeight);
            return Math.max(0, Math.min(rows - 1, value));
        }

        private long key(int x, int y) {
            return (((long) y) << 32) ^ (x & 0xffffffffL);
        }
    }

    private interface BoundsProvider<T> {
        RectF bounds(T object);
    }

    private static final BoundsProvider<Road> ROAD_BOUNDS_PROVIDER =
            new BoundsProvider<Road>() {
                @Override
                public RectF bounds(Road object) {
                    return object.bounds;
                }
            };

    private static final BoundsProvider<SectorManager.Sector> SECTOR_BOUNDS_PROVIDER =
            new BoundsProvider<SectorManager.Sector>() {
                @Override
                public RectF bounds(SectorManager.Sector object) {
                    return object.bounds;
                }
            };

    public PolygonMapView(Context context) {
        super(context);

        setFocusable(true);
        setBackgroundColor(Color.TRANSPARENT);

        sectorManager = new SectorManager(context);
        sectors = sectorManager.getSectors();

        loadRoads(context);
        calculateDataBounds();
        buildSpatialIndexes();
        buildRoadLabels();

        roadPaint.setStyle(Paint.Style.STROKE);
        roadPaint.setStrokeCap(Paint.Cap.ROUND);
        roadPaint.setStrokeJoin(Paint.Join.ROUND);
        roadPaint.setAntiAlias(true);

        sectorPaint.setStyle(Paint.Style.STROKE);
        sectorPaint.setStrokeWidth(1.6f);
        sectorPaint.setColor(0x553E4652);
        sectorPaint.setAntiAlias(true);

        /*
         * Smaller, softer and translucent road labels.
         * The collision system below prevents labels from stacking over each other.
         */
        roadLabelPaint.setTypeface(
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        );
        roadLabelPaint.setTextAlign(Paint.Align.CENTER);
        roadLabelPaint.setAntiAlias(true);
        roadLabelPaint.setColor(0xA8E9EDF2);
        roadLabelPaint.setTextSize(10f);

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setAntiAlias(true);
    }

    private void loadRoads(Context context) {
        try {
            InputStream input = context.getAssets().open("roads_el_tigre.json");

            byte[] bytes = new byte[input.available()];
            int offset = 0;
            int read;

            while (offset < bytes.length &&
                    (read = input.read(bytes, offset, bytes.length - offset)) > 0) {
                offset += read;
            }

            input.close();

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
            RectF b = sector.bounds;

            if (b.width() <= 0f || b.height() <= 0f) {
                continue;
            }

            minX = Math.min(minX, b.left);
            maxX = Math.max(maxX, b.right);
            minY = Math.min(minY, b.top);
            maxY = Math.max(maxY, b.bottom);
            found = true;
        }

        for (Road road : roads) {
            RectF b = road.bounds;

            if (b.width() <= 0f || b.height() <= 0f) {
                continue;
            }

            minX = Math.min(minX, b.left);
            maxX = Math.max(maxX, b.right);
            minY = Math.min(minY, b.top);
            maxY = Math.max(maxY, b.bottom);
            found = true;
        }

        if (found) {
            dataMinX = minX;
            dataMinY = minY;
            dataMaxX = maxX;
            dataMaxY = maxY;

            dataCenterX = (minX + maxX) * 0.5f;
            dataCenterY = (minY + maxY) * 0.5f;
        }
    }

    private void buildSpatialIndexes() {
        /*
         * 12 x 12 is deliberately modest: enough partitioning to avoid
         * scanning the entire El Tigre map while keeping the index tiny.
         */
        sectorGrid.build(
                sectors,
                dataMinX,
                dataMinY,
                dataMaxX,
                dataMaxY,
                12,
                12,
                SECTOR_BOUNDS_PROVIDER
        );

        roadGrid.build(
                roads,
                dataMinX,
                dataMinY,
                dataMaxX,
                dataMaxY,
                12,
                12,
                ROAD_BOUNDS_PROVIDER
        );
    }

    private void buildRoadLabels() {
        Map<String, RoadLabel> bestByName = new HashMap<>();

        for (Road road : roads) {
            if (!LABEL_TYPES.contains(road.type) || road.name.isEmpty()) {
                continue;
            }

            String normalizedName = road.name
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toUpperCase(Locale.ROOT);

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

            // Keep labels readable instead of upside-down.
            if (angle > 90.0 || angle < -90.0) {
                angle += 180.0;
            }

            float length = (float) Math.hypot(x2 - x1, y2 - y1);

            RoadLabel candidate = new RoadLabel(
                    road.name,
                    x,
                    y,
                    (float) angle,
                    length,
                    road.priority()
            );

            RoadLabel previous = bestByName.get(normalizedName);

            /*
             * Prefer the most important road. If priority is equal, use the
             * longest segment because it normally provides the cleanest label.
             */
            if (previous == null ||
                    candidate.priority > previous.priority ||
                    (candidate.priority == previous.priority &&
                            candidate.length > previous.length)) {
                bestByName.put(normalizedName, candidate);
            }
        }

        roadLabels.clear();
        roadLabels.addAll(bestByName.values());

        // Highest-priority and longest labels are considered first for placement.
        Collections.sort(
                roadLabels,
                new Comparator<RoadLabel>() {
                    @Override
                    public int compare(RoadLabel a, RoadLabel b) {
                        if (a.priority != b.priority) {
                            return b.priority - a.priority;
                        }

                        return Float.compare(b.length, a.length);
                    }
                }
        );
    }

    private float[] findLongestSegment(Path path) {
        PathMeasure measure = new PathMeasure(path, false);
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

        final int samples = Math.max(
                2,
                Math.min(24, (int) (total / 35f))
        );

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

        if (bestLength <= 0f) {
            return null;
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
        if (dataMaxX <= dataMinX || dataMaxY <= dataMinY) {
            return;
        }

        float dataWidth = dataMaxX - dataMinX;
        float dataHeight = dataMaxY - dataMinY;

        float availableWidth = width * 0.88f;
        float availableHeight = height * 0.72f;

        float scaleX = availableWidth / dataWidth;
        float scaleY = availableHeight / dataHeight;

        initialScale = Math.min(scaleX, scaleY);

        if (initialScale <= 0f || Float.isNaN(initialScale)) {
            return;
        }

        mapScale = initialScale;
    }

    private float getZoomRatio() {
        if (initialScale <= 0f) {
            return 1f;
        }

        return mapScale / initialScale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        backgroundPaint.setColor(0xFF05060B);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        if (mapScale <= 0f) {
            drawHeader(canvas);
            return;
        }

        canvas.save();

        canvas.translate(
                getWidth() / 2f + mapOffsetX,
                getHeight() / 2f + mapOffsetY
        );

        canvas.scale(mapScale, mapScale);
        canvas.translate(-dataCenterX, -dataCenterY);

        RectF visible = getVisibleDataRect();
        float zoomRatio = getZoomRatio();

        // Sectors remain useful at every zoom level.
        drawSectors(canvas, visible);

        // Roads use automatic LOD.
        if (zoomRatio >= LOD_MAIN_ROADS) {
            drawRoads(canvas, visible, zoomRatio);
        }

        // Labels use a separate, more conservative LOD.
        if (zoomRatio >= 0.85f) {
            drawRoadLabels(canvas, visible, zoomRatio);
        }

        canvas.restore();

        drawHeader(canvas);
    }

    private RectF getVisibleDataRect() {
        float safeScale = Math.max(0.0001f, mapScale);

        float left =
                (0f - getWidth() / 2f - mapOffsetX)
                        / safeScale
                        + dataCenterX;

        float right =
                (getWidth() - getWidth() / 2f - mapOffsetX)
                        / safeScale
                        + dataCenterX;

        float top =
                (0f - getHeight() / 2f - mapOffsetY)
                        / safeScale
                        + dataCenterY;

        float bottom =
                (getHeight() - getHeight() / 2f - mapOffsetY)
                        / safeScale
                        + dataCenterY;

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

    private void drawRoads(
            Canvas canvas,
            RectF visible,
            float zoomRatio
    ) {
        ArrayList<Road> visibleRoads =
                roadGrid.query(visible, ROAD_BOUNDS_PROVIDER);

        for (Road road : visibleRoads) {
            int priority = road.priority();

            /*
             * LOD:
             * 0.55-0.90 -> motorway/trunk/primary/secondary
             * 0.90-1.80 -> + tertiary
             * >1.80      -> all roads
             */
            if (zoomRatio < LOD_TERTIARY && priority < 2) {
                continue;
            }

            if (zoomRatio < LOD_ALL_ROADS && priority < 1) {
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

        ArrayList<SectorManager.Sector> visibleSectors =
                sectorGrid.query(visible, SECTOR_BOUNDS_PROVIDER);

        for (SectorManager.Sector sector : visibleSectors) {
            if (sector.points.length < 6) {
                continue;
            }

            // Path and bounds were computed once during loading.
            canvas.drawPath(sector.path, sectorPaint);
        }
    }

    private void drawRoadLabels(
            Canvas canvas,
            RectF visible,
            float zoomRatio
    ) {
        /*
         * At lower zooms show fewer labels. At close zooms, allow all
         * configured major-road labels, still subject to collision tests.
         */
        float labelDensity;

        if (zoomRatio < 1.10f) {
            labelDensity = 0.72f;
        } else if (zoomRatio < 1.80f) {
            labelDensity = 0.88f;
        } else {
            labelDensity = 1.0f;
        }

        occupiedLabelBounds.clear();

        float textSizePx;

        if (zoomRatio < 1.10f) {
            textSizePx = 9f;
        } else if (zoomRatio < 1.80f) {
            textSizePx = 9.5f;
        } else {
            textSizePx = 10f;
        }

        roadLabelPaint.setTextSize(textSizePx);

        int maxLabels;

        if (zoomRatio < 1.10f) {
            maxLabels = 14;
        } else if (zoomRatio < 1.80f) {
            maxLabels = 24;
        } else {
            maxLabels = 40;
        }

        int drawn = 0;

        for (RoadLabel label : roadLabels) {
            if (drawn >= maxLabels) {
                break;
            }

            if (!visible.contains(label.x, label.y)) {
                continue;
            }

            // Low zoom: prioritize important roads and skip some labels.
            if (labelDensity < 1.0f &&
                    label.priority <= 1 &&
                    ((int) (label.length + label.x + label.y) & 1) == 0) {
                continue;
            }

            String displayName = label.name.toUpperCase(Locale.ROOT);

            float textWidth = roadLabelPaint.measureText(displayName);

            /*
             * Convert the screen-space text size to map/data-space so the
             * collision test remains correct while the canvas is scaled.
             */
            float safeScale = Math.max(0.0001f, mapScale);
            float dataTextWidth = textWidth / safeScale;
            float dataTextHeight = textSizePx / safeScale;

            /*
             * Account approximately for rotation by using the rotated
             * axis-aligned bounding box.
             */
            double radians = Math.toRadians(label.angleDegrees);
            float sin = (float) Math.abs(Math.sin(radians));
            float cos = (float) Math.abs(Math.cos(radians));

            float halfWidth =
                    (dataTextWidth * cos + dataTextHeight * sin) * 0.5f;

            float halfHeight =
                    (dataTextWidth * sin + dataTextHeight * cos) * 0.5f;

            // Extra breathing room keeps labels from visually touching.
            float padding = 5f / safeScale;

            float left = label.x - halfWidth - padding;
            float top = label.y - halfHeight - padding;
            float right = label.x + halfWidth + padding;
            float bottom = label.y + halfHeight + padding;

            if (!isLabelAreaFree(left, top, right, bottom)) {
                continue;
            }

            RectF occupied = obtainOccupiedRect(drawn);
            occupied.set(left, top, right, bottom);
            occupiedLabelBounds.add(occupied);

            canvas.save();

            canvas.rotate(
                    label.angleDegrees,
                    label.x,
                    label.y
            );

            canvas.drawText(
                    displayName,
                    label.x,
                    label.y,
                    roadLabelPaint
            );

            canvas.restore();

            drawn++;
        }
    }

    private RectF obtainOccupiedRect(int index) {
        while (occupiedLabelBounds.size() <= index) {
            occupiedLabelBounds.add(new RectF());
        }

        return occupiedLabelBounds.get(index);
    }

    private boolean isLabelAreaFree(
            float left,
            float top,
            float right,
            float bottom
    ) {
        for (RectF occupied : occupiedLabelBounds) {
            if (occupied.left < right &&
                    occupied.right > left &&
                    occupied.top < bottom &&
                    occupied.bottom > top) {
                return false;
            }
        }

        return true;
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
