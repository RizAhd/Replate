package com.replate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.FavoriteHotel;

import java.util.ArrayList;
import java.util.List;

/** The saved hotels shown on the Hotels tab of the Favorites screen. */
public class FavoriteHotelAdapter extends RecyclerView.Adapter<FavoriteHotelAdapter.Holder> {

    public interface Listener {
        void onViewDeals(FavoriteHotel hotel);

        void onRemove(FavoriteHotel hotel);
    }

    private final List<FavoriteHotel> hotels = new ArrayList<>();
    private final Listener listener;

    public FavoriteHotelAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<FavoriteHotel> newHotels) {
        hotels.clear();
        hotels.addAll(newHotels);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_hotel, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FavoriteHotel hotel = hotels.get(position);
        holder.name.setText(hotel.getName() != null && !hotel.getName().trim().isEmpty()
                ? hotel.getName()
                : holder.itemView.getContext().getString(R.string.food_detail_default_hotel));

        boolean hasAddress = hotel.getAddress() != null && !hotel.getAddress().trim().isEmpty();
        holder.address.setVisibility(hasAddress ? View.VISIBLE : View.GONE);
        holder.address.setText(hasAddress ? hotel.getAddress() : "");

        holder.viewDeals.setOnClickListener(v -> listener.onViewDeals(hotel));
        holder.itemView.setOnClickListener(v -> listener.onViewDeals(hotel));
        holder.remove.setOnClickListener(v -> listener.onRemove(hotel));
    }

    @Override
    public int getItemCount() {
        return hotels.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView address;
        final View viewDeals;
        final ImageView remove;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvFavHotelName);
            address = itemView.findViewById(R.id.tvFavHotelAddress);
            viewDeals = itemView.findViewById(R.id.btnFavHotelDeals);
            remove = itemView.findViewById(R.id.ivFavHotelRemove);
        }
    }
}
