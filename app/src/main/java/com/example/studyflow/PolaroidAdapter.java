package com.example.studyflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Dummy photo cards: colored blocks stand in for real pictures. */
public class PolaroidAdapter extends RecyclerView.Adapter<PolaroidAdapter.VH> {

    private final String[] captions = {"Iced coffee, 2 pm", "The corner table", "Rainy day view", "Cake of the day"};
    private final int[] colors = {0xFFE7C9A9, 0xFFCFE6C4, 0xFFC7C2E3, 0xFFD9B48C};

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_polaroid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.photo.setBackgroundColor(colors[position % colors.length]);
        h.caption.setText(captions[position % captions.length]);
        h.itemView.setRotation(position % 2 == 0 ? -2f : 2f);
    }

    @Override
    public int getItemCount() {
        return captions.length;
    }

    static class VH extends RecyclerView.ViewHolder {
        final View photo;
        final TextView caption;

        VH(@NonNull View v) {
            super(v);
            photo = v.findViewById(R.id.photo);
            caption = v.findViewById(R.id.caption);
        }
    }
}