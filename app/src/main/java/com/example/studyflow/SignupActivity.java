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

public class SignupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        TextView signupTv = findViewById(R.id.signupTv);
        signupTv.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        EditText usernameInput = findViewById(R.id.usernameInput);
        EditText fullNameInput = findViewById(R.id.fullNameInput);
        EditText emailInput = findViewById(R.id.emailInput);
        EditText passwordInput = findViewById(R.id.passwordInput);
        Button signupButton = findViewById(R.id.signupButton);

        //To ensure user input is not erased after pause
        SharedPreferences signupData = getSharedPreferences("SignupData", MODE_PRIVATE);
        usernameInput.setText(signupData.getString("username", ""));
        fullNameInput.setText(signupData.getString("fullname", ""));
        emailInput.setText(signupData.getString("email", ""));
        passwordInput.setText(signupData.getString("password", ""));


        signupButton.setOnClickListener(v -> {
            registerAccount(usernameInput, fullNameInput, emailInput, passwordInput);
        });


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        EditText usernameInput = findViewById(R.id.usernameInput);
        EditText fullNameInput = findViewById(R.id.fullNameInput);
        EditText emailInput = findViewById(R.id.emailInput);
        EditText passwordInput = findViewById(R.id.passwordInput);

        SharedPreferences signupData = getSharedPreferences("SignupData", MODE_PRIVATE);
        SharedPreferences.Editor editor = signupData.edit();

        editor.putString("username", usernameInput.getText().toString());
        editor.putString("fullname", fullNameInput.getText().toString());
        editor.putString("email", emailInput.getText().toString());
        editor.putString("password", passwordInput.getText().toString());
        editor.apply();
    }

    private void registerAccount(EditText usernameInput, EditText fullNameInput, EditText emailInput, EditText passwordInput)
    {
        String username = usernameInput.getText().toString().trim();
        String fullName = "Anonymous User";
        if (fullNameInput.getText() != null)
        {
            fullName = fullNameInput.getText().toString().trim();
        }
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty())
        {
            Toast.makeText(this, "Please fill in all the required fields", Toast.LENGTH_SHORT).show();
        }
        else
        {
            SharedPreferences preferences = getSharedPreferences("SpotSnapAccount", MODE_PRIVATE);

            preferences.edit()
                    .putString("username", username)
                    .putString("fullName", fullName)
                    .putString("email", email)
                    .putString("password", password)
                    .apply();

            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }
}
