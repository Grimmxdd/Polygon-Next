package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Named display zones for El Tigre.
 *
 * The source city map already has a precise H-sector grid. Rather than replacing
 * it, this manager assigns every H-sector to the nearest verified locality seed.
 * That gives POLYGON large, human-readable areas while preserving the original
 * grid for precise reporting and future exact boundary upgrades.
 */
public class ZoneManager {

    public static class Zone {
        public final String id;
        public final String name;
        public final float lat;
        public final float lon;
        public final float x;
        public final float y;
        public final int sourceOsmNode;
        public final String geometryConfidence;

        public Zone(String id, String name, float lat, float lon,
                    float x, float y, int sourceOsmNode,
                    String geometryConfidence) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.x = x;
            this.y = y;
            this.sourceOsmNode = sourceOsmNode;
            this.geometryConfidence = geometryConfidence;
        }
    }

    private static final float MIN_LAT = 8.8304f;
    private static final float MIN_LON = -64.2972f;
    private static final float MAX_LAT = 8.9329f;
    private static final float MAX_LON = -64.2026f;
    private static final float WIDTH = 1400f;
    private static final float HEIGHT = 1400f;

    private final List<Zone> zones = new ArrayList<>();
    private final Map<String, Zone> sectorAssignments = new HashMap<>();

    public ZoneManager(Context context, List<SectorManager.Sector> sectors) {
        loadZones(context);
        assignSectors(sectors);
    }

    private void loadZones(Context context) {
        try {
            InputStream input = context.getAssets().open("zones.json");
            byte[] bytes = new byte[input.available()];
            int offset = 0;
            int read;
            while (offset < bytes.length &&
                    (read = input.read(bytes, offset, bytes.length - offset)) > 0) {
                offset += read;
            }
            input.close();

            JSONObject root = new JSONObject(
                    new String(bytes, StandardCharsets.UTF_8)
            );
            JSONArray array = root.getJSONArray("zones");

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.getString("id");
                String name = obj.getString("name");
                float lat = (float) obj.getDouble("lat");
                float lon = (float) obj.getDouble("lon");
                int osmNode = obj.optInt("sourceOsmNode", -1);
                String confidence = obj.optString("geometryConfidence", "medium");

                zones.add(new Zone(
                        id,
                        name,
                        lat,
                        lon,
                        projectX(lon),
                        projectY(lat),
                        osmNode,
                        confidence
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void assignSectors(List<SectorManager.Sector> sectors) {
        sectorAssignments.clear();

        for (SectorManager.Sector sector : sectors) {
            if (zones.isEmpty() || sector.bounds.width() <= 0f || sector.bounds.height() <= 0f) {
                continue;
            }

            float centerX = (sector.bounds.left + sector.bounds.right) * 0.5f;
            float centerY = (sector.bounds.top + sector.bounds.bottom) * 0.5f;

            Zone nearest = null;
            float bestDistance = Float.MAX_VALUE;

            for (Zone zone : zones) {
                float dx = centerX - zone.x;
                float dy = centerY - zone.y;
                float distance = dx * dx + dy * dy;

                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = zone;
                }
            }

            if (nearest != null) {
                sectorAssignments.put(sector.id, nearest);
            }
        }
    }

    public List<Zone> getZones() {
        return zones;
    }

    public Zone getZoneForSector(String sectorId) {
        return sectorAssignments.get(sectorId);
    }

    public int getAssignedSectorCount() {
        return sectorAssignments.size();
    }

    public static float projectX(float lon) {
        return ((lon - MIN_LON) / (MAX_LON - MIN_LON)) * WIDTH;
    }

    public static float projectY(float lat) {
        return ((MAX_LAT - lat) / (MAX_LAT - MIN_LAT)) * HEIGHT;
    }
}
