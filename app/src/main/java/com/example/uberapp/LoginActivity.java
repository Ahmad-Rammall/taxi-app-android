package com.example.uberapp;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    FirebaseAuth mAuth;
    private Button btnSignIn ;
    private EditText email , password;
    private TextView link;
    LoadingDialog loadingDialog = new LoadingDialog(LoginActivity.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        btnSignIn = findViewById(R.id.btnSignIn);
        link = findViewById(R.id.link);

        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String txtEmail = email.getText().toString();
                String txtPass = password.getText().toString();

                loadingDialog.startLoading();

                try {
                    mAuth.signInWithEmailAndPassword(txtEmail, txtPass)
                            .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                                @Override
                                public void onComplete(@NonNull Task<AuthResult> task) {
                                    if (task.isSuccessful()) {
                                        // Sign in success, update UI with the signed-in user's information
                                        openActivity();
                                    } else {
                                        // If sign in fails, display a message to the user.
                                        loadingDialog.dismissDialog();
                                        Toast.makeText(LoginActivity.this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
                catch (Exception e){
                    Toast.makeText(LoginActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext() , RegisterActivity.class));
            }
        });

    }
    private void openActivity()
    {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers");
        usersRef.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // If snapshot.exists() ==> if the user is a 'Clients' child.
                if (snapshot.exists()) {
                    Intent intent = new Intent(LoginActivity.this, DriverActivity.class);
                    startActivity(intent);

                } else {
                    Intent intent = new Intent(LoginActivity.this, ClientActivity.class);
                    startActivity(intent);
                }

                loadingDialog.dismissDialog();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Handle the error case
            }
        });
    }
}