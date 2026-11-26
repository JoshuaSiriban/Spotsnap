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

/** The row of "nearby spot" cards shown at the bottom of the map. */
public class NearbyAdapter extends RecyclerView.Adapter<NearbyAdapter.VH> {

    /** A spot together with how far it is from the user. */
    public static class Item {
        final Spot spot;
        final float meters;

        public Item(Spot spot, float meters) {
            this.spot = spot;
            this.meters = meters;
        }
    }

    public interface OnSpotClick {
        void onClick(Spot spot);
    }

    private final List<Item> items = new ArrayList<>();
    private final OnSpotClick listener;

    public NearbyAdapter(OnSpotClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** "350 m" below one kilometre, "2.4 km" above. */
    public static String formatDistance(float meters) {
        if (meters < 1000) return String.format(Locale.US, "%d m", Math.round(meters / 10f) * 10);
        return String.format(Locale.US, "%.1f km", meters / 1000f);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item item = items.get(position);
        Spot spot = item.spot;

        h.name.setText(spot.getName());
        h.meta.setText(spot.getCategory().toUpperCase(Locale.ROOT) + "  ·  " + AppSettings.formatDistance(h.itemView.getContext(), item.meters));
        h.photo.setImageBitmap(previewOf(h.photo, spot)); // null keeps the grey placeholder
        h.itemView.setOnClickListener(v -> listener.onClick(spot));
    }

    /** The small thumbnail saved with the spot, or the first sample picture of an old spot. */
    private Bitmap previewOf(ImageView view, Spot spot) {
        if (spot.getThumbnail() != null) return ImageUtils.decode(spot.getThumbnail());
        if (spot.images != null && !spot.images.isEmpty() && spot.images.get(0).image != 0) {
            return ImageUtils.decodeResource(view.getResources(), spot.images.get(0).image, 300);
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;
        final TextView meta;

        VH(@NonNull View v) {
            super(v);
            photo = v.findViewById(R.id.nearby_photo);
            name = v.findViewById(R.id.nearby_name);
            meta = v.findViewById(R.id.nearby_meta);
        }
    }
}