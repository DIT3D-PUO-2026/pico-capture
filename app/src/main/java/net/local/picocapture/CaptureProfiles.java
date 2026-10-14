package net.local.picocapture;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Local settings only: session tokens and root state never belong in a profile. */
final class CaptureProfiles {
    private static final String[] KEYS = {"width", "height", "fps", "bitrate", "port", "encoder", "mime",
        "record", "stream", "rtspPort", "udpDestination", "browserCompatibility", "audio", "nativeCadence",
        "advanced", "upscale", "sgsrThreshold", "sourceScale", "sourceWidth", "sourceHeight", "sharpness"};
    private final SharedPreferences preferences;
    CaptureProfiles(Context context) { this(context.getSharedPreferences("capture_profiles", Context.MODE_PRIVATE)); }
    CaptureProfiles(SharedPreferences preferences) { this.preferences = preferences; }

    static JSONObject settingsOnly(JSONObject config) throws JSONException {
        JSONObject result = new JSONObject();
        for (String key : KEYS) if (config.has(key)) result.put(key, config.get(key));
        return new JSONObject(result.toString());
    }
    JSONObject all() throws JSONException { return new JSONObject(preferences.getString("entries", "{}")); }
    List<String> names() throws JSONException {
        ArrayList<String> names = new ArrayList<>();
        Iterator<String> keys = all().keys();
        while (keys.hasNext()) names.add(keys.next());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    JSONObject load(String name) throws JSONException { return settingsOnly(all().getJSONObject(name)); }
    void save(String name, JSONObject config) throws Exception {
        name = name.trim();
        if (name.isEmpty() || name.length() > 48) throw new IllegalArgumentException("Use a profile name of 1–48 characters.");
        JSONObject entries = all();
        if (!entries.has(name) && entries.length() >= 20) throw new IllegalArgumentException("Up to 20 profiles can be saved. Delete one first.");
        entries.put(name, settingsOnly(config));
        if (!preferences.edit().putString("entries", entries.toString()).commit()) throw new java.io.IOException("Could not save profile.");
    }
    void delete(String name) throws Exception {
        JSONObject entries = all(); entries.remove(name);
        if (!preferences.edit().putString("entries", entries.toString()).commit()) throw new java.io.IOException("Could not delete profile.");
    }
    static JSONObject starter(int index, String encoder) throws JSONException {
        boolean browser = index == 1, recording = index == 2;
        return new JSONObject().put("width", browser ? 1280 : 1920).put("height", browser ? 720 : 1080)
            .put("fps", 60).put("bitrate", (browser ? 8 : recording ? 24 : 12) * 1000000)
            .put("encoder", encoder).put("mime", "video/avc").put("record", recording).put("stream", !recording)
            .put("port", 8088).put("rtspPort", 8554).put("udpDestination", "").put("browserCompatibility", true)
            .put("audio", false).put("nativeCadence", true).put("advanced", new JSONObject())
            .put("upscale", "off").put("sgsrThreshold", 8).put("sourceScale", 2.0 / 3.0)
            .put("sourceWidth", browser ? 1280 : 1920).put("sourceHeight", browser ? 720 : 1080).put("sharpness", 35);
    }
}
