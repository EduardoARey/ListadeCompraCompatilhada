package com.example.listacomprascompartilhada;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class NotificationService {
    private static final String CHANNEL_ID = "shopping_list_notifications";
    private static final String CHANNEL_NAME = "Lista de Compras";
    private static final String CHANNEL_DESC = "Notificações da lista de compras compartilhada";

    private static NotificationService instance;
    private Context context;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private NotificationManager notificationManager;
    private Map<String, ChildEventListener> activeListeners;
    private Map<String, Long> lastItemTimestamps;

    private NotificationService(Context context) {
        this.context = context.getApplicationContext();
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.mAuth = FirebaseAuth.getInstance();
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.activeListeners = new HashMap<>();
        this.lastItemTimestamps = new HashMap<>();

        createNotificationChannel();
    }

    public static NotificationService getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationService(context);
        }
        return instance;
    }

    // Criar canal de notificação
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Iniciar monitoramento de uma casa
    public void startHouseMonitoring(String houseId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        // Buscar a lista de compras da casa
        mDatabase.child("house_shopping_lists").child(houseId).child("default")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String listId = snapshot.getValue(String.class);
                        if (listId != null) {
                            startListMonitoring(listId, houseId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
    }

    // Monitorar mudanças na lista de compras
    private void startListMonitoring(String listId, String houseId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        // Remover listener anterior se existir
        stopListMonitoring(listId);

        ChildEventListener listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                ShoppingItem item = snapshot.getValue(ShoppingItem.class);
                if (item != null && !item.getAddedBy().equals(currentUser.getUid())) {
                    // Verificar se é uma adição recente (últimos 10 segundos)
                    long currentTime = System.currentTimeMillis();
                    String itemKey = listId + "_" + snapshot.getKey();

                    if (currentTime - item.getCreatedAt() < 10000) { // 10 segundos
                        getUserName(item.getAddedBy(), userName ->
                                showNotification(
                                        "Item Adicionado",
                                        userName + " adicionou " + item.getName() + " (" + item.getQuantity() + ")",
                                        houseId
                                )
                        );
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                ShoppingItem item = snapshot.getValue(ShoppingItem.class);
                if (item != null && !item.getAddedBy().equals(currentUser.getUid())) {
                    String itemKey = listId + "_" + snapshot.getKey();
                    Long lastTimestamp = lastItemTimestamps.get(itemKey);

                    // Verificar se é uma mudança recente
                    if (lastTimestamp == null || item.getLastModified() > lastTimestamp) {
                        lastItemTimestamps.put(itemKey, item.getLastModified());

                        String action = item.isCompleted() ? "marcou como concluído" : "editou";
                        getUserName(item.getAddedBy(), userName ->
                                showNotification(
                                        "Item Atualizado",
                                        userName + " " + action + " " + item.getName(),
                                        houseId
                                )
                        );
                    }
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                ShoppingItem item = snapshot.getValue(ShoppingItem.class);
                if (item != null && !item.getAddedBy().equals(currentUser.getUid())) {
                    getUserName(item.getAddedBy(), userName ->
                            showNotification(
                                    "Item Removido",
                                    userName + " removeu " + item.getName(),
                                    houseId
                            )
                    );
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
                // Não usado para listas de compras
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        };

        mDatabase.child("shopping_items").child(listId).addChildEventListener(listener);
        activeListeners.put(listId, listener);
    }

    // Parar monitoramento de uma lista
    public void stopListMonitoring(String listId) {
        ChildEventListener listener = activeListeners.remove(listId);
        if (listener != null) {
            mDatabase.child("shopping_items").child(listId).removeEventListener(listener);
        }
    }

    // Parar monitoramento de uma casa
    public void stopHouseMonitoring(String houseId) {
        mDatabase.child("house_shopping_lists").child(houseId).child("default")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String listId = snapshot.getValue(String.class);
                        if (listId != null) {
                            stopListMonitoring(listId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
    }

    // Obter nome do usuário
    private void getUserName(String userId, UserNameCallback callback) {
        mDatabase.child("users").child(userId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        callback.onUserName(name != null ? name : "Usuário");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onUserName("Usuário");
                    }
                });
    }

    // Mostrar notificação
    private void showNotification(String title, String message, String houseId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("houseId", houseId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shopping_cart_24)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // Iniciar monitoramento de todas as casas do usuário
    public void startAllHousesMonitoring() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        HouseManager.getInstance().getUserHouses(new HouseManager.HouseListCallback() {
            @Override
            public void onSuccess(Map<String, House> houses) {
                for (String houseId : houses.keySet()) {
                    startHouseMonitoring(houseId);
                }
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    // Parar monitoramento de todas as casas
    public void stopAllMonitoring() {
        for (String listId : activeListeners.keySet()) {
            stopListMonitoring(listId);
        }
        activeListeners.clear();
        lastItemTimestamps.clear();
    }

    // Interface para callback de nome de usuário
    private interface UserNameCallback {
        void onUserName(String name);
    }
}