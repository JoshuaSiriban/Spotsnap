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

public class SignupActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 6; // Firebase Authentication's minimum

    private EditText usernameInput;
    private EditText fullNameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private Button signupButton;

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
        String username = usernameInput.getText().toString().trim();
        String fullName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        String usernameError = AuthRepository.validateUsername(username);
        if (usernameError != null) {
            usernameInput.setError(usernameError);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordInput.setError("Use at least " + MIN_PASSWORD_LENGTH + " characters");
            return;
        }
        if (fullName.isEmpty()) {
            fullName = username; // full name is optional
        }

        setBusy(true);
        AuthRepository.signUp(this, username, fullName, email, password, new AuthRepository.Callback() {
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
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(SignupActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
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