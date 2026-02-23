package me.amitshekhar.ridesharing

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import me.amitshekhar.customerActivity
import me.amitshekhar.ridesharing.databinding.ActivityLoginBinding
import me.amitshekhar.ridesharing.ui.maps.MapsActivity

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val loginButton = findViewById<Button>(R.id.loginBtn)
        val registerButton = findViewById<Button>(R.id.regBtn)


        val spinner: Spinner = findViewById(R.id.roles_spinner)
        // Create an ArrayAdapter using the string array and a default spinner layout
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.roles_array,
            android.R.layout.simple_spinner_item // Default layout for selected item
        )

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Apply the adapter to the spinner
        spinner.adapter = adapter



        firebaseAuth = FirebaseAuth.getInstance()

        registerButton.setOnClickListener {
            //val intent = Intent(this, signup::class.java)
            //startActivity(intent)
            //Toast.makeText(this, "REGISTRATION +6289-777-777-20", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, signup::class.java)
            startActivity(intent)
        }
        loginButton.setOnClickListener {
            val email =findViewById<TextView>(R.id.username_input).text.toString()
            val pass = findViewById<TextView>(R.id.password_input).text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty()) {

                firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {

                        val selectedText = spinner.selectedItem.toString()
                        if (selectedText == "FLEET"){
                            val intent = Intent(this, MapsActivity::class.java)
                            startActivity(intent)
                            Toast.makeText(this, "WELCOME FLEET, "+email, Toast.LENGTH_LONG).show()
                        }
                        else if (selectedText == "CUSTOMER") {
                            val intent = Intent(this, customerActivity::class.java)
                            startActivity(intent)
                            Toast.makeText(this, "WELCOME CUSTOMER, "+email, Toast.LENGTH_LONG).show()
                        }


                    } else {
                        Toast.makeText(this, "EMAIL OR PASSWORD IS WRONG OR NOT REGISTERED YET", Toast.LENGTH_SHORT).show()
                        //Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()

                    }
                }
            } else {
                Toast.makeText(this, "Empty Fields Are not Allowed !!", Toast.LENGTH_SHORT).show()

            }
        }

    }

    override fun onStart() {
        super.onStart()

        if(firebaseAuth.currentUser != null){
            val intent = Intent(this, customerActivity::class.java)
            startActivity(intent)
        }
    }
}