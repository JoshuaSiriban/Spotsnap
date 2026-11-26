package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The profile: username, number of shared places and uploaded pictures, saved places,
 * places recommended for you and the most recent posts. Everything is read from Firestore.
 */
public class ProfileFragment extends Fragment {

    private static final int RECENT_POSTS = 6;
    private static final int SAVED_PREVIEW = 3;
    private static final int RECOMMENDED = 5;

    private SpotListAdapter savedAdapter;
    private SpotListAdapter recommendedAdapter;
    private ThumbGridAdapter recentAdapter;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        showNames(view);
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v ->
                AccountDialogs.editName(requireActivity(), () -> {
                    if (getView() != null) showNames(getView());
                }));
        view.findViewById(R.id.link_album).setOnClickListener(v ->
                DrawerMenu.open(requireActivity(), new AlbumFragment()));
        view.findViewById(R.id.link_saved).setOnClickListener(v ->
                DrawerMenu.open(requireActivity(), new SavedLocationsFragment()));

        SpotListAdapter.Listener listener = new SpotListAdapter.Listener() {
            @Override
            public void onOpen(Spot spot) {
                PlaceDetailsFragment.open(requireActivity(), spot);
            }

            @Override
            public void onShowOnMap(Spot spot) {
                ((MainActivity) requireActivity()).focusOnSpot(spot);
            }
        };

        recentAdapter = new ThumbGridAdapter(spot -> PlaceDetailsFragment.open(requireActivity(), spot));
        RecyclerView recent = view.findViewById(R.id.rv_recent);
        recent.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        recent.setAdapter(recentAdapter);

        savedAdapter = new SpotListAdapter(listener);
        RecyclerView savedList = view.findViewById(R.id.rv_saved);
        savedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        savedList.setAdapter(savedAdapter);

        recommendedAdapter = new SpotListAdapter(listener);
        RecyclerView recommended = view.findViewById(R.id.rv_recommended);
        recommended.setLayoutManager(new LinearLayoutManager(requireContext()));
        recommended.setAdapter(recommendedAdapter);

        loadMyPosts(view);
        loadFollowCounts();
    }

    /** "12 followers · 5 following" (counted in Firestore). */
    private void loadFollowCounts() {
        long[] counts = {-2, -2}; // -2 = not loaded yet, -1 = failed
        String uid = UserSession.getUid();
        SocialRepository.countFollowers(uid, n -> { counts[0] = n; showFollowCounts(counts); });
        SocialRepository.countFollowing(uid, n -> { counts[1] = n; showFollowCounts(counts); });
    }

    private void showFollowCounts(long[] counts) {
        if (counts[0] == -2 || counts[1] == -2) return; // wait for both
        String followers = counts[0] < 0 ? "–" : String.valueOf(counts[0]);
        String following = counts[1] < 0 ? "–" : String.valueOf(counts[1]);
        setText(R.id.profile_follow_counts, followers + (counts[0] == 1 ? " follower" : " followers")
                + "  ·  " + following + " following");
    }

    private void showNames(View view) {
        ((TextView) view.findViewById(R.id.profile_name)).setText(UserSession.getDisplayName(requireContext()));
        ((TextView) view.findViewById(R.id.profile_handle)).setText("@" + UserSession.getUsername(requireContext()));
    }

    // Step 1: my own posts -> stats and recent posts. Then step 2 and 3 follow.
    private void loadMyPosts(View view) {
        SpotRepository.loadMySpots(requireContext(), mine -> {
            if (getView() == null) return;

            int shared = 0;
            int pictures = 0;
            for (Spot spot : mine) {
                if (spot.isPublic()) shared++;
                pictures += spot.getPhotoCount();
            }
            setText(R.id.stat_shared, String.valueOf(shared));
            setText(R.id.stat_pictures, String.valueOf(pictures));

            List<Spot> recent = mine.subList(0, Math.min(RECENT_POSTS, mine.size()));
            recentAdapter.setSpots(recent);
            setText(R.id.recent_empty, mine.isEmpty() ? "No posts yet. Tap + on the map to pin your first place." : "");
            getView().findViewById(R.id.recent_empty).setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);

            loadSaved(mine);
        }, message -> {
            if (getView() == null) return;
            setText(R.id.recent_empty, message + " Check your internet connection.");
            setText(R.id.stat_shared, "–");
            setText(R.id.stat_pictures, "–");
        });
    }

    // Step 2: saved places
    private void loadSaved(List<Spot> mine) {
        SavedRepository.loadSaved(requireContext(), saved -> {
            if (getView() == null) return;
            setText(R.id.stat_saved, String.valueOf(saved.size()));
            savedAdapter.setSpots(saved.subList(0, Math.min(SAVED_PREVIEW, saved.size())));
            TextView empty = getView().findViewById(R.id.saved_empty);
            empty.setText("Nothing saved yet. Tap the bookmark on a place to save it.");
            empty.setVisibility(saved.isEmpty() ? View.VISIBLE : View.GONE);

            loadRecommended(mine, saved);
        }, message -> {
            if (getView() == null) return;
            setText(R.id.stat_saved, "–");
            setText(R.id.saved_empty, message);
            loadRecommended(mine, new ArrayList<>());
        });
    }

    // Step 3: recommended places
    private void loadRecommended(List<Spot> mine, List<Spot> saved) {
        SpotRepository.loadVisibleSpots(requireContext(), all -> {
            if (getView() == null) return;

            List<Spot> picks = recommend(all, mine, saved);
            recommendedAdapter.setSpots(picks);

            boolean hasHistory = !mine.isEmpty() || !saved.isEmpty();
            String hint;
            if (picks.isEmpty()) {
                hint = "Nothing to recommend yet. Places from other people will show up here.";
            } else if (hasHistory) {
                hint = "Popular places from other people, in the categories you post and save.";
            } else {
                hint = "Popular places from other people. Post or save places to get better picks.";
            }
            setText(R.id.recommended_hint, hint);
        }, message -> {
            if (getView() == null) return;
            setText(R.id.recommended_hint, message);
        });
    }

    /**
     * Public places by other people that the user has not saved. Categories the user posts or
     * saves count more, then popularity (saves + likes), then newest.
     */
    private List<Spot> recommend(List<Spot> all, List<Spot> mine, List<Spot> saved) {
        Map<String, Integer> categoryWeight = new HashMap<>();
        for (Spot s : mine) categoryWeight.merge(Categories.normalize(s.getCategory()), 2, Integer::sum);
        for (Spot s : saved) categoryWeight.merge(Categories.normalize(s.getCategory()), 1, Integer::sum);

        Set<String> savedIds = new HashSet<>();
        for (Spot s : saved) savedIds.add(s.getId());
        String uid = UserSession.getUid();

        List<Spot> candidates = new ArrayList<>();
        for (Spot s : all) {
            if (!s.isPublic() || uid.equals(s.getCreatorId()) || savedIds.contains(s.getId())) continue;
            candidates.add(s);
        }
        Collections.sort(candidates, (a, b) -> {
            int scoreA = score(a, categoryWeight);
            int scoreB = score(b, categoryWeight);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            return Long.compare(b.getCreatedAt(), a.getCreatedAt());
        });
        return candidates.subList(0, Math.min(RECOMMENDED, candidates.size()));
    }

    private int score(Spot spot, Map<String, Integer> categoryWeight) {
        Integer weight = categoryWeight.get(Categories.normalize(spot.getCategory()));
        return (weight == null ? 0 : weight) * 10 + spot.getSaves() * 2 + spot.getLikes();
    }

    private void setText(int id, String text) {
        View root = getView();
        if (root != null) ((TextView) root.findViewById(id)).setText(text);
    }
}