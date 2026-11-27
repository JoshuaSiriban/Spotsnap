package com.example.studyflow;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView formError; // general errors (no internet, too many attempts, ...)

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
        formError = findViewById(R.id.formError);
        TextView createTv = findViewById(R.id.createTv);
        TextView forgotTv = findViewById(R.id.forgotTv);

        createTv.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        forgotTv.setOnClickListener(v ->
                AccountDialogs.forgotPassword(this, emailInput.getText().toString().trim()));
        loginButton.setOnClickListener(v -> attemptLogin());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void attemptLogin() {
        hideFormError();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        String emailError = AuthRepository.validateEmail(email);
        String passwordError = password.isEmpty() ? "Enter your password" : null;

        if (emailError != null) emailInput.setError(emailError);
        if (passwordError != null) passwordInput.setError(passwordError);
        if (emailError != null) {
            emailInput.requestFocus();
            return;
        }
        if (passwordError != null) {
            passwordInput.requestFocus();
            return;
        }

        setBusy(true);
        AuthRepository.logIn(this, email, password, new AuthRepository.FormCallback() {
            @Override
            public void onSuccess() {
                openMain();
            }

            @Override
            public void onError(AuthRepository.Field field, String message) {
                setBusy(false);
                switch (field) {
                    case EMAIL:
                        emailInput.setError(message);
                        emailInput.requestFocus();
                        break;
                    case PASSWORD:
                        passwordInput.setError(message);
                        passwordInput.requestFocus();
                        break;
                    default:
                        showFormError(message);
                }
            }
        });
    }

    private void showFormError(String message) {
        formError.setText(message);
        formError.setVisibility(View.VISIBLE);
    }

    private void hideFormError() {
        formError.setVisibility(View.GONE);
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