package com.example.studyflow;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A simple list of places: picture, name, "CATEGORY · by @user" and a "Map" link. */
public class SpotListAdapter extends RecyclerView.Adapter<SpotListAdapter.VH> {

    public interface Listener {
        void onOpen(Spot spot);

        void onShowOnMap(Spot spot);
    }

    private final List<Spot> spots = new ArrayList<>();
    private final Listener listener;
    private java.util.Map<String, String> statuses;   // null = do not show a status

    public SpotListAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Optional: spot id -> "want" / "visited". Saved places then show it after the category. */
    public void setStatuses(java.util.Map<String, String> newStatuses) {
        statuses = newStatuses;
    }

    public void setSpots(List<Spot> newSpots) {
        spots.clear();
        spots.addAll(newSpots);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spot_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Spot spot = spots.get(position);
        h.name.setText(spot.getName());

        String meta = Categories.normalize(spot.getCategory()).toUpperCase(Locale.ROOT)
                + "  ·  by @" + spot.getCreator();
        if (!spot.isPublic()) meta += "  ·  PRIVATE";
        if (statuses != null && spot.getId() != null && statuses.containsKey(spot.getId())) {
            meta += SavedRepository.STATUS_VISITED.equals(statuses.get(spot.getId()))
                    ? "  ·  VISITED" : "  ·  WANT TO GO";
        }
        h.meta.setText(meta);

        h.photo.setImageBitmap(previewOf(h.photo, spot)); // null keeps the grey placeholder
        h.itemView.setOnClickListener(v -> listener.onOpen(spot));
        h.map.setOnClickListener(v -> listener.onShowOnMap(spot));
    }

    static Bitmap previewOf(ImageView view, Spot spot) {
        if (spot.getThumbnail() != null) return ImageUtils.decode(spot.getThumbnail());
        if (spot.images != null && !spot.images.isEmpty() && spot.images.get(0).image != 0) {
            return ImageUtils.decodeResource(view.getResources(), spot.images.get(0).image, 300);
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return spots.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;
        final TextView meta;
        final TextView map;

        VH(@NonNull View v) {
            super(v);
            photo = v.findViewById(R.id.row_photo);
            name = v.findViewById(R.id.row_name);
            meta = v.findViewById(R.id.row_meta);
            map = v.findViewById(R.id.row_map);
        }
    }
}