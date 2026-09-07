package com.grimmxd.polygon;

import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SectorManager {

    /*
     * These three areas are outside the useful city footprint in the current
     * source map. Filtering them here prevents them from expanding the map's
     * bounds while keeping the original sectors.json untouched.
     *
     * Each zone is: left, top, right, bottom.
     */
    private static final float[][] EXCLUDED_SECTOR_ZONES = {
            {-130f, 520f, 90f, 800f},
            {170f, 770f, 410f, 1050f},
            {900f, 650f, 1515f, 1280f}
    };

    public static class Sector {
        public final String id;
        public final String status;
        public final float[] points;

        // Precomputed once when the sector is loaded.
        public final Path path = new Path();
        public final RectF bounds = new RectF();

        public Sector(String id, String status, float[] points) {
            this.id = id;
            this.status = status;
            this.points = points;

            if (points.length >= 4) {
                path.moveTo(points[0], points[1]);

                for (int i = 2; i < points.length; i += 2) {
                    path.lineTo(points[i], points[i + 1]);
                }

                path.close();
                path.computeBounds(bounds, true);
            }
        }
    }

    private final List<Sector> sectors = new ArrayList<>();

    public SectorManager(Context context) {
        loadSectors(context);
    }

    private void loadSectors(Context context) {
        try {
            InputStream input = context.getAssets().open("sectors.json");

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
            JSONArray sectorArray = root.getJSONArray("sectors");

            for (int i = 0; i < sectorArray.length(); i++) {
                JSONObject object = sectorArray.getJSONObject(i);

                String id = object.getString("id");
                String status = object.optString("status", "unknown");

                JSONArray points = object.getJSONArray("points");
                float[] coordinates = new float[points.length() * 2];

                for (int p = 0; p < points.length(); p++) {
                    JSONArray point = points.getJSONArray(p);

                    coordinates[p * 2] = (float) point.getDouble(0);
                    coordinates[p * 2 + 1] = (float) point.getDouble(1);
                }

                if (coordinates.length >= 6) {
                    Sector sector = new Sector(id, status, coordinates);

                    if (!isExcludedSector(sector)) {
                        sectors.add(sector);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isExcludedSector(Sector sector) {
        if (sector.bounds.width() <= 0f || sector.bounds.height() <= 0f) {
            return false;
        }

        float centerX = (sector.bounds.left + sector.bounds.right) * 0.5f;
        float centerY = (sector.bounds.top + sector.bounds.bottom) * 0.5f;

        for (float[] zone : EXCLUDED_SECTOR_ZONES) {
            if (centerX >= zone[0] &&
                    centerX <= zone[2] &&
                    centerY >= zone[1] &&
                    centerY <= zone[3]) {
                return true;
            }
        }

        return false;
    }

    public List<Sector> getSectors() {
        return sectors;
    }

    public int getSectorCount() {
        return sectors.size();
    }
}
