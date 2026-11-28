package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Someone else's profile: their username, follower counts, a Follow / Unfollow button and
 * the public places they pinned. Opened by tapping a creator's name on Place Details, or a
 * person in Search.
 */
public class UserProfileFragment extends Fragment {

    private static final String ARG_UID = "uid";
    private static final String ARG_USERNAME = "username";

    private String uid;
    private String username = "";
    private String fullName = "";
    private boolean following;
    private boolean followKnown;     // true once we know whether the logged in user follows them
    private final List<Spot> spots = new ArrayList<>();

    private SpotListAdapter adapter;

    public UserProfileFragment() {
    }

    /**
     * Opens a person's profile from any screen. Your own uid opens your own profile instead.
     * {@code username} can be null; it is then loaded from Firestore.
     */
    public static void open(FragmentActivity activity, String uid, @Nullable String username) {
        if (uid == null || uid.isEmpty()) return;
        if (uid.equals(UserSession.getUid())) {
            DrawerMenu.open(activity, new ProfileFragment());
            return;
        }
        UserProfileFragment fragment = new UserProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        args.putString(ARG_USERNAME, username == null ? "" : username);
        fragment.setArguments(args);
        DrawerMenu.open(activity, fragment);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        uid = args.getString(ARG_UID, "");
        username = args.getString(ARG_USERNAME, "");

        showNames();
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new SpotListAdapter(new SpotListAdapter.Listener() {
            @Override
            public void onOpen(Spot spot) {
                PlaceDetailsFragment.open(requireActivity(), spot);
            }

            @Override
            public void onShowOnMap(Spot spot) {
                ((MainActivity) requireActivity()).focusOnSpot(spot);
            }
        });
        RecyclerView list = view.findViewById(R.id.rv_up_spots);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.up_show_map).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showSpotsOnMap(spots, "@" + username));

        setupFollowButton(view);
        loadProfile();
        loadFollowCounts();
        loadSpots();
    }

    // ------------------------------------------------------------------ header

    private void showNames() {
        View root = getView();
        if (root == null) return;
        String shownName = fullName.isEmpty() ? username : fullName;
        ((TextView) root.findViewById(R.id.up_name)).setText(shownName);
        ((TextView) root.findViewById(R.id.up_handle)).setText(username.isEmpty() ? "" : "@" + username);
    }

    /** Fills in the full name, the bio and the picture (and the username, when we only had the uid). */
    private void loadProfile() {
        SocialRepository.loadUser(uid, user -> {
            View root = getView();
            if (root == null || user == null) return;
            username = user.username;
            fullName = user.fullName;
            showNames();

            AvatarUtils.show(root.findViewById(R.id.up_avatar), user.photo, 24);
            TextView bio = root.findViewById(R.id.up_bio);
            bio.setText(user.bio);
            bio.setVisibility(user.bio.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    /** "12 followers · 5 following" (counted in Firestore). */
    private void loadFollowCounts() {
        long[] counts = {-2, -2}; // -2 = not loaded yet, -1 = failed
        SocialRepository.countFollowers(uid, n -> { counts[0] = n; showFollowCounts(counts); });
        SocialRepository.countFollowing(uid, n -> { counts[1] = n; showFollowCounts(counts); });
    }

    private void showFollowCounts(long[] counts) {
        View root = getView();
        if (root == null || counts[0] == -2 || counts[1] == -2) return; // wait for both
        String followers = counts[0] < 0 ? "–" : String.valueOf(counts[0]);
        String followingCount = counts[1] < 0 ? "–" : String.valueOf(counts[1]);
        ((TextView) root.findViewById(R.id.up_follow_counts)).setText(
                followers + (counts[0] == 1 ? " follower" : " followers")
                        + "  ·  " + followingCount + " following");
    }

    // ------------------------------------------------------------------ follow

    private void setupFollowButton(View root) {
        MaterialButton follow = root.findViewById(R.id.up_follow);
        follow.setEnabled(false); // until we know the current state

        SocialRepository.isFollowing(uid, isFollowing -> {
            if (getView() == null) return;
            following = isFollowing;
            followKnown = true;
            showFollowState();
        });

        follow.setOnClickListener(v -> {
            if (!followKnown) return;
            boolean wantToFollow = !following;
            follow.setEnabled(false);
            SocialRepository.setFollowing(UserSession.getUsername(requireContext()), uid, username,
                    wantToFollow, error -> {
                        if (getView() == null) return;
                        follow.setEnabled(true);
                        if (error != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        following = wantToFollow;
                        showFollowState();
                        loadFollowCounts();
                        Toast.makeText(requireContext(), wantToFollow
                                ? "You follow @" + username
                                : "Unfollowed @" + username, Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void showFollowState() {
        View root = getView();
        if (root == null) return;
        MaterialButton follow = root.findViewById(R.id.up_follow);
        follow.setText(following ? "Following" : "Follow");
        follow.setEnabled(followKnown);
    }

    // ------------------------------------------------------------------- spots

    private void loadSpots() {
        SpotRepository.loadPublicSpotsBy(requireContext(), uid, loaded -> {
            View root = getView();
            if (root == null) return;

            spots.clear();
            spots.addAll(loaded);
            adapter.setSpots(spots);

            int pictures = 0;
            for (Spot spot : spots) pictures += spot.getPhotoCount();
            ((TextView) root.findViewById(R.id.up_stat_shared)).setText(String.valueOf(spots.size()));
            ((TextView) root.findViewById(R.id.up_stat_pictures)).setText(String.valueOf(pictures));

            TextView status = root.findViewById(R.id.up_status);
            status.setText("No public places yet.");
            status.setVisibility(spots.isEmpty() ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.up_show_map).setVisibility(spots.isEmpty() ? View.GONE : View.VISIBLE);
        }, message -> {
            View root = getView();
            if (root == null) return;
            ((TextView) root.findViewById(R.id.up_stat_shared)).setText("–");
            ((TextView) root.findViewById(R.id.up_stat_pictures)).setText("–");
            TextView status = root.findViewById(R.id.up_status);
            status.setText(message + " Check your internet connection.");
            status.setVisibility(View.VISIBLE);
        });
    }
}