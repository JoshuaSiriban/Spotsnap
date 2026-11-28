package com.example.studyflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** A list of people (used by Search): avatar, name and @username. The whole row opens their profile. */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

    public interface Listener {
        void onOpen(UserProfile user);
    }

    private final List<UserProfile> users = new ArrayList<>();
    private final Listener listener;

    public UserAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setUsers(List<UserProfile> newUsers) {
        users.clear();
        users.addAll(newUsers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        UserProfile user = users.get(position);
        h.name.setText(user.displayName());
        h.handle.setText("@" + user.username);
        h.itemView.setOnClickListener(v -> listener.onOpen(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView handle;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.user_name);
            handle = v.findViewById(R.id.user_handle);
        }
    }
}