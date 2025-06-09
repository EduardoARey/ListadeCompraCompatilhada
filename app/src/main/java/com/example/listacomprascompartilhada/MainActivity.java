package com.example.listacomprascompartilhada;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewItems;
    private ItemAdapter itemAdapter;
    private List<ShoppingItem> shoppingItems;
    private FloatingActionButton fabAddItem;
    private TextView tvListName, tvEmptyState, tvHouseName;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String currentListId;
    private String currentUserId;
    private String currentHouseId;
    private String currentHouseName;
    private NotificationService notificationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        notificationService = NotificationService.getInstance(this);

        // Verificar se usuário está logado
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = currentUser.getUid();

        // Verificar se veio de uma casa específica
        Intent intent = getIntent();
        currentHouseId = intent.getStringExtra("houseId");
        currentHouseName = intent.getStringExtra("houseName");

        if (currentHouseId == null) {
            // Se não veio de uma casa, voltar para seleção de casas
            startActivity(new Intent(this, HouseSelectionActivity.class));
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();

        // Carregar lista da casa
        loadHouseShoppingList();

        // Iniciar monitoramento de notificações para esta casa
        notificationService.startHouseMonitoring(currentHouseId);
    }

    private void initializeViews() {
        recyclerViewItems = findViewById(R.id.recyclerViewItems);
        fabAddItem = findViewById(R.id.fabAddItem);
        tvListName = findViewById(R.id.tvListName);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvHouseName = findViewById(R.id.tvHouseName);

        // Definir nome da casa
        if (tvHouseName != null) {
            tvHouseName.setText(currentHouseName != null ? currentHouseName : "Casa");
        }

        shoppingItems = new ArrayList<>();
    }

    private void setupRecyclerView() {
        itemAdapter = new ItemAdapter(shoppingItems, new ItemAdapter.OnItemClickListener() {
            @Override
            public void onItemChecked(ShoppingItem item, int position) {
                toggleItemCompleted(item);
            }

            @Override
            public void onItemEdit(ShoppingItem item, int position) {
                showEditItemDialog(item);
            }

            @Override
            public void onItemDelete(ShoppingItem item, int position) {
                deleteItem(item);
            }
        });

        recyclerViewItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewItems.setAdapter(itemAdapter);
    }

    private void setupClickListeners() {
        fabAddItem.setOnClickListener(v -> showAddItemDialog());
    }

    private void loadHouseShoppingList() {
        // Buscar lista padrão da casa
        mDatabase.child("house_shopping_lists").child(currentHouseId).child("default")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentListId = snapshot.getValue(String.class);
                            loadShoppingList();
                        } else {
                            createHouseShoppingList();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Erro ao carregar lista", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createHouseShoppingList() {
        String listId = mDatabase.child("shopping_lists").push().getKey();
        if (listId != null) {
            Map<String, Object> listData = new HashMap<>();
            listData.put("name", "Lista de Compras");
            listData.put("houseId", currentHouseId);
            listData.put("createdAt", System.currentTimeMillis());

            mDatabase.child("shopping_lists").child(listId).setValue(listData)
                    .addOnSuccessListener(aVoid -> {
                        // Definir como lista padrão da casa
                        mDatabase.child("house_shopping_lists").child(currentHouseId).child("default").setValue(listId);
                        currentListId = listId;
                        loadShoppingList();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this, "Erro ao criar lista", Toast.LENGTH_SHORT).show());
        }
    }

    private void loadShoppingList() {
        if (currentListId == null) return;

        // Carregar informações da lista
        mDatabase.child("shopping_lists").child(currentListId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String listName = snapshot.child("name").getValue(String.class);
                            tvListName.setText(listName != null ? listName : "Lista de Compras");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });

        // Carregar itens da lista em tempo real
        mDatabase.child("shopping_items").child(currentListId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        shoppingItems.clear();
                        for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                            ShoppingItem item = itemSnapshot.getValue(ShoppingItem.class);
                            if (item != null) {
                                item.setId(itemSnapshot.getKey());
                                shoppingItems.add(item);
                            }
                        }
                        itemAdapter.notifyDataSetChanged();
                        updateEmptyState();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Erro ao carregar itens", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showAddItemDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_item, null);
        EditText etItemName = dialogView.findViewById(R.id.etItemName);
        EditText etItemQuantity = dialogView.findViewById(R.id.etItemQuantity);

        new AlertDialog.Builder(this)
                .setTitle("Adicionar Item")
                .setView(dialogView)
                .setPositiveButton("Adicionar", (dialog, which) -> {
                    String name = etItemName.getText().toString().trim();
                    String quantity = etItemQuantity.getText().toString().trim();

                    if (!name.isEmpty()) {
                        addItem(name, quantity.isEmpty() ? "1" : quantity);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showEditItemDialog(ShoppingItem item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_item, null);
        EditText etItemName = dialogView.findViewById(R.id.etItemName);
        EditText etItemQuantity = dialogView.findViewById(R.id.etItemQuantity);

        etItemName.setText(item.getName());
        etItemQuantity.setText(item.getQuantity());

        new AlertDialog.Builder(this)
                .setTitle("Editar Item")
                .setView(dialogView)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String name = etItemName.getText().toString().trim();
                    String quantity = etItemQuantity.getText().toString().trim();

                    if (!name.isEmpty()) {
                        updateItem(item.getId(), name, quantity.isEmpty() ? "1" : quantity);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addItem(String name, String quantity) {
        if (currentListId == null) return;

        String itemId = mDatabase.child("shopping_items").child(currentListId).push().getKey();
        if (itemId != null) {
            ShoppingItem item = new ShoppingItem(name, quantity, false, currentUserId, System.currentTimeMillis());

            mDatabase.child("shopping_items").child(currentListId).child(itemId).setValue(item)
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this, "Erro ao adicionar item", Toast.LENGTH_SHORT).show());
        }
    }

    private void updateItem(String itemId, String name, String quantity) {
        if (currentListId == null || itemId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("quantity", quantity);
        updates.put("lastModified", System.currentTimeMillis());

        mDatabase.child("shopping_items").child(currentListId).child(itemId).updateChildren(updates)
                .addOnFailureListener(e ->
                        Toast.makeText(MainActivity.this, "Erro ao atualizar item", Toast.LENGTH_SHORT).show());
    }

    private void toggleItemCompleted(ShoppingItem item) {
        if (currentListId == null || item.getId() == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("completed", !item.isCompleted());
        updates.put("lastModified", System.currentTimeMillis());

        mDatabase.child("shopping_items").child(currentListId).child(item.getId()).updateChildren(updates)
                .addOnFailureListener(e ->
                        Toast.makeText(MainActivity.this, "Erro ao atualizar item", Toast.LENGTH_SHORT).show());
    }

    private void deleteItem(ShoppingItem item) {
        if (currentListId == null || item.getId() == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Excluir Item")
                .setMessage("Deseja realmente excluir '" + item.getName() + "'?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    mDatabase.child("shopping_items").child(currentListId).child(item.getId()).removeValue()
                            .addOnFailureListener(e ->
                                    Toast.makeText(MainActivity.this, "Erro ao excluir item", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void updateEmptyState() {
        if (shoppingItems.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewItems.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewItems.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_share) {
            shareHouse();
            return true;
        } else if (id == R.id.action_house_list) {
            goToHouseSelection();
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareHouse() {
        // Buscar informações da casa para compartilhar
        mDatabase.child("houses").child(currentHouseId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            House house = snapshot.getValue(House.class);
                            if (house != null) {
                                String shareText = "Convite para a casa '" + house.getName() + "'\n" +
                                        "Código de convite: " + house.getInviteCode() + "\n\n" +
                                        "Use este código no app Lista de Compras Compartilhada para entrar na nossa casa!";

                                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                shareIntent.setType("text/plain");
                                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Convite - " + house.getName());
                                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

                                startActivity(Intent.createChooser(shareIntent, "Compartilhar convite"));
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Erro ao carregar informações da casa", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToHouseSelection() {
        startActivity(new Intent(this, HouseSelectionActivity.class));
        finish();
    }

    private void logout() {
        // Parar notificações
        notificationService.stopAllMonitoring();

        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentHouseId != null) {
            // Parar monitoramento desta casa específica
            notificationService.stopHouseMonitoring(currentHouseId);
        }
    }
}