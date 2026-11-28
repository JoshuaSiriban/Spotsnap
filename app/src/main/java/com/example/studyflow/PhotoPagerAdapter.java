package com.example.studyflow;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/** One full screen, zoomable picture per page of the photo viewer. */
public class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.VH> {

    private final ArrayList<Photo> photos;

    public PhotoPagerAdapter(ArrayList<Photo> photos) {
        this.photos = photos;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_page, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Photo photo = photos.get(position);
        Bitmap bitmap;
        if (photo.bytes != null) {
            bitmap = ImageUtils.decode(photo.bytes);                                   // a user's photo
        } else {
            bitmap = ImageUtils.decodeResource(h.image.getResources(), photo.image, 1600);  // a sample photo
        }
        h.image.setImageBitmap(bitmap);
        h.image.setContentDescription(photo.caption == null || photo.caption.isEmpty() ? "Photo" : photo.caption);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ZoomImageView image;

        VH(@NonNull View v) {
            super(v);
            image = (ZoomImageView) v;
        }
    }
}