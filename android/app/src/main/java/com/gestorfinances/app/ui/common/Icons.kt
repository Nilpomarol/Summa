package com.gestorfinances.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.Liquor
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Nightlife
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementType

/**
 * Maps the Material Symbols names stored on categories (design baseline) to the
 * outlined Compose vectors. Unknown names fall back to the neutral "uncategorized" glyph.
 */
fun categoryIcon(name: String?): ImageVector =
    when (name) {
        "home" -> Icons.Outlined.Home
        "shopping_cart" -> Icons.Outlined.ShoppingCart
        "restaurant" -> Icons.Outlined.Restaurant
        "directions_car" -> Icons.Outlined.DirectionsCar
        "sports_esports" -> Icons.Outlined.SportsEsports
        "credit_card" -> Icons.Outlined.CreditCard
        "payments" -> Icons.Outlined.Payments
        "savings" -> Icons.Outlined.Savings
        "school" -> Icons.Outlined.School
        "local_hospital" -> Icons.Outlined.LocalHospital
        "flight" -> Icons.Outlined.Flight
        "phone_android" -> Icons.Outlined.PhoneAndroid
        "fitness_center" -> Icons.Outlined.FitnessCenter
        "apartment" -> Icons.Outlined.Apartment
        "weekend" -> Icons.Outlined.Weekend
        "kitchen" -> Icons.Outlined.Kitchen
        "build" -> Icons.Outlined.Build
        "fastfood" -> Icons.Outlined.Fastfood
        "local_cafe" -> Icons.Outlined.LocalCafe
        "local_bar" -> Icons.Outlined.LocalBar
        "wine_bar" -> Icons.Outlined.LocalCafe
        "train" -> Icons.Outlined.Train
        "directions_bus" -> Icons.Outlined.DirectionsBus
        "local_taxi" -> Icons.Outlined.LocalTaxi
        "two_wheeler" -> Icons.Outlined.TwoWheeler
        "directions_bike" -> Icons.AutoMirrored.Outlined.DirectionsBike
        "local_gas_station" -> Icons.Outlined.LocalGasStation
        "shopping_bag" -> Icons.Outlined.ShoppingBag
        "storefront" -> Icons.Outlined.Storefront
        "diamond" -> Icons.Outlined.Diamond
        "medical_services" -> Icons.Outlined.MedicalServices
        "local_pharmacy" -> Icons.Outlined.LocalPharmacy
        "spa" -> Icons.Outlined.Spa
        "self_improvement" -> Icons.Outlined.SelfImprovement
        "movie" -> Icons.Outlined.Movie
        "music_note" -> Icons.Outlined.MusicNote
        "theater_comedy" -> Icons.Outlined.TheaterComedy
        "sports_soccer" -> Icons.Outlined.SportsSoccer
        "sports_basketball" -> Icons.Outlined.SportsBasketball
        "beach_access" -> Icons.Outlined.BeachAccess
        "hotel" -> Icons.Outlined.Hotel
        "luggage" -> Icons.Outlined.Luggage
        "computer" -> Icons.Outlined.Computer
        "headphones" -> Icons.Outlined.Headphones
        "camera_alt" -> Icons.Outlined.CameraAlt
        "wifi" -> Icons.Outlined.Wifi
        "menu_book" -> Icons.AutoMirrored.Outlined.MenuBook
        "science" -> Icons.Outlined.Science
        "calculate" -> Icons.Outlined.Calculate
        "work" -> Icons.Outlined.Work
        "business_center" -> Icons.Outlined.BusinessCenter
        "receipt" -> Icons.Outlined.Receipt
        "face" -> Icons.Outlined.Face
        "content_cut" -> Icons.Outlined.ContentCut
        "child_care" -> Icons.Outlined.ChildCare
        "pets" -> Icons.Outlined.Pets
        "group" -> Icons.Outlined.Group
        "volunteer_activism" -> Icons.Outlined.VolunteerActivism
        "card_giftcard" -> Icons.Outlined.CardGiftcard
        "celebration" -> Icons.Outlined.Celebration
        "bolt" -> Icons.Outlined.Bolt
        "water_drop" -> Icons.Outlined.Opacity
        "local_mall" -> Icons.Outlined.LocalMall
        "checkroom" -> Icons.Outlined.Checkroom
        "local_laundry_service" -> Icons.Outlined.LocalLaundryService
        "cleaning_services" -> Icons.Outlined.CleaningServices
        "park" -> Icons.Outlined.Park
        "local_florist" -> Icons.Outlined.LocalFlorist
        "cake" -> Icons.Outlined.Cake
        "icecream" -> Icons.Outlined.Icecream
        "liquor" -> Icons.Outlined.Liquor
        "casino" -> Icons.Outlined.Casino
        "nightlife" -> Icons.Outlined.Nightlife
        "directions_boat" -> Icons.Outlined.DirectionsBoat
        "ev_station" -> Icons.Outlined.EvStation
        "local_parking" -> Icons.Outlined.LocalParking
        "hiking" -> Icons.Outlined.Hiking
        "pool" -> Icons.Outlined.Pool
        "euro" -> Icons.Outlined.Euro
        else -> Icons.Outlined.MoreHoriz
    }

/** Maps the icon key stored on accounts to an outlined Compose vector. */
fun accountIcon(key: String?): ImageVector =
    when (key) {
        "account_balance" -> Icons.Outlined.AccountBalance
        "payments" -> Icons.Outlined.Payments
        "savings" -> Icons.Outlined.Savings
        "trending_up" -> Icons.AutoMirrored.Outlined.TrendingUp
        "credit_card" -> Icons.Outlined.CreditCard
        "wallet" -> Icons.Outlined.AccountBalanceWallet
        "business" -> Icons.Outlined.Business
        "receipt_long" -> Icons.AutoMirrored.Outlined.ReceiptLong
        else -> Icons.Outlined.AccountBalance
    }

fun accountTypeIcon(type: AccountType): ImageVector =
    when (type) {
        AccountType.BANK -> Icons.Outlined.AccountBalance
        AccountType.CASH -> Icons.Outlined.Payments
        AccountType.SAVINGS -> Icons.Outlined.Savings
        AccountType.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
        AccountType.OTHER -> Icons.Outlined.AccountBalanceWallet
    }

/** Icon for a movement chip when no category icon applies (income/transfer/settlement/refund). */
fun movementTypeIcon(type: MovementType): ImageVector =
    when (type) {
        MovementType.INCOME -> Icons.Outlined.SouthWest
        MovementType.EXPENSE -> Icons.Outlined.MoreHoriz
        MovementType.TRANSFER -> Icons.Outlined.SwapHoriz
        MovementType.SETTLEMENT -> Icons.Outlined.Handshake
        MovementType.REFUND -> Icons.AutoMirrored.Outlined.AssignmentReturn
        MovementType.CONTRIBUTION -> Icons.Outlined.Savings
    }
