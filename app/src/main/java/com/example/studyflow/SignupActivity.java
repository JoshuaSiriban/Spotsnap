package com.example.studyflow;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignupActivity extends AppCompatActivity {

    private EditText usernameInput;
    private EditText fullNameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private Button signupButton;
    private TextView formError; // general errors (no internet, ...)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        usernameInput = findViewById(R.id.usernameInput);
        fullNameInput = findViewById(R.id.fullNameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        signupButton = findViewById(R.id.signupButton);
        formError = findViewById(R.id.formError);
        TextView signupTv = findViewById(R.id.signupTv);

        signupTv.setOnClickListener(v -> finish()); // back to the login screen
        signupButton.setOnClickListener(v -> attemptSignup());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void attemptSignup() {
        hideFormError();
        String username = usernameInput.getText().toString().trim();
        String fullName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        String usernameError = AuthRepository.validateUsername(username);
        String emailError = AuthRepository.validateEmail(email);
        String passwordError = AuthRepository.validateNewPassword(password);

        // Mark every wrong field, and put the cursor in the first one.
        if (usernameError != null) usernameInput.setError(usernameError);
        if (emailError != null) emailInput.setError(emailError);
        if (passwordError != null) passwordInput.setError(passwordError);
        if (usernameError != null) {
            usernameInput.requestFocus();
            return;
        }
        if (emailError != null) {
            emailInput.requestFocus();
            return;
        }
        if (passwordError != null) {
            passwordInput.requestFocus();
            return;
        }
        if (fullName.isEmpty()) {
            fullName = username; // full name is optional
        }

        setBusy(true);
        AuthRepository.signUp(this, username, fullName, email, password, new AuthRepository.FormCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(SignupActivity.this, "Account created!", Toast.LENGTH_SHORT).show();
                // The new user is already logged in, so go straight to the map.
                Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(AuthRepository.Field field, String message) {
                setBusy(false);
                switch (field) {
                    case USERNAME:
                        usernameInput.setError(message);
                        usernameInput.requestFocus();
                        break;
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
        signupButton.setEnabled(!busy);
        signupButton.setText(busy ? "Creating account..." : "Signup");
        usernameInput.setEnabled(!busy);
        fullNameInput.setEnabled(!busy);
        emailInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);
    }
}