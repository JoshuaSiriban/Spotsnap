package com.example.studyflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class PolaroidAdapter extends RecyclerView.Adapter<PolaroidAdapter.VH> {

    private ArrayList<Photo> photos;

    public PolaroidAdapter(ArrayList<Photo> photos)
    {
        this.photos = photos;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_polaroid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Photo photo = photos.get(position);
        h.photo.setBackgroundResource(photo.image);
        h.caption.setText(photo.caption);

        h.itemView.setRotation(position % 2 == 0 ? -2f : 2f);
    }

    @Override
    public int getItemCount() {
        return photos.size();
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
