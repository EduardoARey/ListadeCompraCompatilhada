package com.example.listacomprascompartilhada;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private MaterialButton btnGoogleSignIn;
    private TextView tvSwitchMode, tvForgotPassword;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private GoogleSignInClient mGoogleSignInClient;

    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();


        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified()) {
                startHouseSelectionActivity();
            } else {
                showEmailVerificationDialog(currentUser);
            }
            return;
        }

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvSwitchMode = findViewById(R.id.tvSwitchMode);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBar = findViewById(R.id.progressBar);

        updateUI();
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            if (isLoginMode) {
                loginUser();
            } else {
                registerUser();
            }
        });

        btnRegister.setOnClickListener(v -> {
            if (isLoginMode) {
                registerUser();
            } else {
                loginUser();
            }
        });

        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());

        tvSwitchMode.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateUI();
        });

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void updateUI() {
        if (isLoginMode) {
            btnLogin.setText("Entrar");
            btnRegister.setText("Criar Conta");
            tvSwitchMode.setText("Não tem conta? Clique aqui para criar");
            tvForgotPassword.setVisibility(View.VISIBLE);
        } else {
            btnLogin.setText("Criar Conta");
            btnRegister.setText("Entrar");
            tvSwitchMode.setText("Já tem conta? Clique aqui para entrar");
            tvForgotPassword.setVisibility(View.GONE);
        }
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Erro no login com Google", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        showProgressBar(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    showProgressBar(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {

                            saveUserToDatabase(user, user.getDisplayName());
                            Toast.makeText(this, "Login com Google realizado com sucesso!",
                                    Toast.LENGTH_SHORT).show();
                            startHouseSelectionActivity();
                        }
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(this, "Erro na autenticação: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!validateInput(email, password)) {
            return;
        }

        showProgressBar(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showProgressBar(false);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {
                            if (user.isEmailVerified()) {
                                Toast.makeText(this, "Login realizado com sucesso!",
                                        Toast.LENGTH_SHORT).show();
                                updateUserData(user);
                                startHouseSelectionActivity();
                            } else {
                                showEmailVerificationDialog(user);
                            }
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(this, "Falha no login: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!validateInput(email, password)) {
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("A senha deve ter pelo menos 6 caracteres");
            return;
        }

        showProgressBar(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showProgressBar(false);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {
                            String displayName = extractNameFromEmail(email);

                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(displayName)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        saveUserToDatabase(user, displayName);
                                        sendEmailVerification(user);
                                    });
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(this, "Falha no registro: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendEmailVerification(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Conta criada! Verifique seu email para ativar a conta.",
                                Toast.LENGTH_LONG).show();
                        showEmailVerificationDialog(user);
                    } else {
                        Toast.makeText(this, "Erro ao enviar email de verificação",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showEmailVerificationDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Verificação de Email")
                .setMessage("Para usar o aplicativo, você precisa verificar seu email. " +
                        "Verifique sua caixa de entrada e clique no link de verificação.\n\n" +
                        "Email: " + user.getEmail())
                .setPositiveButton("Reenviar Email", (dialog, which) -> {
                    user.sendEmailVerification()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(this, "Email de verificação enviado!",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Erro ao enviar email",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNeutralButton("Verificar Agora", (dialog, which) -> {

                    user.reload().addOnCompleteListener(task -> {
                        if (user.isEmailVerified()) {
                            Toast.makeText(this, "Email verificado com sucesso!",
                                    Toast.LENGTH_SHORT).show();
                            startHouseSelectionActivity();
                        } else {
                            Toast.makeText(this, "Email ainda não foi verificado. " +
                                    "Verifique sua caixa de entrada.", Toast.LENGTH_LONG).show();
                            showEmailVerificationDialog(user);
                        }
                    });
                })
                .setNegativeButton("Sair", (dialog, which) -> {
                    mAuth.signOut();
                    mGoogleSignInClient.signOut();
                })
                .setCancelable(false)
                .show();
    }

    private void showForgotPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        EditText etResetEmail = dialogView.findViewById(R.id.etResetEmail);

        new AlertDialog.Builder(this)
                .setTitle("Recuperar Senha")
                .setView(dialogView)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String email = etResetEmail.getText().toString().trim();
                    if (email.isEmpty()) {
                        Toast.makeText(this, "Digite um email válido", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    mAuth.sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(this, "Email de recuperação enviado para " + email,
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Erro ao enviar email de recuperação",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String extractNameFromEmail(String email) {
        String name = email.split("@")[0];
        name = name.replace(".", " ").replace("_", " ");

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

    private void saveUserToDatabase(FirebaseUser user, String displayName) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("displayName", displayName != null ? displayName : extractNameFromEmail(user.getEmail()));
        userData.put("emailVerified", user.isEmailVerified());
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("lastLogin", System.currentTimeMillis());

        mDatabase.child("users").child(user.getUid()).setValue(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User data saved successfully"))
                .addOnFailureListener(e -> Log.w(TAG, "Error saving user data", e));
    }

    private void updateUserData(FirebaseUser user) {
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = extractNameFromEmail(user.getEmail());

            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build();

            user.updateProfile(profileUpdates);
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("displayName", displayName);
        userData.put("emailVerified", user.isEmailVerified());
        userData.put("lastLogin", System.currentTimeMillis());

        mDatabase.child("users").child(user.getUid()).updateChildren(userData);
    }

    private boolean validateInput(String email, String password) {
        if (email.isEmpty()) {
            etEmail.setError("Email é obrigatório");
            etEmail.requestFocus();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Digite um email válido");
            etEmail.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            etPassword.setError("Senha é obrigatória");
            etPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        btnRegister.setEnabled(!show);
        btnGoogleSignIn.setEnabled(!show);
    }

    private void startHouseSelectionActivity() {
        Intent intent = new Intent(this, HouseSelectionActivity.class);
        startActivity(intent);
        finish();
    }
}