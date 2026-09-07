package com.grimmxd.polygon;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public class MainActivity extends Activity {

    private PolygonMapView polygonMapView;
    private TextView serviceStatus;
    private TextView locationValue;

    private static final int BG = Color.rgb(5, 10, 16);
    private static final int PANEL = Color.rgb(10, 20, 29);
    private static final int PANEL_SOFT = Color.rgb(12, 25, 35);
    private static final int TEXT = Color.rgb(239, 244, 248);
    private static final int MUTED = Color.rgb(150, 165, 178);
    private static final int TEAL = Color.rgb(19, 184, 181);
    private static final int RED = Color.rgb(239, 62, 70);
    private static final int YELLOW = Color.rgb(238, 191, 48);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        polygonMapView = new PolygonMapView(this);
        root.addView(polygonMapView, new FrameLayout.LayoutParams(-1, -1));

        root.addView(buildTopBar());
        root.addView(buildBottomPanel());
        root.addView(buildBottomNav());

        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(14), dp(16), dp(8));
        top.setBackgroundColor(0xE8050A10);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("POLYGON", 23, TEXT, true);
        brand.addView(logo, wrap());

        TextView subtitle = text("EL TIGRE · ESTADO ELÉCTRICO", 10, MUTED, false);
        subtitle.setLetterSpacing(.08f);
        brand.addView(subtitle, wrap());

        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView alert = text("◉", 22, YELLOW, false);
        alert.setGravity(Gravity.CENTER);
        top.addView(alert, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView menu = text("⋮", 28, TEXT, false);
        menu.setGravity(Gravity.CENTER);
        top.addView(menu, new LinearLayout.LayoutParams(dp(36), dp(44)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(78), Gravity.TOP);
        top.setLayoutParams(lp);
        return top;
    }

    private View buildBottomPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(15), dp(16), dp(15));
        panel.setBackground(round(PANEL, 26));

        TextView heading = text("ESTADO DEL SERVICIO ELÉCTRICO", 13, TEXT, true);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading, wrap());

        TextView helper = text("Reporta cómo está la electricidad en tu zona", 11, MUTED, false);
        helper.setGravity(Gravity.CENTER);
        panel.addView(helper, wrap());

        Space gap = new Space(this);
        panel.addView(gap, new LinearLayout.LayoutParams(1, dp(12)));

        LinearLayout reportRow = new LinearLayout(this);
        reportRow.setOrientation(LinearLayout.HORIZONTAL);
        reportRow.setGravity(Gravity.CENTER);

        TextView yes = reportButton("⚡\nTENGO LUZ", TEAL);
        TextView no = reportButton("ϟ̸\nNO TENGO LUZ", RED);

        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(0, dp(74), 1f);
        buttonLp.setMargins(0, 0, dp(7), 0);
        reportRow.addView(yes, buttonLp);

        LinearLayout.LayoutParams buttonLp2 = new LinearLayout.LayoutParams(0, dp(74), 1f);
        buttonLp2.setMargins(dp(7), 0, 0, 0);
        reportRow.addView(no, buttonLp2);

        panel.addView(reportRow);

        Space gap2 = new Space(this);
        panel.addView(gap2, new LinearLayout.LayoutParams(1, dp(10)));

        LinearLayout location = new LinearLayout(this);
        location.setGravity(Gravity.CENTER_VERTICAL);
        location.setPadding(dp(13), dp(7), dp(13), dp(7));
        location.setBackground(round(PANEL_SOFT, 18));

        TextView pin = text("●", 18, TEAL, true);
        location.addView(pin, new LinearLayout.LayoutParams(dp(30), -1));

        LinearLayout locText = new LinearLayout(this);
        locText.setOrientation(LinearLayout.VERTICAL);
        TextView detected = text("SECTOR DETECTADO", 9, MUTED, false);
        locationValue = text("Ubicación actual", 13, TEXT, true);
        locText.addView(detected, wrap());
        locText.addView(locationValue, wrap());
        location.addView(locText, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text("›", 26, MUTED, false);
        location.addView(arrow, new LinearLayout.LayoutParams(dp(24), -1));
        panel.addView(location, new LinearLayout.LayoutParams(-1, dp(54)));

        serviceStatus = text("●  Sin reporte todavía", 10, MUTED, false);
        serviceStatus.setGravity(Gravity.CENTER);
        panel.addView(serviceStatus, wrap());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(10), 0, dp(10), dp(76));
        panel.setLayoutParams(lp);
        return panel;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setBackgroundColor(0xF5070D14);

        nav.addView(navItem("⌂", "INICIO", true), weight());
        nav.addView(navItem("⌖", "MAPA", false), weight());
        nav.addView(navItem("+", "REPORTAR", false), weight());
        nav.addView(navItem("▥", "ESTADÍSTICAS", false), weight());
        nav.addView(navItem("○", "PERFIL", false), weight());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(68), Gravity.BOTTOM);
        nav.setLayoutParams(lp);
        return nav;
    }

    private TextView navItem(String icon, String label, boolean active) {
        TextView v = text(icon + "\n" + label, active ? 13 : 11, active ? TEAL : MUTED, active);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(3), 0, dp(2));
        v.setLineSpacing(0, .9f);
        return v;
    }

    private TextView reportButton(String label, int color) {
        TextView v = text(label, 13, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, .95f);
        v.setBackground(round(color, 20));
        v.setOnClickListener(view -> {
            serviceStatus.setText("●  Reporte registrado · hace unos segundos");
            serviceStatus.setTextColor(color);
        });
        return v;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
