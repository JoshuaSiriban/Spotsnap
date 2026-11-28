package com.example.studyflow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** The last searches, kept on this phone (SharedPreferences). Newest first. */
public final class SearchHistory {

    private static final String FILE = "spotsnap_search_history";
    private static final String KEY = "recent";
    private static final int MAX = 8;

    private SearchHistory() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The saved searches, newest first. */
    public static List<String> load(Context context) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) list.add(array.getString(i));
        } catch (JSONException e) {
            // damaged data: start with an empty list
        }
        return list;
    }

    /** Saves a search at the top. The same search (ignoring upper/lower case) is not stored twice. */
    public static void add(Context context, String query) {
        if (query == null) return;
        String clean = query.trim();
        if (clean.isEmpty()) return;

        List<String> list = load(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(clean)) list.remove(i);
        }
        list.add(0, clean);
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(context, list);
    }

    public static void remove(Context context, String query) {
        List<String> list = load(context);
        list.remove(query);
        save(context, list);
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static void save(Context context, List<String> list) {
        prefs(context).edit().putString(KEY, new JSONArray(list).toString()).apply();
    }
}