package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class ReviewDetailActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageView
    private lateinit var deleteBtn: ImageView
    private lateinit var restImage: ImageView
    private lateinit var likeBtn: ImageView

    private lateinit var postName: TextView
    private lateinit var restName: TextView
    private lateinit var ratingTextView: TextView
    private lateinit var likeCount: TextView
    private lateinit var postDescription: TextView
    private lateinit var detailPrice: TextView

    private lateinit var backgroundLayout: ConstraintLayout

    private lateinit var firestore: FirebaseFirestore
    private var reviewId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_detail)

        // Initialize views
        backBtn = findViewById(R.id.backBtn_offerDesc)
        deleteBtn = findViewById(R.id.deleteBtn)
        restImage = findViewById(R.id.restImage)
        likeBtn = findViewById(R.id.like_btn)

        postName = findViewById(R.id.postName)
        restName = findViewById(R.id.restName)
        ratingTextView = findViewById(R.id.ratingTextView)
        likeCount = findViewById(R.id.like_count)
        postDescription = findViewById(R.id.postDescription)
        detailPrice = findViewById(R.id.detailPrice)
        backgroundLayout = findViewById(R.id.background_color)

        // Get data from intent
        val dishName = intent.getStringExtra("dishName") ?: ""
        val restaurantName = intent.getStringExtra("restaurantName") ?: ""
        val reviewText = intent.getStringExtra("reviewText") ?: ""
        val price = intent.getIntExtra("price", 0)
        val likes = intent.getIntExtra("likes", 0)
        val rating = intent.getIntExtra("rating", 0)
        val imageUrl = intent.getStringExtra("imageUrl")

        // Set values to views
        postName.text = dishName
        restName.text = restaurantName
        postDescription.text = reviewText
        detailPrice.text = "₹$price"
        likeCount.text = "$likes Likes"
        ratingTextView.text = "Rating: $rating"

        // Load image using Glide
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this).load(imageUrl).into(restImage)
        }

        // Set background color based on rating
        val backgroundColor = when (rating) {
            0, 1 -> R.drawable.redrectangle
            2 -> R.drawable.orangerectangle
            3 -> R.drawable.yellowrectangle
            else -> R.drawable.greenrectangle
        }
        backgroundLayout.setBackgroundResource(backgroundColor)

        // Initialize Firebase Firestore
        firestore = FirebaseFirestore.getInstance()

        // Handle back button click
        backBtn.setOnClickListener {
            finish()
        }

        // Handle delete button click
        deleteBtn.setOnClickListener {
            deleteReview()
        }
    }

    private fun deleteReview() {
        if (reviewId != null) {
            firestore.collection("reviews").document(reviewId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Review deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete review", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
