package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class ReviewDetailActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageView
    private lateinit var deleteBtn: ImageView
    private lateinit var restImage: ImageView
    private lateinit var ratingIcon: ImageView
    private lateinit var likeBtn: ImageView

    private lateinit var postName: TextView
    private lateinit var ratingTextView: TextView
    private lateinit var likeCount: TextView
    private lateinit var postDescription: TextView

    private lateinit var firestore: FirebaseFirestore
    private var reviewId: String? = null
    private var userId: String? = null  // ID of the post owner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_detail)

        val dishName = intent.getStringExtra("dishName")
        val restaurantName = intent.getStringExtra("restaurantName")
        val userName = intent.getStringExtra("userName")
        val reviewText = intent.getStringExtra("reviewText")
        val price = intent.getIntExtra("price", 0)
        val likes = intent.getIntExtra("likes", 0)
        val rating = intent.getIntExtra("rating", 0)
        val imageUrl = intent.getStringExtra("imageUrl")
        val userProfilePic = intent.getStringExtra("userProfilePic")

        findViewById<TextView>(R.id.postName).text = dishName
//        findViewById<TextView>(R.id.detailRestaurantName).text = restaurantName
//        findViewById<TextView>(R.id.detailUserName).text = userName
        findViewById<TextView>(R.id.postDescription).text = reviewText
//        findViewById<TextView>(R.id.detailPrice).text = "Rs.$price"
        findViewById<TextView>(R.id.like_count).text = "$likes Likes"
        findViewById<TextView>(R.id.ratingTextView).text = "Rating: $rating"

        val foodImage = findViewById<ImageView>(R.id.restImage)
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this).load(imageUrl).into(foodImage)
        }

//        val profilePic = findViewById<ImageView>(R.id.detailUserProfilePic)
//        if (!userProfilePic.isNullOrEmpty()) {
//            Glide.with(this).load(userProfilePic).into(profilePic)
//        }

//        // Initialize Firebase Firestore
//        firestore = FirebaseFirestore.getInstance()
//
//        // Get data from intent
//        reviewId = intent.getStringExtra("reviewId")
//        val dishName = intent.getStringExtra("dishName")
//        val restaurantName = intent.getStringExtra("restaurantName")
//        val reviewText = intent.getStringExtra("reviewText")
//        val price = intent.getIntExtra("price", 0)
//        val rating = intent.getIntExtra("rating", 0)
//        val imageUrl = intent.getStringExtra("imageUrl")
//        val userName = intent.getStringExtra("userName")
//        val userProfilePic = intent.getStringExtra("userProfilePic")
//        val likes = intent.getIntExtra("likes", 0)
//        val shopLat = intent.getDoubleExtra("shopLat", 0.0)
//        val shopLng = intent.getDoubleExtra("shopLng", 0.0)
//
//        // Initialize UI elements
//        backBtn = findViewById(R.id.backBtn_offerDesc)
//        deleteBtn = findViewById(R.id.deleteBtn)
//        restImage = findViewById(R.id.restImage)
//        ratingIcon = findViewById(R.id.rating)
//        likeBtn = findViewById(R.id.like_btn)
//
//        postName = findViewById(R.id.postName)
//        ratingTextView = findViewById(R.id.ratingTextView)
//        likeCount = findViewById(R.id.like_count)
//        postDescription = findViewById(R.id.postDescription)
//
//        // Set data to UI
//        postName.text = "$dishName - $restaurantName"
//        ratingTextView.text = rating.toString()
//        likeCount.text = likes.toString()
//        postDescription.text = reviewText ?: "No description available"
//
//        // Load image using Glide
//        Glide.with(this)
//            .load(imageUrl)
//            .placeholder(R.drawable.biryani_plahol)
//            .into(restImage)
//
//        // Handle back button click
//        backBtn.setOnClickListener {
//            finish()
//        }
//        // Handle delete button click (only allow the owner to delete)
//        deleteBtn.setOnClickListener {
//            deleteReview()
//        }
    }

//    private fun deleteReview() {
//        if (reviewId != null) {
//            firestore.collection("reviews").document(reviewId!!)
//                .delete()
//                .addOnSuccessListener {
//                    Toast.makeText(this, "Review deleted", Toast.LENGTH_SHORT).show()
//                    finish()
//                }
//                .addOnFailureListener {
//                    Toast.makeText(this, "Failed to delete review", Toast.LENGTH_SHORT).show()
//                }
//        }
//    }
}