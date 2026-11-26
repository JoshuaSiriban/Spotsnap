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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.FragmentManager;

public class PlaceDetailsFragment extends Fragment {

    private Spot spot;



    public PlaceDetailsFragment() {
    }

    public void setSpot(Spot spot)
    {
        this.spot = spot;
    }

    public static PlaceDetailsFragment newInstance(@Nullable String name, @Nullable String category,
                                                   @Nullable String review) {
        PlaceDetailsFragment f = new PlaceDetailsFragment();
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("category", category);
        b.putString("review", review);
        f.setArguments(b);
        return f;
    }

    /** Opens Place Details for a spot from any screen. */
    public static void open(androidx.fragment.app.FragmentActivity activity, Spot spot) {
        PlaceDetailsFragment fragment = newInstance(spot.getName(), spot.getCategory(), spot.getReview());
        fragment.setSpot(spot);
        DrawerMenu.open(activity, fragment);
    }

    /** Shows a message over the photo strip, or hides it when {@code message} is null. */
    private void showStatus(TextView status, @Nullable String message) {
        status.setText(message);
        status.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_place_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (spot != null)
        {
            ((TextView) view.findViewById(R.id.txt_name))
                    .setText(spot.getName());

            ((TextView) view.findViewById(R.id.txt_category))
                    .setText(Categories.normalize(spot.getCategory()).toUpperCase());

            ((TextView) view.findViewById(R.id.txt_review))
                    .setText(spot.getReview());

            ((TextView) view.findViewById(R.id.likesCount)).setText(String.valueOf(spot.getLikes()));

            ((TextView) view.findViewById(R.id.savesCount)).setText(String.valueOf(spot.getSaves()));

            ((TextView) view.findViewById(R.id.photosCount)).setText(String.valueOf(Math.max(spot.getPhotoCount(), spot.images.size())));

            ((TextView) view.findViewById(R.id.spotCreator)).setText("Pinned by @" + spot.getCreator());

            ((TextView) view.findViewById(R.id.noteCreator)).setText("— @" + spot.getCreator());

            ((TextView) view.findViewById(R.id.txt_location)).setText(String.format(
                    java.util.Locale.US, "%.4f, %.4f", spot.getLatitude(), spot.getLongitude()));

            ((TextView) view.findViewById(R.id.txt_visibility)).setText(
                    spot.isPublic() ? "Public post" : "Private post (only you can see it)");
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // Edit and delete: only for the owner of the post
        View ownerActions = view.findViewById(R.id.owner_actions);
        if (SpotRepository.isOwner(spot)) {
            ownerActions.setVisibility(View.VISIBLE);
            view.findViewById(R.id.btn_edit).setOnClickListener(v -> startEditing());
            view.findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        } else {
            ownerActions.setVisibility(View.GONE);
        }

        RecyclerView rv = view.findViewById(R.id.rv_photos);
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        PolaroidAdapter adapter = new PolaroidAdapter(spot != null ? spot.images : new java.util.ArrayList<Photo>());
        rv.setAdapter(adapter);

        // Spots from the database only carry a small thumbnail, so load their pictures now
        TextView status = view.findViewById(R.id.txt_photos_status);
        if (spot != null && spot.images.isEmpty() && spot.getPhotoCount() > 0 && spot.getId() != null) {
            showStatus(status, "Loading photos...");
            SpotRepository.loadPhotos(spot, loaded -> {
                spot.images = loaded;
                adapter.setPhotos(loaded);

                View root = getView();
                if (root == null) return; // the screen was closed while loading
                ((TextView) root.findViewById(R.id.photosCount)).setText(String.valueOf(loaded.size()));
                showStatus(root.findViewById(R.id.txt_photos_status),
                        loaded.isEmpty() ? "No photos for this spot yet" : null);
            }, message -> {
                View root = getView();
                if (root == null) return;
                showStatus(root.findViewById(R.id.txt_photos_status),
                        message + " Check your internet connection and try again.");
            });
        } else if (adapter.getItemCount() == 0) {
            showStatus(status, "No photos for this spot yet");
        }

        setupLikeButton(view);
        setupSaveButton(view);
        setupFollowButton(view);

        view.findViewById(R.id.btn_go).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Directions (UI only)", Toast.LENGTH_SHORT).show());
    }

    private void startEditing() {
        AddSpotFragment edit = new AddSpotFragment();
        edit.setEditingSpot(spot);
        DrawerMenu.open(requireActivity(), edit);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete this post?")
                .setMessage("\"" + spot.getName() + "\" and its photos will be removed for good.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSpot())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSpot() {
        android.content.Context appContext = requireContext().getApplicationContext();
        androidx.fragment.app.FragmentActivity activity = requireActivity();
        SpotRepository.deleteSpot(spot, new SpotRepository.OnDone() {
            @Override
            public void onDone() {
                Toast.makeText(appContext, "Post deleted", Toast.LENGTH_SHORT).show();
                FragmentManager fm = activity.getSupportFragmentManager();
                if (!fm.isStateSaved()) {
                    fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** The bookmark button: shows whether the spot is saved and saves / unsaves it. */
    private void setupSaveButton(View root) {
        View saveButton = root.findViewById(R.id.btn_save);
        if (spot == null || spot.getId() == null) {
            // old sample spots are not in the database, so they cannot be saved
            saveButton.setOnClickListener(v -> Toast.makeText(requireContext(),
                    "This sample place cannot be saved.", Toast.LENGTH_SHORT).show());
            return;
        }

        saveButton.setEnabled(false); // until we know the current state
        SavedRepository.isSaved(spot.getId(), saved -> {
            if (getView() == null) return;
            saveButton.setSelected(saved);
            saveButton.setEnabled(true);
        });

        saveButton.setOnClickListener(v -> {
            boolean save = !v.isSelected();
            v.setEnabled(false);
            SavedRepository.setSaved(spot, save, error -> {
                if (getView() == null) return;
                v.setEnabled(true);
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    return;
                }
                v.setSelected(save);
                spot.setSaves(Math.max(0, spot.getSaves() + (save ? 1 : -1)));
                ((TextView) getView().findViewById(R.id.savesCount)).setText(String.valueOf(spot.getSaves()));
                Toast.makeText(requireContext(), save ? "Saved to your places" : "Removed from saved",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** The heart button: shows whether you liked the spot and likes / unlikes it in Firestore. */
    private void setupLikeButton(View root) {
        View likeButton = root.findViewById(R.id.btn_like);
        if (spot == null || spot.getId() == null) {
            likeButton.setOnClickListener(v -> Toast.makeText(requireContext(),
                    "This sample place cannot be liked.", Toast.LENGTH_SHORT).show());
            return;
        }

        likeButton.setEnabled(false); // until we know the current state
        SocialRepository.isLiked(spot.getId(), liked -> {
            if (getView() == null) return;
            likeButton.setSelected(liked);
            likeButton.setEnabled(true);
        });

        likeButton.setOnClickListener(v -> {
            boolean like = !v.isSelected();
            v.setEnabled(false);
            SocialRepository.setLiked(spot, like, error -> {
                if (getView() == null) return;
                v.setEnabled(true);
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    return;
                }
                v.setSelected(like);
                spot.setLikes(Math.max(0, spot.getLikes() + (like ? 1 : -1)));
                ((TextView) getView().findViewById(R.id.likesCount)).setText(String.valueOf(spot.getLikes()));
            });
        });
    }

    /** Follow / Following button for the person who pinned the spot (hidden for your own posts). */
    private void setupFollowButton(View root) {
        MaterialButton follow = root.findViewById(R.id.btn_follow);
        String creatorId = spot == null ? null : spot.getCreatorId();
        if (creatorId == null || creatorId.equals(UserSession.getUid())) {
            follow.setVisibility(View.GONE); // your own post, or an old sample spot
            return;
        }

        boolean[] following = {false};
        follow.setEnabled(false);
        SocialRepository.isFollowing(creatorId, isFollowing -> {
            if (getView() == null) return;
            following[0] = isFollowing;
            follow.setText(isFollowing ? "Following" : "Follow");
            follow.setEnabled(true);
        });

        follow.setOnClickListener(v -> {
            boolean follow_ = !following[0];
            follow.setEnabled(false);
            SocialRepository.setFollowing(UserSession.getUsername(requireContext()), creatorId,
                    spot.getCreator(), follow_, error -> {
                        if (getView() == null) return;
                        follow.setEnabled(true);
                        if (error != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        following[0] = follow_;
                        follow.setText(follow_ ? "Following" : "Follow");
                        Toast.makeText(requireContext(), follow_
                                ? "You follow @" + spot.getCreator()
                                : "Unfollowed @" + spot.getCreator(), Toast.LENGTH_SHORT).show();
                    });
        });
    }
}