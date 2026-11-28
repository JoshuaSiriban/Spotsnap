package com.example.studyflow;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The lines of the activity list. A like opens the spot, a follow opens the person's profile. */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    public interface Listener {
        void onOpenSpot(NotificationRepository.Item item);

        void onOpenPerson(NotificationRepository.Item item);
    }

    private final List<NotificationRepository.Item> items = new ArrayList<>();
    private final Listener listener;

    public NotificationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<NotificationRepository.Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NotificationRepository.Item item = items.get(position);
        boolean like = NotificationRepository.TYPE_LIKE.equals(item.type);

        h.icon.setImageResource(like ? R.drawable.ic_heart : R.drawable.ic_person);

        // "@maria liked your spot Cafe Ocampo": the name and the spot in bold
        String actor = "@" + (item.actorUsername.isEmpty() ? "someone" : item.actorUsername);
        SpannableStringBuilder text = new SpannableStringBuilder(actor);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, actor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (like) {
            text.append(" liked your spot");
            if (!item.spotName.isEmpty()) {
                text.append(" ");
                int start = text.length();
                text.append(item.spotName);
                text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            text.append(" started following you");
        }
        h.text.setText(text);

        h.time.setText(item.createdAt > 0
                ? DateUtils.getRelativeTimeSpanString(item.createdAt, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS)
                : "Just now");
        h.dot.setVisibility(item.read ? View.GONE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> {
            if (like) listener.onOpenSpot(item);
            else listener.onOpenPerson(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;
        final TextView time;
        final View dot;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.notif_icon);
            text = v.findViewById(R.id.notif_text);
            time = v.findViewById(R.id.notif_time);
            dot = v.findViewById(R.id.notif_dot);
        }
    }
}