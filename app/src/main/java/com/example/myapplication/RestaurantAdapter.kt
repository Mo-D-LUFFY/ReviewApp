package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import model.Restaurant

class RestaurantAdapter(
    private val restaurants: MutableList<Restaurant>,
    private val onVoteClick: (Restaurant) -> Unit
) : RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_restaurant_voting, parent, false)
        return RestaurantViewHolder(view)
    }

    override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
        val restaurant = restaurants[position]
        holder.restaurantName.text = restaurant.name
        holder.voteCount.text = "${restaurant.votes} Votes"
        Glide.with(holder.itemView.context).load(restaurant.imageUrl).into(holder.restaurantImage)

        holder.voteUpButton.setOnClickListener {
            onVoteClick(restaurant)
        }
    }

    override fun getItemCount(): Int = restaurants.size

    class RestaurantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val restaurantName: TextView = itemView.findViewById(R.id.restaurantName)
        val voteCount: TextView = itemView.findViewById(R.id.voteCount)
        val restaurantImage: ImageView = itemView.findViewById(R.id.restaurantImage)
        val voteUpButton: CardView = itemView.findViewById(R.id.voteUpButton)
        val cardView: LinearLayout = itemView.findViewById(R.id.cardView)
    }
}