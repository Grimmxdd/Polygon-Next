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
                    sectors.add(new Sector(id, status, coordinates));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Sector> getSectors() {
        return sectors;
    }

    public int getSectorCount() {
        return sectors.size();
    }
}
