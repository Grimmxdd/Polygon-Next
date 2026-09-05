package com.grimmxd.polygon;

import android.content.Context;

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

        public Sector(String id, String status, float[] points) {
            this.id = id;
            this.status = status;
            this.points = points;
        }
    }

    private final List<Sector> sectors = new ArrayList<>();

    public SectorManager(Context context) {
        loadSectors(context);
    }

    private void loadSectors(Context context) {

        try {

            InputStream input =
                    context.getAssets().open("sectors.json");

            byte[] bytes = new byte[input.available()];

            input.read(bytes);
            input.close();

            String json =
                    new String(bytes, StandardCharsets.UTF_8);

            JSONObject root =
                    new JSONObject(json);

            JSONArray sectorArray =
                    root.getJSONArray("sectors");

            for (int i = 0; i < sectorArray.length(); i++) {

                JSONObject object =
                        sectorArray.getJSONObject(i);

                String id =
                        object.getString("id");

                String status =
                        object.getString("status");

                JSONArray points =
                        object.getJSONArray("points");

                float[] coordinates =
                        new float[points.length() * 2];

                for (int p = 0; p < points.length(); p++) {

                    JSONArray point =
                            points.getJSONArray(p);

                    coordinates[p * 2] =
                            (float) point.getDouble(0);

                    coordinates[p * 2 + 1] =
                            (float) point.getDouble(1);
                }

                sectors.add(
                        new Sector(
                                id,
                                status,
                                coordinates
                        )
                );
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
