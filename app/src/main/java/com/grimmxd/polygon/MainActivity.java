package com.grimmxd.polygon;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

public class MainActivity extends Activity {

    private PolygonMapView polygonMapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        polygonMapView = new PolygonMapView(this);
        setContentView(polygonMapView);
    }
}
