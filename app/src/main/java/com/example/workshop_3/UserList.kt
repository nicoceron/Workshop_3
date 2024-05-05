package com.example.workshop_3

import android.content.ContentValues
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.workshop_3.databinding.ActivityUserListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UserList : AppCompatActivity() {

    data class User(
        val available: Boolean = false,
        val idNumber: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val userName: String = ""
    )

    private lateinit var binding: ActivityUserListBinding
    private lateinit var database: FirebaseDatabase // Firebase database instance
    private lateinit var usersRef: DatabaseReference
    private lateinit var auth: FirebaseAuth // Firebase authentication instance


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()
        usersRef = database.reference.child("USERS")
        subscribeToUserChanges()
    }

    private fun subscribeToUserChanges() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.listUsers.removeAllViews() // Clear the list to avoid duplicate entries
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java)
                    if (user != null && user.available) {
                        val textView = TextView(baseContext)
                        textView.text = "${user.userName} - ${user.idNumber}"
                        binding.listUsers.addView(textView)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(
                    ContentValues.TAG, "Database access was cancelled or failed: ${error.message}"
                )
            }
        }
        usersRef.addValueEventListener(listener)
    }
}
