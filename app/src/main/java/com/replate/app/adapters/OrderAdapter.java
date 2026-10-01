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

import com.replate.app.utils.Money;
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
            if (orderId == null || orderId.isEmpty()) {
                orderId = "";
            } else {
                orderId = "#" + orderId.substring(Math.max(0, orderId.length() - 8)).toUpperCase(Locale.ROOT);
            }
            tvOrderId.setText(orderId);

            // Hotel name
            String hotelName = order.getHotelName();
            tvHotelName.setText(hotelName != null && !hotelName.trim().isEmpty()
                    ? hotelName : context.getString(R.string.hotel_name_fallback));

            // Items count
            int itemCount = 0;
            if (order.getItems() instanceof java.util.List) {
                itemCount = ((java.util.List<?>) order.getItems()).size();
            } else if (order.getItems() instanceof java.util.Map) {
                itemCount = ((java.util.Map<?, ?>) order.getItems()).size();
            }
            if (itemCount <= 0) itemCount = 1;
            tvItemsCount.setText(context.getResources().getQuantityString(R.plurals.order_items_count, itemCount, itemCount));

            // Total amount
            tvTotalAmount.setText(Money.format(order.getTotalAmountValue()));

            // Order status — include order type prefix for delivery orders
            String statusText = order.getStatusDisplay();
            if (order.isDelivery()) {
                statusText = context.getString(R.string.order_status_delivery_prefix, statusText);
            }
            tvOrderStatus.setText(statusText);
            setStatusColor(order.getOrderStatus());

            // Order date
            // Orders without a timestamp used to show 1 January 1970
            long createdAt = order.getCreatedAtValue();
            if (createdAt > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
                tvOrderDate.setText(sdf.format(new Date(createdAt)));
                tvOrderDate.setVisibility(View.VISIBLE);
            } else {
                tvOrderDate.setVisibility(View.GONE);
            }

            // Pickup time / delivery info
            if (order.isDelivery()) {
                String addr = order.getDeliveryAddress();
                if (addr != null && !addr.isEmpty() && !"Pickup at Hotel".equalsIgnoreCase(addr)) {
                    tvPickupTime.setText(context.getString(R.string.order_delivery_to, addr));
                } else {
                    tvPickupTime.setText(R.string.order_delivery_order);
                }
                tvPickupTime.setVisibility(View.VISIBLE);
            } else if (order.getPickupTime() != null && !order.getPickupTime().isEmpty() && !"N/A".equalsIgnoreCase(order.getPickupTime())) {
                tvPickupTime.setText(context.getString(R.string.order_pickup_at, order.getPickupTime()));
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
                    case "pending_delivery":
                        color = R.color.warning;
                        break;
                    case "confirmed":
                    case "preparing":
                    case "ready":
                        color = R.color.primary;
                        break;
                    case "out_for_delivery":
                        color = R.color.primary;
                        break;
                    case "completed":
                    case "delivered":
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

