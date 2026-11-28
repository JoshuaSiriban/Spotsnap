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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Activity screen.
 *   Everyone       the most recent public posts from other people, newest first
 *   Following      only the people you follow
 *   Notifications  who liked your spots and who started following you
 */
public class ActivityFeedFragment extends Fragment {

    private static final int MAX_POSTS = 50;

    private final ArrayList<Spot> posts = new ArrayList<>();   // public posts by others, newest first
    private Set<String> followingIds;                          // null until loaded (or when it failed)
    private boolean postsLoaded;
    private boolean followingFailed;
    private String error;
    private FeedAdapter adapter;

    private NotificationAdapter notificationAdapter;
    private List<NotificationRepository.Item> notifications = new ArrayList<>();
    private boolean notificationsLoaded;
    private String notificationsError;
    private boolean notificationsMarkedRead;

    private RecyclerView feedList;

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

        notificationAdapter = new NotificationAdapter(new NotificationAdapter.Listener() {
            @Override
            public void onOpenSpot(NotificationRepository.Item item) {
                SpotRepository.loadSpot(requireContext(), item.spotId, spot -> {
                    if (getView() == null) return;
                    if (spot == null) {
                        Toast.makeText(requireContext(), "That place is no longer available.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        PlaceDetailsFragment.open(requireActivity(), spot);
                    }
                });
            }

            @Override
            public void onOpenPerson(NotificationRepository.Item item) {
                UserProfileFragment.open(requireActivity(), item.actorUid, item.actorUsername);
            }
        });

        feedList = view.findViewById(R.id.feed_list);
        feedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        feedList.setAdapter(adapter);

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

        NotificationRepository.load(items -> {
            if (getView() == null) return;
            notifications = items;
            notificationsLoaded = true;
            notificationsError = null;

            int unread = 0;
            for (NotificationRepository.Item item : items) if (!item.read) unread++;
            showNotificationCount(unread);
            render();
        }, message -> {
            if (getView() == null) return;
            notificationsError = message + " Check your internet connection.";
            render();
        });
    }

    private boolean followingSelected() {
        View root = getView();
        return root != null && ((ChipGroup) root.findViewById(R.id.feed_chip_group))
                .getCheckedChipId() == R.id.feed_chip_following;
    }

    private boolean notificationsSelected() {
        View root = getView();
        return root != null && ((ChipGroup) root.findViewById(R.id.feed_chip_group))
                .getCheckedChipId() == R.id.feed_chip_notifications;
    }

    /** "Notifications (3)" on the chip while there is something new. */
    private void showNotificationCount(int unread) {
        View root = getView();
        if (root == null) return;
        ((Chip) root.findViewById(R.id.feed_chip_notifications))
                .setText(unread > 0 ? "Notifications (" + unread + ")" : "Notifications");
    }

    private void render() {
        View root = getView();
        if (root == null) return;
        if (notificationsSelected()) {
            renderNotifications(root);
            return;
        }
        if (feedList.getAdapter() != adapter) feedList.setAdapter(adapter);

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

    /** The "Notifications" chip: who liked your spots and who followed you. */
    private void renderNotifications(View root) {
        TextView status = root.findViewById(R.id.feed_status);
        if (feedList.getAdapter() != notificationAdapter) feedList.setAdapter(notificationAdapter);

        if (notificationsError != null) {
            notificationAdapter.setItems(new ArrayList<>());
            status.setText(notificationsError);
            status.setVisibility(View.VISIBLE);
            return;
        }
        if (!notificationsLoaded) {
            notificationAdapter.setItems(new ArrayList<>());
            status.setText("Loading...");
            status.setVisibility(View.VISIBLE);
            return;
        }

        notificationAdapter.setItems(notifications);
        if (notifications.isEmpty()) {
            status.setText("No activity yet. When someone likes one of your spots or follows you, it shows up here.");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }

        // The person is looking at them now: they count as seen (the dots stay until you leave)
        if (!notificationsMarkedRead) {
            notificationsMarkedRead = true;
            NotificationRepository.markAllRead(notifications);
            showNotificationCount(0);
        }
    }
}