package com.umniah.vpn;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class VpnConfigManager {

    private static final String TAG = "UmniahVPN-Config";

    private static final String CONFIG_URL =
            "https://raw.githubusercontent.com/fadixd1/UmniahVPN/main/config/vpn.json";

    private static final String PREFS = "umniah_vpn_config";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_UPDATED = "updated_at";

    private VpnConfigManager() {}

    public static boolean update(Context context) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(CONFIG_URL);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();

            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub HTTP=" + code);
                return false;
            }

            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );

            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            reader.close();
            input.close();

            JSONObject config = new JSONObject(body.toString());

            if (!config.has("version")) {
                Log.w(TAG, "Invalid config: missing version");
                return false;
            }

            String updatedAt = config.optString("updated_at", "");

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CONFIG, config.toString())
                    .putString(KEY_UPDATED, updatedAt)
                    .apply();

            Log.i(TAG, "GitHub configuration updated: " + updatedAt);
            return true;

        } catch (Throwable e) {
            Log.e(TAG, "GitHub update failed", e);
            return false;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String getConfig(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CONFIG, "{}");
    }

    public static String getUpdatedAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_UPDATED, "");
    }

    public static boolean hasConfig(Context context) {
        String value = getConfig(context);
        return value != null && !value.equals("{}") && !value.isEmpty();
    }
}
