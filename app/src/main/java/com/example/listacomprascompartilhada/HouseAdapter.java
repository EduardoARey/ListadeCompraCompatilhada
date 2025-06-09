package com.example.listacomprascompartilhada;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HouseAdapter extends RecyclerView.Adapter<HouseAdapter.ViewHolder> {

    private List<House> houses;
    private OnHouseClickListener listener;
    private String currentUserId;

    public interface OnHouseClickListener {
        void onHouseClick(House house);
        void onHouseLeave(House house);
        void onHouseShare(House house);
        void onHouseEdit(House house);
    }

    public HouseAdapter(List<House> houses, OnHouseClickListener listener) {
        this.houses = houses;
        this.listener = listener;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_house, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        House house = houses.get(position);

        holder.tvHouseName.setText(house.getName());
        holder.tvOwnerName.setText("Dono: " + house.getOwnerName());
        holder.tvMemberCount.setText(house.getMemberCount() + " membro(s)");
        holder.tvInviteCode.setText("Código: " + house.getInviteCode());

        // Mostrar o apelido do usuário atual nesta casa
        House.HouseMember currentMember = house.getMembers().get(currentUserId);
        if (currentMember != null) {
            holder.tvMyNickname.setText("Meu apelido: " + currentMember.getName());
            holder.tvMyNickname.setVisibility(View.VISIBLE);
        } else {
            holder.tvMyNickname.setVisibility(View.GONE);
        }

        // Formatar data de criação
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        holder.tvCreatedAt.setText("Criada em: " + dateFormat.format(new Date(house.getCreatedAt())));

        // Verificar se é o dono da casa
        boolean isOwner = house.getOwnerId().equals(currentUserId);
        holder.tvOwnerBadge.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        // Configurar botão de sair (não mostrar para o dono)
        holder.btnLeaveHouse.setVisibility(isOwner ? View.GONE : View.VISIBLE);

        // Configurar listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHouseClick(house);
            }
        });

        holder.btnLeaveHouse.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHouseLeave(house);
            }
        });

        holder.btnShareHouse.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHouseShare(house);
            }
        });

        holder.btnEditNickname.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHouseEdit(house);
            }
        });
    }

    @Override
    public int getItemCount() {
        return houses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvHouseName;
        TextView tvOwnerName;
        TextView tvMemberCount;
        TextView tvInviteCode;
        TextView tvCreatedAt;
        TextView tvOwnerBadge;
        TextView tvMyNickname;
        ImageButton btnLeaveHouse;
        ImageButton btnShareHouse;
        ImageButton btnEditNickname;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHouseName = itemView.findViewById(R.id.tvHouseName);
            tvOwnerName = itemView.findViewById(R.id.tvOwnerName);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            tvInviteCode = itemView.findViewById(R.id.tvInviteCode);
            tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
            tvOwnerBadge = itemView.findViewById(R.id.tvOwnerBadge);
            tvMyNickname = itemView.findViewById(R.id.tvMyNickname);
            btnLeaveHouse = itemView.findViewById(R.id.btnLeaveHouse);
            btnShareHouse = itemView.findViewById(R.id.btnShareHouse);
            btnEditNickname = itemView.findViewById(R.id.btnEditNickname);
        }
    }
}