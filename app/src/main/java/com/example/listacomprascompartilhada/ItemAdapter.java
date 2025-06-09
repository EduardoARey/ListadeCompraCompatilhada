package com.example.listacomprascompartilhada;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {

    private List<ShoppingItem> items;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemChecked(ShoppingItem item, int position);
        void onItemEdit(ShoppingItem item, int position);
        void onItemDelete(ShoppingItem item, int position);
    }

    public ItemAdapter(List<ShoppingItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shopping_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShoppingItem item = items.get(position);

        holder.checkBox.setChecked(item.isCompleted());
        holder.tvItemName.setText(item.getName());
        holder.tvItemQuantity.setText(item.getQuantity());
        holder.tvAddedBy.setText("Adicionado por: " + item.getAddedBy());

        // Formatar data
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvCreatedAt.setText(dateFormat.format(new Date(item.getCreatedAt())));

        // Aplicar estilo de item completado
        if (item.isCompleted()) {
            holder.tvItemName.setPaintFlags(holder.tvItemName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvItemName.setAlpha(0.6f);
            holder.tvItemQuantity.setAlpha(0.6f);
        } else {
            holder.tvItemName.setPaintFlags(holder.tvItemName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvItemName.setAlpha(1.0f);
            holder.tvItemQuantity.setAlpha(1.0f);
        }

        // Configurar listeners
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onItemChecked(item, position);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemEdit(item, position);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemDelete(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView tvItemName;
        TextView tvItemQuantity;
        TextView tvAddedBy;
        TextView tvCreatedAt;
        ImageButton btnEdit;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBox);
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvItemQuantity = itemView.findViewById(R.id.tvItemQuantity);
            tvAddedBy = itemView.findViewById(R.id.tvAddedBy);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}