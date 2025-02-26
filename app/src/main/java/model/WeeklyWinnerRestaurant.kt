package model

import com.google.firebase.Timestamp

data class WeeklyWinnerRestaurant(
    var restaurantId: String = "",
    var name: String = "",
    var imageUrl: String = "",
    var votes: Int = 0,
    var weekStartTimestamp: Timestamp = Timestamp.now()
)
