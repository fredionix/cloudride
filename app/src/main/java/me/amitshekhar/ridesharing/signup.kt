package me.amitshekhar.ridesharing

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import me.amitshekhar.ridesharing.databinding.ActivitySignupBinding


class signup : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //binding = ActivitySignupBinding.inflate(layoutInflater)
        //setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        val loginButton = findViewById<Button>(R.id.loginBtn)
        val submitButton = findViewById<Button>(R.id.submit_registration)

        loginButton.setOnClickListener {
            val intent = Intent(this, signin::class.java)
            startActivity(intent)
        }


        submitButton.setOnClickListener {
            val email = findViewById<TextView>(R.id.email_input).text.toString()
            val pass = findViewById<TextView>(R.id.password_input).text.toString()
            val confirmPass = findViewById<TextView>(R.id.password_input_validation).text.toString()
            val phone = findViewById<TextView>(R.id.phone_input).text.toString()
            val role = findViewById<TextView>(R.id.role_input).text.toString()




            if (email.isNotEmpty() && pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                if (pass == confirmPass) {

                    firebaseAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener {
                        if (it.isSuccessful) {
                            val intent = Intent(this, signin::class.java)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()

                        }
                    }
                } else {
                    Toast.makeText(this, "Password is not matching", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Empty Fields Are not Allowed !!", Toast.LENGTH_SHORT).show()

            }
        }


    }
}