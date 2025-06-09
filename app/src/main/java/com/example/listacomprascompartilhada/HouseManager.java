package com.example.listacomprascompartilhada;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class HouseManager {
    private static HouseManager instance;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    public interface HouseCallback {
        void onSuccess(House house);
        void onError(String error);
    }

    public interface HouseListCallback {
        void onSuccess(Map<String, House> houses);
        void onError(String error);
    }

    private HouseManager() {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
    }

    public static HouseManager getInstance() {
        if (instance == null) {
            instance = new HouseManager();
        }
        return instance;
    }

    // Criar uma nova casa
    public void createHouse(String houseName, HouseCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Usuário não autenticado");
            return;
        }

        String houseId = mDatabase.child("houses").push().getKey();
        if (houseId == null) {
            callback.onError("Erro ao gerar ID da casa");
            return;
        }

        String inviteCode = generateInviteCode();
        String userName = currentUser.getDisplayName() != null ?
                currentUser.getDisplayName() : "Usuário";

        House house = new House(houseName, currentUser.getUid(), userName, inviteCode);
        house.setId(houseId);

        // Adicionar o criador como membro
        House.HouseMember owner = new House.HouseMember(
                currentUser.getUid(),
                userName,
                currentUser.getEmail(),
                true
        );

        Map<String, House.HouseMember> members = new HashMap<>();
        members.put(currentUser.getUid(), owner);
        house.setMembers(members);

        // Salvar casa no Firebase
        mDatabase.child("houses").child(houseId).setValue(house)
                .addOnSuccessListener(aVoid -> {
                    // Adicionar referência da casa ao usuário
                    mDatabase.child("user_houses").child(currentUser.getUid()).child(houseId).setValue(true);

                    // Criar lista de compras padrão para a casa
                    createDefaultShoppingList(houseId);

                    callback.onSuccess(house);
                })
                .addOnFailureListener(e -> callback.onError("Erro ao criar casa: " + e.getMessage()));
    }

    // Entrar em uma casa usando código de convite
    public void joinHouse(String inviteCode, HouseCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Usuário não autenticado");
            return;
        }

        // Buscar casa pelo código de convite
        mDatabase.child("houses").orderByChild("inviteCode").equalTo(inviteCode)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError("Código de convite inválido");
                            return;
                        }

                        for (DataSnapshot houseSnapshot : snapshot.getChildren()) {
                            String houseId = houseSnapshot.getKey();
                            House house = houseSnapshot.getValue(House.class);

                            if (house != null && houseId != null) {
                                house.setId(houseId);

                                // Verificar se usuário já é membro
                                if (house.getMembers().containsKey(currentUser.getUid())) {
                                    callback.onError("Você já é membro desta casa");
                                    return;
                                }

                                // Adicionar usuário como membro
                                String userName = currentUser.getDisplayName() != null ?
                                        currentUser.getDisplayName() : "Usuário";

                                House.HouseMember newMember = new House.HouseMember(
                                        currentUser.getUid(),
                                        userName,
                                        currentUser.getEmail(),
                                        false
                                );

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("houses/" + houseId + "/members/" + currentUser.getUid(), newMember);
                                updates.put("houses/" + houseId + "/memberCount", house.getMemberCount() + 1);
                                updates.put("user_houses/" + currentUser.getUid() + "/" + houseId, true);

                                mDatabase.updateChildren(updates)
                                        .addOnSuccessListener(aVoid -> callback.onSuccess(house))
                                        .addOnFailureListener(e -> callback.onError("Erro ao entrar na casa: " + e.getMessage()));
                            }
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Erro ao buscar casa: " + error.getMessage());
                    }
                });
    }

    // Listar casas do usuário
    public void getUserHouses(HouseListCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Usuário não autenticado");
            return;
        }

        mDatabase.child("user_houses").child(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Map<String, House> houses = new HashMap<>();

                        if (!snapshot.exists()) {
                            callback.onSuccess(houses);
                            return;
                        }

                        long totalHouses = snapshot.getChildrenCount();
                        final long[] loadedHouses = {0};

                        for (DataSnapshot houseIdSnapshot : snapshot.getChildren()) {
                            String houseId = houseIdSnapshot.getKey();

                            mDatabase.child("houses").child(houseId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot houseSnapshot) {
                                            if (houseSnapshot.exists()) {
                                                House house = houseSnapshot.getValue(House.class);
                                                if (house != null) {
                                                    house.setId(houseId);
                                                    houses.put(houseId, house);
                                                }
                                            }

                                            loadedHouses[0]++;
                                            if (loadedHouses[0] == totalHouses) {
                                                callback.onSuccess(houses);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            loadedHouses[0]++;
                                            if (loadedHouses[0] == totalHouses) {
                                                callback.onSuccess(houses);
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Erro ao carregar casas: " + error.getMessage());
                    }
                });
    }

    // Sair de uma casa
    public void leaveHouse(String houseId, HouseCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Usuário não autenticado");
            return;
        }

        // Verificar se o usuário é o dono da casa
        mDatabase.child("houses").child(houseId).child("ownerId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String ownerId = snapshot.getValue(String.class);

                        if (currentUser.getUid().equals(ownerId)) {
                            callback.onError("O dono da casa não pode sair. Transfira a propriedade primeiro.");
                            return;
                        }

                        // Remover usuário da casa
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("houses/" + houseId + "/members/" + currentUser.getUid(), null);
                        updates.put("user_houses/" + currentUser.getUid() + "/" + houseId, null);

                        // Decrementar contador de membros
                        mDatabase.child("houses").child(houseId).child("memberCount")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot memberCountSnapshot) {
                                        Integer memberCount = memberCountSnapshot.getValue(Integer.class);
                                        if (memberCount != null && memberCount > 1) {
                                            updates.put("houses/" + houseId + "/memberCount", memberCount - 1);
                                        }

                                        mDatabase.updateChildren(updates)
                                                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                                .addOnFailureListener(e -> callback.onError("Erro ao sair da casa: " + e.getMessage()));
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        callback.onError("Erro ao sair da casa: " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Erro ao verificar propriedade da casa: " + error.getMessage());
                    }
                });
    }

    // Gerar código de convite único
    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }

        return code.toString();
    }

    // Criar lista de compras padrão para a casa
    private void createDefaultShoppingList(String houseId) {
        String listId = mDatabase.child("shopping_lists").push().getKey();
        if (listId != null) {
            Map<String, Object> listData = new HashMap<>();
            listData.put("name", "Lista de Compras");
            listData.put("houseId", houseId);
            listData.put("createdAt", System.currentTimeMillis());

            mDatabase.child("shopping_lists").child(listId).setValue(listData);
            mDatabase.child("house_shopping_lists").child(houseId).child("default").setValue(listId);
        }
    }
}