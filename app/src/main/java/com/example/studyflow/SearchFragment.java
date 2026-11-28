package com.example.studyflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search: type to find places by name, review, category or username, and/or tap a category chip.
 * It searches every public place plus the user's own private ones. People whose username starts
 * with what you typed are listed above the places; tapping one opens their profile.
 *
 * The places can be sorted (best match, newest, most liked, closest), and the last searches are
 * remembered on the phone and shown while nothing is typed.
 */
public class SearchFragment extends Fragment {

    private static final int MAX_RESULTS = 50;
    private static final int MAX_RECENT_SHOWN = 5;
    private static final long USER_SEARCH_DELAY_MS = 300;   // wait for the person to stop typing

    private static final int SORT_BEST = 0;
    private static final int SORT_NEWEST = 1;
    private static final int SORT_LIKED = 2;
    private static final int SORT_CLOSEST = 3;

    private final ArrayList<Spot> allSpots = new ArrayList<>();
    private boolean loaded;
    private SpotListAdapter adapter;
    private UserAdapter userAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingUserSearch;
    private int userRequestId;                       // answers for older searches are ignored
    private List<UserProfile> users = new ArrayList<>();
    private int placeCount;
    private boolean browsing;                        // nothing typed, no chip: showing a starting list

    private int sortMode = SORT_BEST;
    private Location here;                           // where the user is (for "Closest"), null = unknown
    private boolean waitingForLocation;              // the person picked "Closest" and we are finding them

    private final ActivityResultLauncher<String[]> requestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) fetchLocation();
                else closestUnavailable("Allow location to sort by distance.");
            });

    public SearchFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText input = view.findViewById(R.id.search_edit_text);
        View clear = view.findViewById(R.id.search_clear);
        ChipGroup chips = view.findViewById(R.id.search_chip_group);
        ChipGroup sortGroup = view.findViewById(R.id.search_sort_group);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new SpotListAdapter(new SpotListAdapter.Listener() {
            @Override
            public void onOpen(Spot spot) {
                rememberQuery();
                hideKeyboard();
                PlaceDetailsFragment.open(requireActivity(), spot);
            }

            @Override
            public void onShowOnMap(Spot spot) {
                rememberQuery();
                hideKeyboard();
                ((MainActivity) requireActivity()).focusOnSpot(spot);
            }
        });
        userAdapter = new UserAdapter(user -> {
            rememberQuery();
            hideKeyboard();
            UserProfileFragment.open(requireActivity(), user.uid, user.username);
        });
        RecyclerView list = view.findViewById(R.id.search_results);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new ConcatAdapter(userAdapter, adapter));   // people first, then places

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                runSearch();
            }
        });
        clear.setOnClickListener(v -> input.setText(""));
        chips.setOnCheckedStateChangeListener((group, ids) -> runSearch());
        input.setOnEditorActionListener((v, actionId, event) -> {
            rememberQuery();      // the keyboard's search key saves the search
            hideKeyboard();
            return true;
        });

        // Best match / Newest / Most liked / Closest
        sortGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            int mode = id == R.id.search_sort_newest ? SORT_NEWEST
                    : id == R.id.search_sort_liked ? SORT_LIKED
                      : id == R.id.search_sort_closest ? SORT_CLOSEST : SORT_BEST;

            if (mode == SORT_CLOSEST && here == null) {
                // we first need to know where the person is
                waitingForLocation = true;
                if (hasLocationPermission()) {
                    fetchLocation();
                } else {
                    requestLocation.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION});
                }
                return;
            }
            waitingForLocation = false;
            sortMode = mode;
            runSearch();
        });

        view.findViewById(R.id.recent_clear).setOnClickListener(v -> {
            SearchHistory.clear(requireContext());
            showRecent(true);
        });

        // Show the keyboard right away
        input.requestFocus();
        input.postDelayed(() -> {
            if (getView() == null) return;
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);

        // If location is already allowed, find the person quietly so "Closest" works at once
        if (hasLocationPermission()) fetchLocation();

        SpotRepository.loadVisibleSpots(requireContext(), spots -> {
            if (getView() == null) return;
            allSpots.clear();
            allSpots.addAll(spots);
            loaded = true;
            runSearch();
        }, message -> {
            if (getView() == null) return;
            setStatus(message + " Check your internet connection.");
        });
    }

    private void runSearch() {
        View root = getView();
        if (root == null || !loaded) return;

        String query = ((EditText) root.findViewById(R.id.search_edit_text)).getText().toString();
        String[] words = simplify(query).trim().isEmpty()
                ? new String[0] : simplify(query).trim().split("\\s+");
        String category = selectedCategory(root);

        // People are searched by username, so only when something is typed and no category chip is on
        searchPeople(query, category);

        if (words.length == 0 && category == null) {
            // nothing typed and no chip: show 10 places (sorted the way the person chose) as a starting point
            List<Spot> start = new ArrayList<>(allSpots);
            sortSpots(start, null);
            List<Spot> top = new ArrayList<>(start.subList(0, Math.min(10, start.size())));
            adapter.setSpots(top);
            browsing = true;
            placeCount = top.size();
            showRecent(true);
            updateStatus();
            return;
        }
        browsing = false;
        showRecent(false);

        List<Spot> matches = new ArrayList<>();
        final Map<Spot, Integer> scores = new IdentityHashMap<>();
        for (Spot spot : allSpots) {
            if (category != null && !Categories.normalize(spot.getCategory()).equals(category)) continue;
            int score = scoreOf(spot, words);
            if (score < 0) continue;
            scores.put(spot, score);
            matches.add(spot);
        }
        sortSpots(matches, scores);
        if (matches.size() > MAX_RESULTS) matches = new ArrayList<>(matches.subList(0, MAX_RESULTS));

        adapter.setSpots(matches);
        placeCount = matches.size();
        updateStatus();
    }

    // ------------------------------------------------------------------ sorting

    /** Sorts the list in place using the chosen sort. {@code scores} = how well each place matched the words. */
    private void sortSpots(List<Spot> list, @Nullable Map<Spot, Integer> scores) {
        switch (sortMode) {
            case SORT_NEWEST:
                Collections.sort(list, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                break;

            case SORT_LIKED:
                Collections.sort(list, (a, b) -> {
                    int byLikes = Integer.compare(b.getLikes(), a.getLikes());
                    return byLikes != 0 ? byLikes : Long.compare(b.getCreatedAt(), a.getCreatedAt());
                });
                break;

            case SORT_CLOSEST:
                if (here != null) {
                    final Map<Spot, Float> meters = new IdentityHashMap<>();
                    float[] d = new float[1];
                    for (Spot s : list) {
                        Location.distanceBetween(here.getLatitude(), here.getLongitude(),
                                s.getLatitude(), s.getLongitude(), d);
                        meters.put(s, d[0]);
                    }
                    Collections.sort(list, (a, b) -> Float.compare(meters.get(a), meters.get(b)));
                    break;
                }
                // no location yet: fall through to the best match order

            default:
                // best matches first, then the newest
                Collections.sort(list, (a, b) -> {
                    if (scores != null) {
                        int sa = scores.get(a);
                        int sb = scores.get(b);
                        if (sa != sb) return Integer.compare(sb, sa);
                    }
                    return Long.compare(b.getCreatedAt(), a.getCreatedAt());
                });
        }
    }

    private boolean hasLocationPermission() {
        Context context = getContext();
        if (context == null) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // only called after the permission check
    private void fetchLocation() {
        Context context = getContext();
        if (context == null) return;

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        onLocationFound(location);
                    } else {
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                        new CancellationTokenSource().getToken())
                                .addOnSuccessListener(current -> {
                                    if (current != null) onLocationFound(current);
                                    else closestUnavailable("Could not find your location. Make sure location is turned on.");
                                })
                                .addOnFailureListener(e ->
                                        closestUnavailable("Could not find your location. Make sure location is turned on."));
                    }
                })
                .addOnFailureListener(e ->
                        closestUnavailable("Could not find your location. Make sure location is turned on."));
    }

    private void onLocationFound(Location location) {
        if (getView() == null) return;
        here = location;
        if (waitingForLocation) {          // the person is waiting for "Closest"
            waitingForLocation = false;
            sortMode = SORT_CLOSEST;
            runSearch();
        }
    }

    /** "Closest" cannot work: say why and go back to "Best match". */
    private void closestUnavailable(String message) {
        View root = getView();
        if (root == null || !waitingForLocation) return;   // a quiet lookup that failed: nothing to tell
        waitingForLocation = false;
        Toast.makeText(root.getContext(), message, Toast.LENGTH_LONG).show();
        ((ChipGroup) root.findViewById(R.id.search_sort_group)).check(R.id.search_sort_best);
    }

    // ------------------------------------------------------------ recent searches

    /** Saves what is typed in the search box (does nothing when it is empty). */
    private void rememberQuery() {
        View root = getView();
        if (root == null) return;
        String typed = ((EditText) root.findViewById(R.id.search_edit_text)).getText().toString();
        SearchHistory.add(requireContext(), typed);
    }

    /** Shows the saved searches (only while nothing is typed). */
    private void showRecent(boolean show) {
        View root = getView();
        if (root == null) return;

        View section = root.findViewById(R.id.recent_section);
        List<String> recent = SearchHistory.load(requireContext());
        if (!show || recent.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);

        EditText input = root.findViewById(R.id.search_edit_text);
        LinearLayout container = root.findViewById(R.id.recent_list);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < Math.min(MAX_RECENT_SHOWN, recent.size()); i++) {
            String query = recent.get(i);
            View row = inflater.inflate(R.layout.item_recent_search, container, false);
            ((TextView) row.findViewById(R.id.recent_text)).setText("↺   " + query);
            row.setOnClickListener(v -> {
                input.setText(query);                 // this starts the search by itself
                input.setSelection(query.length());
            });
            row.findViewById(R.id.recent_remove).setOnClickListener(v -> {
                SearchHistory.remove(requireContext(), query);
                showRecent(true);
            });
            container.addView(row);
        }
    }

    // ------------------------------------------------------------------ people

    /** Looks people up by username (a short moment after the last key press). */
    private void searchPeople(String query, @Nullable String category) {
        if (pendingUserSearch != null) handler.removeCallbacks(pendingUserSearch);
        userRequestId++;                                   // any answer still on its way is out of date

        String typed = query.trim();
        if (typed.startsWith("@")) typed = typed.substring(1);
        final String usernameQuery = typed.trim();

        if (usernameQuery.isEmpty() || category != null) {
            showPeople(new ArrayList<>());
            return;
        }

        final int requestId = userRequestId;
        pendingUserSearch = () -> SocialRepository.searchUsers(usernameQuery, found -> {
            if (getView() == null || requestId != userRequestId) return;
            showPeople(found);
        }, message -> {
            if (getView() == null || requestId != userRequestId) return;
            showPeople(new ArrayList<>());                 // places still work without people
        });
        handler.postDelayed(pendingUserSearch, USER_SEARCH_DELAY_MS);
    }

    private void showPeople(List<UserProfile> found) {
        users = found;
        userAdapter.setUsers(found);
        updateStatus();
    }

    /** "2 PEOPLE · 3 PLACES FOUND", "NEWEST PLACES", "MOST LIKED PLACES", "NOTHING FOUND"... */
    private void updateStatus() {
        if (!loaded) return;
        if (browsing) {
            if (allSpots.isEmpty()) {
                setStatus("NO PLACES YET");
            } else {
                setStatus(sortMode == SORT_LIKED ? "MOST LIKED PLACES"
                        : sortMode == SORT_CLOSEST ? "CLOSEST PLACES" : "NEWEST PLACES");
            }
            return;
        }
        if (users.isEmpty() && placeCount == 0) {
            setStatus("NOTHING FOUND");
            return;
        }
        List<String> parts = new ArrayList<>();
        if (!users.isEmpty()) parts.add(users.size() + (users.size() == 1 ? " PERSON" : " PEOPLE"));
        if (placeCount > 0) parts.add(placeCount + (placeCount == 1 ? " PLACE" : " PLACES"));
        setStatus(android.text.TextUtils.join("  ·  ", parts) + " FOUND");
    }

    /** -1 when some word is not found anywhere; otherwise higher = better match. */
    private int scoreOf(Spot spot, String[] words) {
        String name = simplify(spot.getName());
        String category = simplify(Categories.keywords(spot.getCategory()) + " "
                + Categories.normalize(spot.getCategory()));
        String creator = simplify(spot.getCreator());
        String review = simplify(spot.getReview());

        int score = 0;
        for (String word : words) {
            if (name.startsWith(word) || name.contains(" " + word)) score += 5;
            else if (name.contains(word)) score += 3;
            else if (category.contains(word)) score += 3;
            else if (creator.contains(word)) score += 2;
            else if (review.contains(word)) score += 1;
            else return -1;
        }
        return score;
    }

    /** Lower case and without accents, so "cafe" finds "Café". */
    private static String simplify(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    @Nullable
    private String selectedCategory(View root) {
        ChipGroup group = root.findViewById(R.id.search_chip_group);
        int id = group.getCheckedChipId();
        if (id == View.NO_ID) return null;
        Chip chip = group.findViewById(id);
        return chip == null ? null : Categories.normalize(chip.getText().toString());
    }

    private void setStatus(String text) {
        View root = getView();
        if (root != null) ((TextView) root.findViewById(R.id.search_status)).setText(text);
    }

    private void hideKeyboard() {
        View root = getView();
        if (root == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    @Override
    public void onDestroyView() {
        hideKeyboard();
        if (pendingUserSearch != null) handler.removeCallbacks(pendingUserSearch);
        userRequestId++;
        super.onDestroyView();
    }
}