package com.example.workshop_3.Firebase

import android.content.ContentValues.TAG
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class UserManager(database: FirebaseDatabase) {

    private val userReference: DatabaseReference = database.reference
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()


    fun writeUserLocation(lat: Double, lng: Double) {
        val user = auth.currentUser
        user?.let { currentUser ->
            val userReference = userReference.child("USERS").child(currentUser.uid)
            userReference.child("latitude").setValue(lat)
            userReference.child("longitude").setValue(lng).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Location updated successfully for user ${currentUser.uid}")
                } else {
                    Log.e(
                        TAG,
                        "Failed to update location for user ${currentUser.uid}: ${task.exception?.message}"
                    )
                }
            }
        } ?: Log.e(TAG, "No user logged in")
    }

    fun toggleUserAvailability() {
        val user = auth.currentUser
        user?.let { currentUser ->
            val userReference = userReference.child(currentUser.uid).child("available")
            userReference.get().addOnSuccessListener { dataSnapshot ->
                val currentAvailability = dataSnapshot.getValue(Boolean::class.java) ?: false
                val newAvailability = !currentAvailability
                userReference.setValue(newAvailability).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(
                            TAG,
                            "Availability updated to $newAvailability for user ${currentUser.uid}"
                        )
                    } else {
                        Log.e(
                            TAG,
                            "Failed to update availability for user ${currentUser.uid}: ${task.exception?.message}"
                        )
                    }
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting user availability: ${exception.message}")
            }
        } ?: Log.e(TAG, "No user logged in")
    }
}