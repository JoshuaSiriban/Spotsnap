package com.example.studyflow;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Album: all of the user's own places (public and private). Each place is one group with
 * its pictures in a row, a button that opens the post and a button that shows it on the map.
 */
public class AlbumFragment extends Fragment {

    private static final int THUMB_SIDE = 320;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService worker;

    public AlbumFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_album, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        worker = Executors.newSingleThreadExecutor();

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        TextView status = view.findViewById(R.id.album_status);
        LinearLayout container = view.findViewById(R.id.album_container);

        SpotRepository.loadMySpots(requireContext(), spots -> {
            if (getView() == null) return;
            if (spots.isEmpty()) {
                status.setText("You have not pinned any places yet. Tap + on the map to add your first one!");
                return;
            }
            int pictures = 0;
            for (Spot spot : spots) pictures += spot.getPhotoCount();
            status.setText(spots.size() + (spots.size() == 1 ? " place" : " places") + "  ·  "
                    + pictures + (pictures == 1 ? " picture" : " pictures"));
            for (Spot spot : spots) container.addView(createGroup(container, spot));
        }, message -> {
            if (getView() == null) return;
            status.setText(message + " Check your internet connection and try again.");
        });
    }

    private View createGroup(ViewGroup parent, Spot spot) {
        View group = LayoutInflater.from(requireContext()).inflate(R.layout.item_album_group, parent, false);

        int count = Math.max(spot.getPhotoCount(), spot.images == null ? 0 : spot.images.size());
        ((TextView) group.findViewById(R.id.album_title)).setText(String.format(Locale.US,
                "%s · %d %s · %s", spot.getName().toUpperCase(Locale.ROOT), count,
                count == 1 ? "PHOTO" : "PHOTOS", spot.isPublic() ? "PUBLIC" : "PRIVATE"));

        group.findViewById(R.id.album_open).setOnClickListener(v ->
                PlaceDetailsFragment.open(requireActivity(), spot));
        group.findViewById(R.id.album_map).setOnClickListener(v ->
                ((MainActivity) requireActivity()).focusOnSpot(spot));

        LinearLayout thumbs = group.findViewById(R.id.album_thumbs);
        TextView thumbStatus = group.findViewById(R.id.album_thumb_status);
        loadThumbs(spot, thumbs, thumbStatus);
        return group;
    }

    /** Loads the pictures of one place and shows them as small squares. */
    private void loadThumbs(Spot spot, LinearLayout thumbs, TextView thumbStatus) {
        if (spot.getId() == null || spot.getPhotoCount() == 0) {
            showBitmaps(thumbs, thumbStatus, firstPictureOnly(spot));
            return;
        }
        SpotRepository.loadPhotos(spot, loaded -> {
            if (worker == null || worker.isShutdown()) return;
            worker.execute(() -> {
                ArrayList<Bitmap> bitmaps = new ArrayList<>();
                for (Photo photo : loaded) {
                    if (photo.bytes == null) continue;
                    Bitmap b = ImageUtils.decode(ImageUtils.makeThumbnail(photo.bytes, THUMB_SIDE));
                    if (b != null) bitmaps.add(b);
                }
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    showBitmaps(thumbs, thumbStatus, bitmaps.isEmpty() ? firstPictureOnly(spot) : bitmaps);
                });
            });
        }, message -> {
            if (!isAdded()) return;
            thumbStatus.setText("Could not load the photos");
        });
    }

    private ArrayList<Bitmap> firstPictureOnly(Spot spot) {
        ArrayList<Bitmap> list = new ArrayList<>();
        Bitmap preview = spot.getThumbnail() != null ? ImageUtils.decode(spot.getThumbnail()) : null;
        if (preview != null) list.add(preview);
        return list;
    }

    private void showBitmaps(LinearLayout thumbs, TextView thumbStatus, ArrayList<Bitmap> bitmaps) {
        if (bitmaps.isEmpty()) {
            thumbStatus.setText("No photos");
            return;
        }
        thumbStatus.setVisibility(View.GONE);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(100 * density);
        int gap = Math.round(6 * density);
        for (Bitmap bitmap : bitmaps) {
            ImageView image = new ImageView(requireContext());
            image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            ((LinearLayout.LayoutParams) image.getLayoutParams()).setMarginEnd(gap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundResource(R.drawable.bg_photo_round);
            image.setClipToOutline(true);
            image.setImageBitmap(bitmap);
            thumbs.addView(image);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (worker != null) worker.shutdownNow();
        worker = null;
    }
}