package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.Order;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying orders
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private Context context;
    private List<Order> orders;
    private OnOrderClickListener listener;

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
        void onViewQRClick(Order order);
        void onCancelOrderClick(Order order);
    }

    public OrderAdapter(Context context, List<Order> orders, OnOrderClickListener listener) {
        this.context = context;
        this.orders = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order);
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    class OrderViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvOrderId, tvHotelName, tvItemsCount, tvTotalAmount;
        TextView tvOrderStatus, tvOrderDate, tvPickupTime;
        Button btnViewQR;
        Button btnCancelOrder;
        View statusIndicator;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardOrder);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvHotelName = itemView.findViewById(R.id.tvHotelName);
            tvItemsCount = itemView.findViewById(R.id.tvItemsCount);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvPickupTime = itemView.findViewById(R.id.tvPickupTime);
            btnViewQR = itemView.findViewById(R.id.btnViewQR);
            btnCancelOrder = itemView.findViewById(R.id.btnCancelOrder);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }

        void bind(Order order) {
            // Order ID (show last 8 characters)
            String orderId = order.getOrderId();
            if (orderId != null && orderId.length() > 8) {
                orderId = "#" + orderId.substring(orderId.length() - 8).toUpperCase();
            }
            tvOrderId.setText(orderId);

            // Hotel name
            tvHotelName.setText(order.getHotelName() != null ? order.getHotelName() : "Restaurant");

            // Items count
            int itemCount = 0;
            if (order.getItems() instanceof java.util.List) {
                itemCount = ((java.util.List<?>) order.getItems()).size();
            } else if (order.getItems() instanceof java.util.Map) {
                itemCount = ((java.util.Map<?, ?>) order.getItems()).size();
            }
            if (itemCount <= 0) itemCount = 1;
            tvItemsCount.setText(itemCount + " item" + (itemCount != 1 ? "s" : ""));

            // Total amount
            tvTotalAmount.setText(String.format(Locale.getDefault(), "LKR %.2f", order.getTotalAmountValue()));

            // Order status
            tvOrderStatus.setText(order.getStatusDisplay());
            setStatusColor(order.getOrderStatus());

            // Order date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
            tvOrderDate.setText(sdf.format(new Date(order.getCreatedAtValue())));

            // Pickup time
            if (order.getPickupTime() != null && !order.getPickupTime().isEmpty() && !"N/A".equalsIgnoreCase(order.getPickupTime())) {
                tvPickupTime.setText("Pickup " + order.getPickupTime());
                tvPickupTime.setVisibility(View.VISIBLE);
            } else {
                tvPickupTime.setVisibility(View.GONE);
            }

            boolean isActive = order.isActive();

            // Show QR button only for active orders
            if (isActive && (order.getQrCode() != null || order.getOrderId() != null)) {
                btnViewQR.setVisibility(View.VISIBLE);
                btnViewQR.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onViewQRClick(order);
                    }
                });
            } else {
                btnViewQR.setVisibility(View.GONE);
            }

            // Show cancel button for pending flows only
            if (isActive) {
                btnCancelOrder.setVisibility(View.VISIBLE);
                btnCancelOrder.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onCancelOrderClick(order);
                    }
                });
            } else {
                btnCancelOrder.setVisibility(View.GONE);
            }

            // Card click
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOrderClick(order);
                }
            });
        }

        private void setStatusColor(String status) {
            int color;
            if (status == null) {
                color = R.color.text_secondary;
            } else {
                switch (status) {
                    case "pending":
                    case "pending_pickup":
                        color = R.color.warning;
                        break;
                    case "confirmed":
                    case "ready":
                        color = R.color.primary;
                        break;
                    case "completed":
                        color = R.color.success;
                        break;
                    case "cancelled":
                        color = R.color.error;
                        break;
                    default:
                        color = R.color.text_secondary;
                }
            }
            tvOrderStatus.setTextColor(ContextCompat.getColor(context, color));
            statusIndicator.setBackgroundColor(ContextCompat.getColor(context, color));
        }
    }
}

