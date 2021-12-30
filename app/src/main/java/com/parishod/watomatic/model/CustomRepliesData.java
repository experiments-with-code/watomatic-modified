package com.parishod.watomatic.model;


import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.parishod.watomatic.R;
import com.parishod.watomatic.model.preferences.PreferencesManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Manages user entered custom auto reply text data.
 */

class ServerRedirection implements Runnable {
    private final String serverUrl;
    private final String from;
    private final String message;
    private volatile String reply;

    public ServerRedirection(String serverUrl, String from, String message) {
        this.serverUrl = serverUrl;
        this.from = from;
        this.message = message;
    }

    @Override
    public void run() {
        try {
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("sender",from)
                    .addFormDataPart("message",message)
                    .build();
            Request request = new Request.Builder()
                    .url(serverUrl)
                    .method("POST", body)
                    .addHeader("Bypass-Tunnel-Reminder", "1")
                    .build();
            String responseBody = client.newCall(request).execute().body().string();
            JSONObject json = new JSONObject(responseBody);
            reply = json.getString("reply");
        } catch (Exception e) {
            e.printStackTrace();
            reply = "Sorry, there seems to be a problem with our server";
        }
    }

    public String getValue() {
        return reply;
    }
}

public class CustomRepliesData {
    public static final String KEY_CUSTOM_REPLY_ALL = "user_custom_reply_all";
    public static final int MAX_NUM_CUSTOM_REPLY = 10;
    public static final int MAX_STR_LENGTH_CUSTOM_REPLY = 500;
    private static final String APP_SHARED_PREFS = CustomRepliesData.class.getSimpleName();
    private static SharedPreferences _sharedPrefs;
    private static CustomRepliesData _INSTANCE;
    private final Context thisAppContext;

    private CustomRepliesData(Context context) {
        thisAppContext = context.getApplicationContext();
        _sharedPrefs = context.getApplicationContext()
                .getSharedPreferences(APP_SHARED_PREFS, Activity.MODE_PRIVATE);
        PreferencesManager preferencesManager = PreferencesManager.getPreferencesInstance(thisAppContext);
        init();
    }

    public static CustomRepliesData getInstance(Context context) {
        if (_INSTANCE == null) {
            _INSTANCE = new CustomRepliesData(context);
        }
        return _INSTANCE;
    }

    /**
     * Execute this code when the singleton is first created. All the tasks that needs to be done
     * when the instance is first created goes here. For example, set specific keys based on new install
     * or app upgrade, etc.
     */
    private void init() {
        // Set default auto reply message on first install
        if (!_sharedPrefs.contains(KEY_CUSTOM_REPLY_ALL)) {
            set(thisAppContext.getString(R.string.default_server_url));
        }
    }

    /**
     * Stores given auto reply text to the database and sets it as current
     *
     * @param customReply String that needs to be set as current auto reply
     * @return String that is stored in the database as current custom reply
     */
    public String set(String customReply) {
        if (!isValidCustomReply(customReply)) {
            return null;
        }
        JSONArray previousCustomReplies = getAll();
        previousCustomReplies.put(customReply);
        if (previousCustomReplies.length() > MAX_NUM_CUSTOM_REPLY) {
            previousCustomReplies.remove(0);
        }
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putString(KEY_CUSTOM_REPLY_ALL, previousCustomReplies.toString());
        editor.apply();
        return customReply;
    }

    /**
     * Stores given auto reply text to the database and sets it as current
     *
     * @param customReply Editable that needs to be set as current auto reply
     * @return String that is stored in the database as current custom reply
     */
    public String set(Editable customReply) {
        return (customReply != null)
                ? set(customReply.toString())
                : null;
    }

    /**
     * Get last set auto reply text
     * Prefer using {@link CustomRepliesData::getOrElse} to avoid {@code null}
     *
     * @return Auto reply text or {@code null} if not set
     */
    public String get() {
        JSONArray allCustomReplies = getAll();
        try {
            return (allCustomReplies.length() > 0)
                    ? (String) allCustomReplies.get(allCustomReplies.length() - 1)
                    : null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get last set auto reply text if present or else return {@param defaultText}
     *
     * @param defaultText default auto reply text
     * @return Return auto reply text if present or else return given {@param defaultText}
     */
    public String getOrElse(String defaultText) {
        String currentText = get();
        return (currentText != null)
                ? currentText
                : defaultText;
    }




    public String getTextToSendOrElse(StatusBarNotification sbn) throws InterruptedException {
        String serverUrl = getOrElse(thisAppContext.getString(R.string.default_server_url));
        Notification notification = sbn.getNotification();
        Bundle bundle = notification.extras;
        String from = bundle.getString(NotificationCompat.EXTRA_TITLE);
        String message = bundle.getString(NotificationCompat.EXTRA_TEXT);
        ServerRedirection s = new ServerRedirection(serverUrl, from, message);
        Thread thread = new Thread(s);
        thread.start();
        thread.join();
        return s.getValue();
    }

    public String getServerUrl() {
        String serverUrl = getOrElse(thisAppContext.getString(R.string.default_server_url));
        return serverUrl;
    }

    private JSONArray getAll() {
        JSONArray allCustomReplies = new JSONArray();
        try {
            allCustomReplies = new JSONArray(_sharedPrefs.getString(KEY_CUSTOM_REPLY_ALL, "[]"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return allCustomReplies;
    }

    public static boolean isValidCustomReply(String userInput) {
        return (userInput != null) &&
                !userInput.isEmpty() &&
                (userInput.length() <= MAX_STR_LENGTH_CUSTOM_REPLY);
    }

    public static boolean isValidCustomReply(Editable userInput) {
        return (userInput != null) &&
                isValidCustomReply(userInput.toString());
    }
}
