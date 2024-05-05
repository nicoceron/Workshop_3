package com.example.workshop_3

import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.workshop_3.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class SignUp : AppCompatActivity() {

    // Defining a class represent user data
    data class MyUser(
        val userName: String,
        val idNumber: String,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val available: Boolean = false
    )

    val USERS = "users/"

    // Declaring variables
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var database: FirebaseDatabase // Firebase database instance
    private lateinit var myRef: DatabaseReference // Reference to a specific location in the database
    private lateinit var auth: FirebaseAuth // Firebase authentication instance
    private var selectedImageUri: Uri? = null

    private val getContentGallery =
        registerForActivityResult(ActivityResultContracts.GetContent(), ActivityResultCallback {
            loadImage(it)
        })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignUpBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Getting an instance of FirebaseDatabase for database operations
        database = FirebaseDatabase.getInstance()


        // Getting an instance of FirebaseAuth for authentication operations
        auth = FirebaseAuth.getInstance()

        // Setting up a click listener for the "pickImage" button to launch a gallery content request when clicked
        binding.pickImage.setOnClickListener {
            getContentGallery.launch("image/*")
        }

        // Setting up a click listener for the "register" button
        binding.continueButton.setOnClickListener {
            val email = binding.email.text.toString().trim()
            val password = binding.password.text.toString().trim()
            val userName = binding.userName.text.toString().trim()
            val idNumber = binding.idNumber.text.toString().trim()

            if (validateForm(email, password, userName, idNumber)) {
                createAccount(email, password, userName, idNumber)
            }
        }
    }

    private fun loadImage(uri: Uri?) {
        uri?.let {
            selectedImageUri = it
            val imageStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(imageStream)
            binding.profileImage.setImageBitmap(bitmap)
        }
    }

    private fun createAccount(email: String, password: String, userName: String, idNumber: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                uploadProfileImage(userName, idNumber)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.auth_failed) + task.exception.toString(),
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, task.exception?.message ?: "Unknown error")
            }
        }
    }

    private fun uploadProfileImage(userName: String, idNumber: String) {
        val user = auth.currentUser
        val storageRef = FirebaseStorage.getInstance().reference
        selectedImageUri?.let { uri ->
            val profileImageRef = storageRef.child("profile_images/${user?.uid}.jpg")
            val uploadTask = profileImageRef.putFile(uri)
            uploadTask.addOnSuccessListener { taskSnapshot ->
                // Getting the download URL from the file that was just uploaded
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                    updateUserProfile(downloadUri.toString(), userName, idNumber)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Getting download URL failed: ${e.message}")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Uploading profile image failed: ${e.message}")
            }
        } ?: run {
            // If no image selected, just update the user profile without the image
            updateUserProfile("", userName, idNumber)
        }
    }


    private fun updateUserProfile(imageUrl: String, userName: String, idNumber: String) {
        val user = auth.currentUser
        user?.let {
            val userProfileChangeRequest =
                UserProfileChangeRequest.Builder().setDisplayName(userName)
                    .setPhotoUri(Uri.parse(imageUrl)).build()

            it.updateProfile(userProfileChangeRequest).addOnCompleteListener { updateTask ->
                if (updateTask.isSuccessful) {
                    Log.d(TAG, "User profile updated successfully")
                    val additionalUserDetails = MyUser(userName, idNumber, 0.0, 0.0, false)
                    val myRef = FirebaseDatabase.getInstance().getReference("USERS/${user.uid}")
                    myRef.setValue(additionalUserDetails).addOnSuccessListener {
                        Log.d(TAG, "Additional user details saved successfully")
                        updateUI(user)
                    }.addOnFailureListener {
                        Log.e(TAG, "Failed to save additional user details: ${it.message}")
                    }
                } else {
                    Log.e(TAG, "User profile update failed: ${updateTask.exception?.message}")
                }
            }
        }
    }


    private fun validateForm(
        email: String, password: String, userName: String, idNumber: String
    ): Boolean {
        var valid = true

        if (email.isEmpty()) {
            binding.email.error = "Required!"
            valid = false
        } else if (!isValidEmail(email)) {
            binding.email.error = "Invalid email address"
            valid = false
        } else {
            binding.email.error = null
        }

        if (password.isEmpty()) {
            binding.password.error = "Required"
            valid = false
        } else if (password.length < 6) {
            binding.password.error = "Password should be at least 6 characters long"
            valid = false
        } else {
            binding.password.error = null
        }

        if (userName.isEmpty()) {
            binding.userName.error = "Required"
            valid = false
        } else {
            binding.userName.error = null
        }

        if (idNumber.isEmpty()) {
            binding.idNumber.error = "Required"  // Correct field for idNumber error
            valid = false
        } else {
            binding.idNumber.error = null
        }

        return valid
    }


    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        return email.matches(emailRegex.toRegex())
    }

    private fun updateUI(currentUser: FirebaseUser?) {
        if (currentUser != null) {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra("email", currentUser.email.toString())
            startActivity(i)
        }
    }

}