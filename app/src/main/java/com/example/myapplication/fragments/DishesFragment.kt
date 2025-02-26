package com.example.myapplication.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.ReviewAdapter
import com.example.myapplication.databinding.FragmentDishesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import model.DishReview

class DishesFragment : Fragment() {

    private var _binding: FragmentDishesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var reviewsRecyclerView: RecyclerView
    private var reviewList = mutableListOf<DishReview>()
    private lateinit var searchBar: EditText
    private var searchJob: Job? = null  // Debouncing search

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDishesBinding.inflate(inflater, container, false)
        val root = binding.root
        firestore = FirebaseFirestore.getInstance()

        reviewsRecyclerView = binding.dishesrecyclerView
        reviewsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        reviewAdapter = ReviewAdapter(reviewList, requireContext())
        reviewsRecyclerView.adapter = reviewAdapter

        searchBar = binding.topSearch.findViewById(R.id.searchEditText)

        // Show ProgressBar initially
        binding.progressBarDishes.visibility = View.VISIBLE
        reviewsRecyclerView.visibility = View.GONE

        fetchReviews()  // Load all reviews initially

        setupSearchListener()

        return root
    }

    /**
     * Fetches all reviews initially.
     */
    private fun fetchReviews() {
        showLoader(true)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        firestore.collection("dishReview")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val newReviewList = documents.mapNotNull { document ->
                    document.toObject(DishReview::class.java).apply {
                        id = document.id
                        likedBy = (document.get("likedBy") as? List<String>) ?: emptyList()
                    }
                }
                reviewList.clear()
                reviewList.addAll(newReviewList)
                reviewAdapter.updateReviews(reviewList)
                reviewAdapter.setCurrentUserId(currentUserId)

                showLoader(false)
            }
            .addOnFailureListener { exception ->
                showToast("Error fetching reviews: ${exception.message}")
                showLoader(false)
            }
    }

    /**
     * Sets up the search bar listener with debounce logic.
     */
    private fun setupSearchListener() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()  // Cancel previous search if still running
                searchJob = lifecycleScope.launch {
                    delay(500)  // Debounce time (500ms)
                    searchReviews(s.toString())
                }
            }
        })
    }

    /**
     * Fetch reviews based on search query.
     */
    private fun searchReviews(query: String) {
        if (query.isEmpty()) {
            fetchReviews()  // Load all reviews if search is empty
            return
        }

        showLoader(true)

        lifecycleScope.launch {
            try {
                val dishQuery = async {
                    firestore.collection("dishReview")
                        .orderBy("dishName", Query.Direction.ASCENDING)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .whereGreaterThanOrEqualTo("dishName", query)
                        .whereLessThanOrEqualTo("dishName", query + "\uf8ff")
                        .get()
                        .await() // ✅ Await here to get QuerySnapshot
                }

                val restaurantQuery = async {
                    firestore.collection("dishReview")
                        .orderBy("restaurantName") // ✅ Order by restaurantName first
                        .whereGreaterThanOrEqualTo("restaurantName", query)
                        .whereLessThanOrEqualTo("restaurantName", query + "\uf8ff")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get()
                        .await() // ✅ Await here to get QuerySnapshot
                }

                val dishResults = dishQuery.await().documents.mapNotNull { doc ->
                    doc.toObject(DishReview::class.java)?.apply { id = doc.id }
                }

                val restaurantResults = restaurantQuery.await().documents.mapNotNull { doc ->
                    doc.toObject(DishReview::class.java)?.apply { id = doc.id }
                }

                val combinedResults = (dishResults + restaurantResults).distinctBy { it.id }

                if (combinedResults.isEmpty()) {
                    showToast("No results found.")
                }

                reviewList.clear()
                reviewList.addAll(combinedResults)
                reviewAdapter.updateReviews(reviewList)

            } catch (e: Exception) {
                showToast("Error fetching search results: ${e.message}")
            } finally {
                showLoader(false)
            }
        }
    }



    /**
     * Shows or hides the progress bar while fetching data.
     */
    private fun showLoader(show: Boolean) {
        binding.progressBarDishes.visibility = if (show) View.VISIBLE else View.GONE
        reviewsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
