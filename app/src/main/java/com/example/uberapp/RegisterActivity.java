package com.example.uberapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {

    private Button registerBtn;
    private EditText regName , regEmail , regPass , regPhoneNb;
    private CheckBox checkBox;
    FirebaseAuth auth;
    FirebaseUser user;
    DatabaseReference dbReference;
    DatabaseReference dbReferenceDrivers;
    DatabaseReference dbReferenceClients;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        dbReference = FirebaseDatabase.getInstance().getReference().child("Users");
        dbReferenceClients = dbReference.child("Clients");
        dbReferenceDrivers = dbReference.child("Drivers");


        regName = findViewById(R.id.fullName);
        regEmail = findViewById(R.id.email);
        regPass = findViewById(R.id.password);
        regPhoneNb = findViewById(R.id.phoneNb);
        registerBtn = findViewById(R.id.btnRegister);
        checkBox = findViewById(R.id.isDriver);

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String fullName = regName.getText().toString();
                String email = regEmail.getText().toString();
                String password = regPass.getText().toString();
                String phoneNb = regPhoneNb.getText().toString();
                boolean isDriver = checkBox.isChecked();

                if(TextUtils.isEmpty(fullName))
                {
                    Toast.makeText(RegisterActivity.this, "Name is empty !", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(TextUtils.isEmpty(email))
                {
                    Toast.makeText(RegisterActivity.this, "Email is empty !", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(TextUtils.isEmpty(password) || password.length()<6)
                {
                    Toast.makeText(RegisterActivity.this, "Password is too Short !", Toast.LENGTH_SHORT).show();
                    return;
                }

                if(TextUtils.isEmpty(phoneNb))
                {
                    Toast.makeText(RegisterActivity.this, "Phone Number is empty !", Toast.LENGTH_SHORT).show();
                    return;
                }

                auth.createUserWithEmailAndPassword(email , password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful() && !isDriver)
                        {
                            user = task.getResult().getUser();
                            DatabaseReference newUser = dbReferenceClients.child(user.getUid());
                            newUser.child("Full Name").setValue(fullName);
                            newUser.child("Email Address").setValue(email);
                            newUser.child("Password").setValue(password);
                            newUser.child("Phone Number").setValue(phoneNb);
                            newUser.child("isDriver").setValue(isDriver);

                            Intent intent = new Intent(getApplicationContext() , LoginActivity.class);
                            startActivity(intent);

                            Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                        }
                        else if(task.isSuccessful() && isDriver == true)
                        {
                            user = task.getResult().getUser();
                            DatabaseReference newUser = dbReferenceDrivers.child(user.getUid());
                            newUser.child("ClientID").setValue("");
                            newUser.child("Full Name").setValue(fullName);
                            newUser.child("Email Address").setValue(email);
                            newUser.child("Password").setValue(password);
                            newUser.child("Phone Number").setValue(phoneNb);
                            newUser.child("isDriver").setValue(isDriver);

                            Intent intent = new Intent(getApplicationContext() , LoginActivity.class);
                            startActivity(intent);

                            Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                        }

                        else
                            Toast.makeText(RegisterActivity.this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });



    }
}