package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Activity Feed: the most recent public posts from other people, newest first.
 * "Everyone" shows all of them, "Following" only the people you follow.
 */
public class ActivityFeedFragment extends Fragment {

    private static final int MAX_POSTS = 50;

    private final ArrayList<Spot> posts = new ArrayList<>();   // public posts by others, newest first
    private Set<String> followingIds;                          // null until loaded (or when it failed)
    private boolean postsLoaded;
    private boolean followingFailed;
    private String error;
    private FeedAdapter adapter;

    public ActivityFeedFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_activity_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new FeedAdapter(new FeedAdapter.Listener() {
            @Override
            public void onOpen(Spot spot) {
                PlaceDetailsFragment.open(requireActivity(), spot);
            }

            @Override
            public void onShowOnMap(Spot spot) {
                ((MainActivity) requireActivity()).focusOnSpot(spot);
            }
        });
        RecyclerView list = view.findViewById(R.id.feed_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        ((ChipGroup) view.findViewById(R.id.feed_chip_group))
                .setOnCheckedStateChangeListener((group, ids) -> render());

        // Loaded again each time the screen opens, so it always shows the newest posts
        SpotRepository.loadPublicSpots(requireContext(), spots -> {
            posts.clear();
            String me = UserSession.getUid();
            for (Spot spot : spots) {
                if (!me.equals(spot.getCreatorId())) posts.add(spot);   // "from others"
            }
            postsLoaded = true;
            render();
        }, message -> {
            error = message + " Check your internet connection.";
            render();
        });

        SocialRepository.loadFollowingIds(ids -> {
            followingIds = ids;
            followingFailed = ids == null;
            render();
        });
    }

    private boolean followingSelected() {
        View root = getView();
        return root != null && ((ChipGroup) root.findViewById(R.id.feed_chip_group))
                .getCheckedChipId() == R.id.feed_chip_following;
    }

    private void render() {
        View root = getView();
        if (root == null) return;
        TextView status = root.findViewById(R.id.feed_status);
        boolean following = followingSelected();

        if (error != null) {
            adapter.setSpots(new ArrayList<>());
            status.setText(error);
            status.setVisibility(View.VISIBLE);
            return;
        }
        if (!postsLoaded || (following && followingIds == null && !followingFailed)) {
            status.setText("Loading...");
            status.setVisibility(View.VISIBLE);
            return;
        }
        if (following && followingFailed) {
            adapter.setSpots(new ArrayList<>());
            status.setText("Could not load who you follow. Check your internet connection.");
            status.setVisibility(View.VISIBLE);
            return;
        }

        List<Spot> shown = new ArrayList<>();
        for (Spot spot : posts) {
            if (!following || followingIds.contains(spot.getCreatorId())) shown.add(spot);
            if (shown.size() >= MAX_POSTS) break;
        }
        adapter.setSpots(shown);

        if (shown.isEmpty()) {
            status.setText(!following
                    ? "No posts from other people yet."
                    : followingIds.isEmpty()
                      ? "You are not following anyone yet. Open a place and tap Follow."
                      : "The people you follow have not posted anything yet.");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }
}