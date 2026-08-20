package com.umniah.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST = 1001;

    private Button connect;
    private TextView status;
    private TextView timer;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable updateUi = new Runnable() {
        @Override
        public void run() {
            updateState();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(30, 40, 30, 30);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(4,15,42),
                        Color.rgb(28,8,61),
                        Color.rgb(2,57,72)
                }
        );
        root.setBackground(bg);

        TextView logo = new TextView(this);
        logo.setText("✦");
        logo.setTextColor(Color.rgb(25,215,255));
        logo.setTextSize(72);
        logo.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("Umniah VPN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(36);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("SECURE  •  PRIVATE  •  FAST");
        sub.setTextColor(Color.rgb(170,220,255));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 12, 0, 35);

        connect = new Button(this);
        connect.setText("اتصال");
        connect.setTextSize(20);
        connect.setAllCaps(false);
        connect.setTextColor(Color.WHITE);

        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.rgb(16,145,190));
        buttonBg.setCornerRadius(100);
        connect.setBackground(buttonBg);

        connect.setOnClickListener(v -> startVpn());

        status = new TextView(this);
        status.setText("غير متصل");
        status.setTextColor(Color.rgb(210,225,240));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 25, 0, 0);

        timer = new TextView(this);
        timer.setText("00:00:00");
        timer.setTextColor(Color.rgb(25,215,255));
        timer.setTextSize(25);
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(0, 15, 0, 0);

        TextView sign = new TextView(this);
        sign.setText("FADI - XD");
        sign.setTextColor(Color.WHITE);
        sign.setTextSize(18);
        sign.setGravity(Gravity.CENTER);
        sign.setTypeface(null, 1);
        sign.setPadding(0, 45, 0, 0);

        root.addView(logo, new LinearLayout.LayoutParams(-1,100));
        root.addView(title, new LinearLayout.LayoutParams(-1,-2));
        root.addView(sub, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1,75);
        bp.setMargins(20,0,20,0);
        root.addView(connect,bp);

        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        root.addView(timer,new LinearLayout.LayoutParams(-1,-2));
        root.addView(sign,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
    }

    private void startVpn() {
        Intent prepare = VpnService.prepare(this);

        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, UmniahVpnService.class);

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        status.setText("جاري الاتصال...");
    }

    private void disconnectVpn() {
        try {
            Intent intent = new Intent(this, UmniahVpnService.class);
            intent.setAction(UmniahVpnService.ACTION_STOP);

            // The app is in the foreground because the user pressed the button,
            // so send the explicit STOP command to the running VPN service.
            startService(intent);
        } catch (Exception e) {
            // Fallback: explicitly stop the service.
            try {
                stopService(new Intent(this, UmniahVpnService.class));
            } catch (Exception ignored) {
            }
        }

        connect.setText("اتصال");
        status.setText("جاري إيقاف VPN...");
        timer.setText("00:00:00");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                status.setText("لم يتم السماح بالـVPN");
            }
        }
    }

    private void updateState() {
        boolean running = UmniahVpnService.isRunning();
        boolean online = UmniahVpnService.isInternetValidated();

        if (!running) {
            connect.setText("اتصال");
            status.setText("غير متصل");
            timer.setText("00:00:00");

            connect.setOnClickListener(v -> startVpn());
            return;
        }

        connect.setText("إيقاف");

        if (online) {
            status.setText("متصل بالإنترنت عبر VPN");
            timer.setText(UmniahVpnService.getElapsedText());
        } else {
            status.setText("VPN متصل - لا يوجد إنترنت");
            timer.setText("00:00:00");
        }

        connect.setOnClickListener(v -> disconnectVpn());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateUi);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateUi);
    }
}
