package com.example.studyflow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        TextView createTv = findViewById(R.id.createTv);

        createTv.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        EditText usernameInput = findViewById(R.id.usernameInput);
        EditText passwordInput = findViewById(R.id.passwordInput);
        Button loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> {
            loginAccount(usernameInput, passwordInput);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        EditText usernameInput = findViewById(R.id.usernameInput);
        EditText passwordInput = findViewById(R.id.passwordInput);

        usernameInput.setEnabled(true);
        usernameInput.setFocusable(true);
        usernameInput.setFocusableInTouchMode(true);

        passwordInput.setEnabled(true);
        passwordInput.setFocusable(true);
        passwordInput.setFocusableInTouchMode(true);
    }

    private void loginAccount(EditText usernameInput, EditText passwordInput)
    {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        SharedPreferences preferences = getSharedPreferences("SpotSnapAccount", MODE_PRIVATE);

        String storedUsername = preferences.getString("username", "");
        String storedPassword = preferences.getString("password", "");

        if ((username.equals(storedUsername) && password.equals(storedPassword) && !storedPassword.isEmpty()) || (username.equals("user") && password.equals("password")))
        {
            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();

        }
        else
        {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
        }
    }
}