package com.example.studyflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The posts of the Activity Feed: who pinned it and when, the picture, the note and the counts. */
public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

    public interface Listener {
        void onOpen(Spot spot);

        void onShowOnMap(Spot spot);
    }

    private final List<Spot> spots = new ArrayList<>();
    private final Listener listener;

    public FeedAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setSpots(List<Spot> newSpots) {
        spots.clear();
        spots.addAll(newSpots);
        notifyDataSetChanged();
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago", or the date for older posts. */
    public static String timeAgo(long millis) {
        if (millis <= 0) return "";
        long diff = Math.max(0, System.currentTimeMillis() - millis);
        long minutes = diff / 60000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h ago";
        long days = hours / 24;
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(millis));
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Spot spot = spots.get(position);

        h.user.setText("@" + spot.getCreator() + " pinned a place");
        h.time.setText(timeAgo(spot.getCreatedAt()));
        h.photo.setImageBitmap(SpotListAdapter.previewOf(h.photo, spot)); // null keeps the placeholder
        h.name.setText(spot.getName());
        h.meta.setText(Categories.normalize(spot.getCategory()).toUpperCase(Locale.ROOT));

        String review = spot.getReview() == null ? "" : spot.getReview().trim();
        h.review.setText(review);
        h.review.setVisibility(review.isEmpty() ? View.GONE : View.VISIBLE);

        int likes = spot.getLikes();
        int saves = spot.getSaves();
        h.stats.setText(likes + (likes == 1 ? " like" : " likes") + "  ·  "
                + saves + (saves == 1 ? " save" : " saves"));

        h.itemView.setOnClickListener(v -> listener.onOpen(spot));
        h.map.setOnClickListener(v -> listener.onShowOnMap(spot));
    }

    @Override
    public int getItemCount() {
        return spots.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView user, time, name, meta, review, stats, map;
        final ImageView photo;

        VH(@NonNull View v) {
            super(v);
            user = v.findViewById(R.id.feed_user);
            time = v.findViewById(R.id.feed_time);
            photo = v.findViewById(R.id.feed_photo);
            name = v.findViewById(R.id.feed_name);
            meta = v.findViewById(R.id.feed_meta);
            review = v.findViewById(R.id.feed_review);
            stats = v.findViewById(R.id.feed_stats);
            map = v.findViewById(R.id.feed_map);
        }
    }
}