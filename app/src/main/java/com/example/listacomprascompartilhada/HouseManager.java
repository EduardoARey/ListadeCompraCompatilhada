package com.example.listacomprascompartilhada;

import android.util.Log;

import androidx.annotation.NonNull;

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
    private static final String TAG = "HouseManager";
    private static final String DATABASE_URL = "https://lista-de-compras-474d7-default-rtdb.firebaseio.com/";

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
        try {
            mDatabase = FirebaseDatabase.getInstance(DATABASE_URL).getReference();
            mAuth = FirebaseAuth.getInstance();
            Log.d(TAG, "HouseManager initialized with database URL: " + DATABASE_URL);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing HouseManager", e);
            mDatabase = FirebaseDatabase.getInstance().getReference();
            mAuth = FirebaseAuth.getInstance();
        }
    }

    public static HouseManager getInstance() {
        if (instance == null) {
            instance = new HouseManager();
        }
        return instance;
    }

    // Criar uma nova casa com apelido personalizado
    public void createHouseWithNickname(String houseName, String nickname, HouseCallback callback) {
        Log.d(TAG, "Creating house: " + houseName + " with nickname: " + nickname);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            callback.onError("Usuário não autenticado");
            return;
        }

        String houseId = mDatabase.child("houses").push().getKey();
        if (houseId == null) {
            Log.e(TAG, "Failed to generate house ID");
            callback.onError("Erro ao gerar ID da casa");
            return;
        }

        Log.d(TAG, "Generated house ID: " + houseId);
        String inviteCode = generateInviteCode();
        Log.d(TAG, "Generated invite code: " + inviteCode);

        House house = new House(houseName, currentUser.getUid(), nickname, inviteCode);
        house.setId(houseId);

        // Adicionar o criador como membro com o apelido escolhido
        House.HouseMember owner = new House.HouseMember(
                currentUser.getUid(),
                nickname, // Usar o apelido escolhido
                currentUser.getEmail(),
                true
        );

        Map<String, House.HouseMember> members = new HashMap<>();
        members.put(currentUser.getUid(), owner);
        house.setMembers(members);

        Log.d(TAG, "Saving house to Firebase...");

        // Salvar casa no Firebase
        mDatabase.child("houses").child(houseId).setValue(house)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "House saved successfully!");

                    // Adicionar referência da casa ao usuário
                    mDatabase.child("user_houses").child(currentUser.getUid()).child(houseId).setValue(true)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "User house reference added successfully!");

                                // Criar lista de compras padrão para a casa
                                createDefaultShoppingList(houseId);

                                callback.onSuccess(house);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error adding user house reference", e);
                                callback.onError("Erro ao vincular casa ao usuário: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving house", e);
                    callback.onError("Erro ao criar casa: " + e.getMessage());
                });
    }

    // Método original para compatibilidade
    public void createHouse(String houseName, HouseCallback callback) {
        // Usar nome extraído do email como apelido padrão
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String defaultNickname = extractNameFromEmail(currentUser.getEmail());
            createHouseWithNickname(houseName, defaultNickname, callback);
        } else {
            callback.onError("Usuário não autenticado");
        }
    }

    // Entrar em uma casa com apelido personalizado
    public void joinHouseWithNickname(String inviteCode, String nickname, HouseCallback callback) {
        Log.d(TAG, "Joining house with code: " + inviteCode + " and nickname: " + nickname);

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
                        Log.d(TAG, "Search result for invite code: " + snapshot.exists());

                        if (!snapshot.exists()) {
                            callback.onError("Código de convite inválido");
                            return;
                        }

                        for (DataSnapshot houseSnapshot : snapshot.getChildren()) {
                            String houseId = houseSnapshot.getKey();
                            House house = houseSnapshot.getValue(House.class);

                            Log.d(TAG, "Found house: " + houseId);

                            if (house != null && houseId != null) {
                                house.setId(houseId);

                                // Verificar se usuário já é membro
                                if (house.getMembers().containsKey(currentUser.getUid())) {
                                    callback.onError("Você já é membro desta casa");
                                    return;
                                }

                                House.HouseMember newMember = new House.HouseMember(
                                        currentUser.getUid(),
                                        nickname, // Usar o apelido escolhido
                                        currentUser.getEmail(),
                                        false
                                );

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("houses/" + houseId + "/members/" + currentUser.getUid(), newMember);
                                updates.put("houses/" + houseId + "/memberCount", house.getMemberCount() + 1);
                                updates.put("user_houses/" + currentUser.getUid() + "/" + houseId, true);

                                Log.d(TAG, "Adding user to house...");

                                mDatabase.updateChildren(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "User added to house successfully!");
                                            callback.onSuccess(house);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error adding user to house", e);
                                            callback.onError("Erro ao entrar na casa: " + e.getMessage());
                                        });
                            }
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error searching for house", error.toException());
                        callback.onError("Erro ao buscar casa: " + error.getMessage());
                    }
                });
    }

    // Método original para compatibilidade
    public void joinHouse(String inviteCode, HouseCallback callback) {
        // Usar nome extraído do email como apelido padrão
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String defaultNickname = extractNameFromEmail(currentUser.getEmail());
            joinHouseWithNickname(inviteCode, defaultNickname, callback);
        } else {
            callback.onError("Usuário não autenticado");
        }
    }

    // Atualizar apelido do usuário em uma casa específica
    public void updateUserNickname(String houseId, String newNickname, HouseCallback callback) {
        Log.d(TAG, "Updating nickname in house: " + houseId + " to: " + newNickname);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Usuário não autenticado");
            return;
        }

        // Verificar se o usuário é membro da casa
        mDatabase.child("houses").child(houseId).child("members").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            callback.onError("Você não é membro desta casa");
                            return;
                        }

                        // Atualizar apenas o nome do membro
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("houses/" + houseId + "/members/" + currentUser.getUid() + "/name", newNickname);

                        // Se for o dono, atualizar também o ownerName
                        mDatabase.child("houses").child(houseId).child("ownerId")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot ownerSnapshot) {
                                        String ownerId = ownerSnapshot.getValue(String.class);
                                        if (currentUser.getUid().equals(ownerId)) {
                                            updates.put("houses/" + houseId + "/ownerName", newNickname);
                                        }

                                        mDatabase.updateChildren(updates)
                                                .addOnSuccessListener(aVoid -> {
                                                    Log.d(TAG, "Nickname updated successfully!");
                                                    // Buscar a casa atualizada para retornar
                                                    mDatabase.child("houses").child(houseId)
                                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                                @Override
                                                                public void onDataChange(@NonNull DataSnapshot houseSnapshot) {
                                                                    House house = houseSnapshot.getValue(House.class);
                                                                    if (house != null) {
                                                                        house.setId(houseId);
                                                                        callback.onSuccess(house);
                                                                    } else {
                                                                        callback.onSuccess(null);
                                                                    }
                                                                }

                                                                @Override
                                                                public void onCancelled(@NonNull DatabaseError error) {
                                                                    callback.onSuccess(null);
                                                                }
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Error updating nickname", e);
                                                    callback.onError("Erro ao atualizar apelido: " + e.getMessage());
                                                });
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        // Continuar mesmo se não conseguir verificar o dono
                                        mDatabase.updateChildren(updates)
                                                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                                .addOnFailureListener(e -> callback.onError("Erro ao atualizar apelido: " + e.getMessage()));
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error checking membership", error.toException());
                        callback.onError("Erro ao verificar membro: " + error.getMessage());
                    }
                });
    }

    // Listar casas do usuário
    public void getUserHouses(HouseListCallback callback) {
        Log.d(TAG, "Loading user houses...");

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Usuário não autenticado");
            return;
        }

        mDatabase.child("user_houses").child(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "User houses snapshot exists: " + snapshot.exists());
                        Log.d(TAG, "User houses count: " + snapshot.getChildrenCount());

                        Map<String, House> houses = new HashMap<>();

                        if (!snapshot.exists()) {
                            callback.onSuccess(houses);
                            return;
                        }

                        long totalHouses = snapshot.getChildrenCount();
                        final long[] loadedHouses = {0};

                        for (DataSnapshot houseIdSnapshot : snapshot.getChildren()) {
                            String houseId = houseIdSnapshot.getKey();
                            Log.d(TAG, "Loading house: " + houseId);

                            mDatabase.child("houses").child(houseId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot houseSnapshot) {
                                            if (houseSnapshot.exists()) {
                                                House house = houseSnapshot.getValue(House.class);
                                                if (house != null) {
                                                    house.setId(houseId);
                                                    houses.put(houseId, house);
                                                    Log.d(TAG, "House loaded: " + house.getName());
                                                }
                                            } else {
                                                Log.w(TAG, "House not found: " + houseId);
                                            }

                                            loadedHouses[0]++;
                                            if (loadedHouses[0] == totalHouses) {
                                                Log.d(TAG, "All houses loaded, total: " + houses.size());
                                                callback.onSuccess(houses);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Log.e(TAG, "Error loading house: " + houseId, error.toException());
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
                        Log.e(TAG, "Error loading user houses", error.toException());
                        callback.onError("Erro ao carregar casas: " + error.getMessage());
                    }
                });
    }

    // Sair de uma casa
    public void leaveHouse(String houseId, HouseCallback callback) {
        Log.d(TAG, "Leaving house: " + houseId);

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
                                                .addOnSuccessListener(aVoid -> {
                                                    Log.d(TAG, "Left house successfully");
                                                    callback.onSuccess(null);
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Error leaving house", e);
                                                    callback.onError("Erro ao sair da casa: " + e.getMessage());
                                                });
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Log.e(TAG, "Error getting member count", error.toException());
                                        callback.onError("Erro ao sair da casa: " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error checking owner", error.toException());
                        callback.onError("Erro ao verificar propriedade da casa: " + error.getMessage());
                    }
                });
    }

    // Extrair nome do email
    private String extractNameFromEmail(String email) {
        if (email == null) return "Usuário";

        String name = email.split("@")[0];
        name = name.replace(".", " ").replace("_", " ");

        // Capitalizar primeira letra de cada palavra
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
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
        Log.d(TAG, "Creating default shopping list for house: " + houseId);

        String listId = mDatabase.child("shopping_lists").push().getKey();
        if (listId != null) {
            Map<String, Object> listData = new HashMap<>();
            listData.put("name", "Lista de Compras");
            listData.put("houseId", houseId);
            listData.put("createdAt", System.currentTimeMillis());

            mDatabase.child("shopping_lists").child(listId).setValue(listData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Shopping list created successfully: " + listId);
                        mDatabase.child("house_shopping_lists").child(houseId).child("default").setValue(listId);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error creating shopping list", e));
        }
    }
}