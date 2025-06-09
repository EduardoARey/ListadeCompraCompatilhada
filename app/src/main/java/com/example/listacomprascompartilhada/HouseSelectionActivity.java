package com.example.listacomprascompartilhada;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HouseSelectionActivity extends AppCompatActivity {

    private RecyclerView recyclerViewHouses;
    private HouseAdapter houseAdapter;
    private List<House> houses;
    private FloatingActionButton fabCreateHouse, fabJoinHouse;
    private TextView tvEmptyState;

    private FirebaseAuth mAuth;
    private HouseManager houseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_house_selection);

        mAuth = FirebaseAuth.getInstance();
        houseManager = HouseManager.getInstance();

        // Verificar se usuário está logado
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadUserHouses();
    }

    private void initializeViews() {
        recyclerViewHouses = findViewById(R.id.recyclerViewHouses);
        fabCreateHouse = findViewById(R.id.fabCreateHouse);
        fabJoinHouse = findViewById(R.id.fabJoinHouse);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        houses = new ArrayList<>();
    }

    private void setupRecyclerView() {
        houseAdapter = new HouseAdapter(houses, new HouseAdapter.OnHouseClickListener() {
            @Override
            public void onHouseClick(House house) {
                openHouse(house);
            }

            @Override
            public void onHouseLeave(House house) {
                showLeaveHouseDialog(house);
            }

            @Override
            public void onHouseShare(House house) {
                shareHouseInvite(house);
            }

            @Override
            public void onHouseEdit(House house) {
                showEditNicknameDialog(house);
            }
        });

        recyclerViewHouses.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHouses.setAdapter(houseAdapter);
    }

    private void setupClickListeners() {
        fabCreateHouse.setOnClickListener(v -> showCreateHouseDialog());
        fabJoinHouse.setOnClickListener(v -> showJoinHouseDialog());
    }

    private void loadUserHouses() {
        houseManager.getUserHouses(new HouseManager.HouseListCallback() {
            @Override
            public void onSuccess(Map<String, House> houseMap) {
                houses.clear();
                houses.addAll(houseMap.values());
                houseAdapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(HouseSelectionActivity.this, error, Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    private void showCreateHouseDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_house, null);
        EditText etHouseName = dialogView.findViewById(R.id.etHouseName);
        EditText etNickname = dialogView.findViewById(R.id.etNickname);

        // Sugerir um apelido baseado no nome do usuário
        String suggestedNickname = getSuggestedNickname();
        etNickname.setText(suggestedNickname);

        new AlertDialog.Builder(this)
                .setTitle("Criar Nova Casa")
                .setView(dialogView)
                .setPositiveButton("Criar", (dialog, which) -> {
                    String houseName = etHouseName.getText().toString().trim();
                    String nickname = etNickname.getText().toString().trim();

                    if (houseName.isEmpty()) {
                        Toast.makeText(this, "Digite um nome para a casa", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (nickname.isEmpty()) {
                        Toast.makeText(this, "Digite um apelido", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createHouse(houseName, nickname);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showJoinHouseDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_house, null);
        EditText etInviteCode = dialogView.findViewById(R.id.etInviteCode);
        EditText etNickname = dialogView.findViewById(R.id.etNickname);

        // Sugerir um apelido baseado no nome do usuário
        String suggestedNickname = getSuggestedNickname();
        etNickname.setText(suggestedNickname);

        new AlertDialog.Builder(this)
                .setTitle("Entrar em uma Casa")
                .setView(dialogView)
                .setPositiveButton("Entrar", (dialog, which) -> {
                    String inviteCode = etInviteCode.getText().toString().trim().toUpperCase();
                    String nickname = etNickname.getText().toString().trim();

                    if (inviteCode.isEmpty()) {
                        Toast.makeText(this, "Digite o código de convite", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (nickname.isEmpty()) {
                        Toast.makeText(this, "Digite um apelido", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    joinHouse(inviteCode, nickname);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showEditNicknameDialog(House house) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_nickname, null);
        EditText etNickname = dialogView.findViewById(R.id.etNickname);

        // Pegar apelido atual do usuário nesta casa
        String currentUserId = mAuth.getCurrentUser().getUid();
        House.HouseMember currentMember = house.getMembers().get(currentUserId);
        if (currentMember != null) {
            etNickname.setText(currentMember.getName());
        }

        new AlertDialog.Builder(this)
                .setTitle("Editar Apelido")
                .setMessage("Como você quer aparecer nesta casa?")
                .setView(dialogView)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String newNickname = etNickname.getText().toString().trim();
                    if (!newNickname.isEmpty()) {
                        updateNickname(house.getId(), newNickname);
                    } else {
                        Toast.makeText(this, "Digite um apelido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showLeaveHouseDialog(House house) {
        new AlertDialog.Builder(this)
                .setTitle("Sair da Casa")
                .setMessage("Deseja realmente sair da casa '" + house.getName() + "'?")
                .setPositiveButton("Sair", (dialog, which) -> leaveHouse(house))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String getSuggestedNickname() {
        // Tentar diferentes fontes para sugerir um apelido
        if (mAuth.getCurrentUser() != null) {
            String displayName = mAuth.getCurrentUser().getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                // Pegar apenas o primeiro nome
                String firstName = displayName.split(" ")[0];
                return firstName;
            }

            String email = mAuth.getCurrentUser().getEmail();
            if (email != null) {
                String name = email.split("@")[0];
                name = name.replace(".", " ").replace("_", " ");
                // Capitalizar e pegar primeira palavra
                String[] words = name.split(" ");
                if (words.length > 0 && words[0].length() > 0) {
                    return Character.toUpperCase(words[0].charAt(0)) + words[0].substring(1).toLowerCase();
                }
            }
        }
        return "";
    }

    private void createHouse(String houseName, String nickname) {
        houseManager.createHouseWithNickname(houseName, nickname, new HouseManager.HouseCallback() {
            @Override
            public void onSuccess(House house) {
                Toast.makeText(HouseSelectionActivity.this, "Casa criada com sucesso!", Toast.LENGTH_SHORT).show();
                loadUserHouses();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(HouseSelectionActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void joinHouse(String inviteCode, String nickname) {
        houseManager.joinHouseWithNickname(inviteCode, nickname, new HouseManager.HouseCallback() {
            @Override
            public void onSuccess(House house) {
                Toast.makeText(HouseSelectionActivity.this, "Entrou na casa com sucesso!", Toast.LENGTH_SHORT).show();
                loadUserHouses();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(HouseSelectionActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateNickname(String houseId, String newNickname) {
        houseManager.updateUserNickname(houseId, newNickname, new HouseManager.HouseCallback() {
            @Override
            public void onSuccess(House house) {
                Toast.makeText(HouseSelectionActivity.this, "Apelido atualizado!", Toast.LENGTH_SHORT).show();
                loadUserHouses();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(HouseSelectionActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void leaveHouse(House house) {
        houseManager.leaveHouse(house.getId(), new HouseManager.HouseCallback() {
            @Override
            public void onSuccess(House house) {
                Toast.makeText(HouseSelectionActivity.this, "Saiu da casa com sucesso!", Toast.LENGTH_SHORT).show();
                loadUserHouses();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(HouseSelectionActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openHouse(House house) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("houseId", house.getId());
        intent.putExtra("houseName", house.getName());
        startActivity(intent);
    }

    private void shareHouseInvite(House house) {
        String shareText = "Convite para a casa '" + house.getName() + "'\n" +
                "Código de convite: " + house.getInviteCode() + "\n\n" +
                "Use este código no app Lista de Compras Compartilhada para entrar na nossa casa!";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Convite - " + house.getName());
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        startActivity(Intent.createChooser(shareIntent, "Compartilhar convite"));
    }

    private void updateEmptyState() {
        if (houses.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewHouses.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewHouses.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_house_selection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        // Parar notificações
        NotificationService.getInstance(this).stopAllMonitoring();

        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Iniciar monitoramento de notificações
        NotificationService.getInstance(this).startAllHousesMonitoring();
    }
}