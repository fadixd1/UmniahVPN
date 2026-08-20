package com.umniah.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

public class UmniahVpnService extends VpnService {

    private Thread githubUpdateThread;
    private volatile boolean githubUpdateRunning = false;


    public static final String ACTION_STOP =
            "com.umniah.vpn.STOP";

    private static final String CHANNEL_ID =
            "UmniahVPN_CHANNEL";

    private static final int NOTIFICATION_ID = 2026;

    private static volatile boolean running = false;
    private static volatile boolean internetValidated = false;
    private static volatile long connectionStart = 0;

    private static ParcelFileDescriptor tun;
    private Thread monitorThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null &&
                ACTION_STOP.equals(intent.getAction())) {

            stopVpn();
            return START_NOT_STICKY;
        }

        startGithubConfigUpdates();

        createChannel();

        Notification notification =
                buildNotification("VPN قيد التشغيل");

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (running) {
            return START_NOT_STICKY;
        }

        try {
            Builder builder = new Builder();

            builder.setSession("Umniah VPN");
            builder.setMtu(1500);

            /*
             * TUN interface.
             * The application deliberately does not fake Internet
             * availability: the timer only starts after Android
             * reports a validated Internet connection.
             */
            builder.addAddress("10.8.0.2", 32);
            builder.addRoute("0.0.0.0", 0);

            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {
            }

            tun = builder.establish();

            if (tun == null) {
                stopVpn();
                return START_NOT_STICKY;
            }

            running = true;
            connectionStart = 0;
            internetValidated = false;

            startMonitor();

        } catch (Throwable e) {
            stopVpn();
        }

        return START_NOT_STICKY;
    }

    private void startGithubConfigUpdates() {

        if (githubUpdateRunning) {
            return;
        }

        githubUpdateRunning = true;

        githubUpdateThread = new Thread(() -> {

            while (githubUpdateRunning &&
                    running &&
                    !Thread.currentThread().isInterrupted()) {

                try {
                    boolean updated = VpnConfigManager.update(this);

                    android.util.Log.i(
                            "UmniahVPN-Config",
                            "GITHUB_UPDATE=" + updated +
                            " UPDATED_AT=" +
                            VpnConfigManager.getUpdatedAt(this)
                    );

                    Thread.sleep(15 * 60 * 1000L);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;

                } catch (Throwable e) {
                    android.util.Log.e(
                            "UmniahVPN-Config",
                            "UPDATE_THREAD_ERROR",
                            e
                    );

                    try {
                        Thread.sleep(60 * 1000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            githubUpdateRunning = false;
        });

        githubUpdateThread.start();
    }

    private void startMonitor() {

        if (monitorThread != null &&
                monitorThread.isAlive()) {
            return;
        }

        monitorThread = new Thread(() -> {

            while (running &&
                    !Thread.currentThread().isInterrupted()) {

                boolean valid = checkInternet();

                if (valid && connectionStart == 0) {
                    connectionStart =
                            System.currentTimeMillis();
                }

                if (!valid) {
                    connectionStart = 0;
                }

                internetValidated = valid;

                updateNotification(valid);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        monitorThread.start();
    }

    private boolean checkInternet() {

        try {
            ConnectivityManager cm =
                    (ConnectivityManager)
                            getSystemService(CONNECTIVITY_SERVICE);

            if (cm == null) {
                return false;
            }

            Network network = cm.getActiveNetwork();

            if (network == null) {
                return false;
            }

            NetworkCapabilities caps =
                    cm.getNetworkCapabilities(network);

            if (caps == null) {
                return false;
            }

            boolean validated =
                    caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    );

            boolean hasInternet =
                    caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                    );

            return validated && hasInternet;

        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateNotification(boolean online) {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);

        if (manager == null) {
            return;
        }

        String text = online
                ? "VPN متصل • الإنترنت متاح"
                : "VPN متصل • لا يوجد إنترنت";

        manager.notify(
                NOTIFICATION_ID,
                buildNotification(text)
        );
    }

    private Notification buildNotification(String text) {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder =
                    new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder =
                    new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Umniah VPN")
                .setContentText(text)
                .setSmallIcon(
                        android.R.drawable.stat_sys_warning
                )
                .setOngoing(true)
                .setCategory(
                        Notification.CATEGORY_SERVICE
                )
                .build();
    }

    private void createChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(NOTIFICATION_SERVICE);

            if (manager != null) {

                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "Umniah VPN",
                                NotificationManager.IMPORTANCE_LOW
                        );

                channel.setDescription(
                        "إشعار اتصال Umniah VPN"
                );

                manager.createNotificationChannel(channel);
            }
        }
    }

    private void stopVpn() {

        running = false;
        internetValidated = false;
        connectionStart = 0;

        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }

        githubUpdateRunning = false;

        if (githubUpdateThread != null) {
            githubUpdateThread.interrupt();
            githubUpdateThread = null;
        }

        if (tun != null) {
            try {
                tun.close();
            } catch (Exception ignored) {
            }

            tun = null;
        }

        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }

        stopSelf();
    }

    @Override
    public void onDestroy() {
        // onDestroy is the final cleanup path when MainActivity
        // calls stopService().
        stopVpn();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isInternetValidated() {
        return running && internetValidated;
    }

    public static String getElapsedText() {

        if (!isInternetValidated() ||
                connectionStart == 0) {
            return "00:00:00";
        }

        long seconds =
                (System.currentTimeMillis() -
                        connectionStart) / 1000;

        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        return String.format(
                java.util.Locale.US,
                "%02d:%02d:%02d",
                h, m, s
        );
    }
}
