package com.example.studyflow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Already logged in from a previous session: skip straight to the map.
        if (UserSession.isLoggedIn()) {
            openMain();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        TextView createTv = findViewById(R.id.createTv);

        createTv.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        loginButton.setOnClickListener(v -> attemptLogin());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Enter your password");
            return;
        }

        setBusy(true);
        AuthRepository.logIn(this, email, password, new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                openMain();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        loginButton.setText(busy ? "Logging in..." : "Login");
        emailInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}