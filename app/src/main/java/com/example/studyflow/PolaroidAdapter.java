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

public class PolaroidAdapter extends RecyclerView.Adapter<PolaroidAdapter.VH> {

    private ArrayList<Photo> photos;

    public PolaroidAdapter(ArrayList<Photo> photos)
    {
        this.photos = photos;
    }

    /** Replaces the photos, for example after they finished loading from Firestore. */
    public void setPhotos(ArrayList<Photo> photos)
    {
        this.photos = photos;
        notifyDataSetChanged();
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

        if (photo.bytes != null) {
            // A photo taken by a user
            h.photo.setImageBitmap(ImageUtils.decode(photo.bytes));
        } else {
            // A sample photo bundled with the app (loaded smaller so it uses less memory)
            Bitmap bitmap = ImageUtils.decodeResource(h.photo.getResources(), photo.image, 600);
            h.photo.setImageBitmap(bitmap);
        }

        h.caption.setText(photo.caption);
        h.photo.setContentDescription(photo.caption == null || photo.caption.isEmpty() ? "Photo" : photo.caption);

        h.itemView.setRotation(position % 2 == 0 ? -2f : 2f);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView caption;

        VH(@NonNull View v) {
            super(v);
            photo = v.findViewById(R.id.photo);
            caption = v.findViewById(R.id.caption);
        }
    }
}