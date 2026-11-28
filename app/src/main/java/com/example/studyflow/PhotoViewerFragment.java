package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;

/** Full screen photo viewer: swipe between the photos of a spot, pinch to zoom. */
public class PhotoViewerFragment extends Fragment {

    private ArrayList<Photo> photos;
    private int startIndex;

    public PhotoViewerFragment() {
    }

    /** Opens the viewer on top of the current screen, starting at the given photo. */
    public static void open(FragmentActivity activity, ArrayList<Photo> photos, int startIndex) {
        if (photos == null || photos.isEmpty()) return;
        PhotoViewerFragment fragment = new PhotoViewerFragment();
        fragment.photos = new ArrayList<>(photos);
        fragment.startIndex = Math.max(0, Math.min(startIndex, photos.size() - 1));
        DrawerMenu.open(activity, fragment);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_photo_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_close).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        if (photos == null || photos.isEmpty()) {
            // the screen was recreated by Android and the photos are gone: just go back
            view.post(() -> {
                if (isAdded()) requireActivity().getOnBackPressedDispatcher().onBackPressed();
            });
            return;
        }

        ViewPager2 pager = view.findViewById(R.id.photo_pager);
        pager.setAdapter(new PhotoPagerAdapter(photos));
        pager.setOffscreenPageLimit(1);
        pager.setCurrentItem(startIndex, false);
        showPosition(view, startIndex);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPosition(view, position);
            }
        });
    }

    /** "2 / 4" at the top and the photo's caption at the bottom. */
    private void showPosition(View root, int position) {
        ((TextView) root.findViewById(R.id.txt_counter))
                .setText(photos.size() == 1 ? "" : (position + 1) + " / " + photos.size());

        TextView caption = root.findViewById(R.id.txt_caption);
        String text = photos.get(position).caption;
        if (text == null || text.isEmpty()) {
            caption.setVisibility(View.GONE);
        } else {
            caption.setText(text);
            caption.setVisibility(View.VISIBLE);
        }
    }
}