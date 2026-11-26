package com.example.studyflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** A grid of picture thumbnails, one per place (used for "Recent posts" on the Profile). */
public class ThumbGridAdapter extends RecyclerView.Adapter<ThumbGridAdapter.VH> {

    public interface OnSpotClick {
        void onClick(Spot spot);
    }

    private final List<Spot> spots = new ArrayList<>();
    private final OnSpotClick listener;

    public ThumbGridAdapter(OnSpotClick listener) {
        this.listener = listener;
    }

    public void setSpots(List<Spot> newSpots) {
        spots.clear();
        spots.addAll(newSpots);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_thumb_grid, parent, false);
        return new VH((ImageView) v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Spot spot = spots.get(position);
        h.image.setImageBitmap(SpotListAdapter.previewOf(h.image, spot));
        h.image.setContentDescription(spot.getName());
        h.image.setOnClickListener(v -> listener.onClick(spot));
    }

    @Override
    public int getItemCount() {
        return spots.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;

        VH(@NonNull ImageView v) {
            super(v);
            image = v;
        }
    }
}