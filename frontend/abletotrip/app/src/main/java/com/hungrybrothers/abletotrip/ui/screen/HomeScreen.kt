package com.hungrybrothers.abletotrip.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hungrybrothers.abletotrip.BuildConfig
import com.hungrybrothers.abletotrip.KakaoAuthViewModel
import com.hungrybrothers.abletotrip.R
import com.hungrybrothers.abletotrip.ui.components.CategorySecond
import com.hungrybrothers.abletotrip.ui.components.HeaderBar
import com.hungrybrothers.abletotrip.ui.components.SearchBar
import com.hungrybrothers.abletotrip.ui.datatype.Attraction
import com.hungrybrothers.abletotrip.ui.datatype.Catalog2Attraction
import com.hungrybrothers.abletotrip.ui.navigation.NavRoute
import com.hungrybrothers.abletotrip.ui.network.AttractionsRepository
import com.hungrybrothers.abletotrip.ui.network.Catalog2Repository
import com.hungrybrothers.abletotrip.ui.network.UserInfoRepository
import com.hungrybrothers.abletotrip.ui.theme.CustomPrimary
import com.hungrybrothers.abletotrip.ui.viewmodel.Catalog2ViewModel
import com.hungrybrothers.abletotrip.ui.viewmodel.CurrentLocationViewModel
import com.hungrybrothers.abletotrip.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.util.Locale

data class IconData(
    val label: String,
    val labelKo: String,
    val icon: Painter,
)

@Composable
fun HomeScreen(
    navController: NavController,
    currentLocationViewModel: CurrentLocationViewModel,
) {
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(AttractionsRepository()) }
    val catalog2ViewModel: Catalog2ViewModel = viewModel { Catalog2ViewModel(Catalog2Repository()) }
    val context = LocalContext.current
    val kakaoAuthViewModel: KakaoAuthViewModel = viewModel()
    var searchText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val categories =
        listOf(
            IconData("park", "공원", painterResource(id = R.drawable.camping)),
            IconData("tour", "관광지", painterResource(id = R.drawable.japanese_castle)),
            IconData("leisure", "레저시설", painterResource(id = R.drawable.leisure)),
            IconData("sports", "체육시설", painterResource(id = R.drawable.stadium)),
            IconData("beauty", "명승지", painterResource(id = R.drawable.mountain)),
            IconData("perform", "공연/연극", painterResource(id = R.drawable.circus_tent)),
            IconData("exhibit", "전시/기념관", painterResource(id = R.drawable.classical_building)),
        )

    // 권한 요청을 처리할 런처 설정
    val locationPermissionRequest =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted: Boolean ->
                if (isGranted) {
                    startLocationUpdates(context, currentLocationViewModel)
                } else {
                    Toast.makeText(context, "위치 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
                    currentLocationViewModel.setLocation(null, null)
                }
            },
        )

    LaunchedEffect(Unit) {
        when (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates(context, currentLocationViewModel)
            }
            else -> {
                locationPermissionRequest.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startLocationUpdates(context, currentLocationViewModel)
                    } else {
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observe location data without default values
    val latitude by currentLocationViewModel.latitude.observeAsState(null)
    val longitude by currentLocationViewModel.longitude.observeAsState(null)

    // UI 시작
    val selectedCategories = remember { mutableStateOf(mutableListOf<String>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderBar(navController = navController, false, true)
            SearchBar(
                text = searchText,
                onValueChange = { newSearchText ->
                    searchText = newSearchText
                },
                onSearch = {
                    if (searchText.isNotEmpty()) {
                        navController.navigate("${NavRoute.SEARCH.routeName}/$searchText")
                    }
                },
                placeholder = "검색어를 입력해주세요.",
                onClear = {
                    searchText = ""
                    keyboardController?.hide()
                },
            )

            CategorySelector(categories, selectedCategories.value) { updatedSelectedCategories ->
                val selectedString = updatedSelectedCategories.joinToString("-")
                if (selectedString.isBlank()) {
                    selectedCategories.value = updatedSelectedCategories.toMutableList()
                } else {
                    if (latitude == null || longitude == null) {
                        Toast.makeText(context, "위치 정보를 확인할 수 없습니다. 위치 권한을 확인해주세요.", Toast.LENGTH_LONG).show()
                    } else {
                        catalog2ViewModel.fetchInitialData(latitude!!, longitude!!, selectedString)
                        selectedCategories.value = updatedSelectedCategories.toMutableList()
                    }
                }
            }

            when {
                selectedCategories.value.isNotEmpty() ->
                    DisplayCustomAttractionsScreen(
                        catalog2ViewModel,
                        navController,
                        latitude,
                        longitude,
                        selectedCategories.value.joinToString("-"),
                    )
                else ->
                    DisplayAttractionsScreen(homeViewModel, navController, latitude, longitude)
            }
        }

        if (latitude == null || longitude == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = {
                        // 설정 화면으로 이동하도록 유도
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        context.startActivity(intent)
                    }) {
                        Text(text = "위치 권한을 설정하려면 여기를 클릭하세요.")
                    }
                }
            }
        }

        FloatingActionMenu(
            navController = navController,
            kakaoAuthViewModel = kakaoAuthViewModel,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        )
    }
}

@Composable
fun FloatingActionMenu(
    navController: NavController,
    kakaoAuthViewModel: KakaoAuthViewModel,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(300)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(300)),
        ) {
            LogoutButton(navController = navController, kakaoAuthViewModel = kakaoAuthViewModel)
        }

        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(300)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(300)),
        ) {
            GohomeActionButton(navController = navController)
        }

        FloatingActionButton(
            onClick = { isMenuExpanded = !isMenuExpanded },
            shape = RoundedCornerShape(50),
            containerColor = CustomPrimary,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                tint = Color.White,
                contentDescription = "Expand Menu",
            )
        }
    }
}

@Composable
fun LogoutButton(
    navController: NavController,
    kakaoAuthViewModel: KakaoAuthViewModel,
) {
    FloatingActionButton(
        onClick = {
            kakaoAuthViewModel.clearAuthData()
            navController.navigate(NavRoute.LOGIN.routeName) {
                popUpTo(NavRoute.HOME.routeName) { inclusive = true }
            }
        },
        shape = RoundedCornerShape(50),
        containerColor = MaterialTheme.colorScheme.secondary,
    ) {
        Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = "Logout",
        )
    }
}

@Composable
fun GohomeActionButton(navController: NavController) {
    val scope = rememberCoroutineScope()
    val userInfoRepository = remember { UserInfoRepository() }

    FloatingActionButton(
        onClick = {
            scope.launch {
                val userData = userInfoRepository.fetchUserInfoData()
                if (userData != null) {
                    val arrivalLatitude = userData.latitude ?: 37.5665
                    val arrivalLongitude = userData.longitude ?: 126.9780
                    val arrivalAddress = userData.address ?: "서울특별시 중구 태평로1가 31"

                    if (arrivalAddress.isNotEmpty() && userData.latitude != null && userData.longitude != null) {
                        navController.navigate(
                            "DEPARTURE/$arrivalLatitude/$arrivalLongitude/$arrivalAddress",
                        )
                    } else {
                        Log.e("GohomeActionButton", "Incomplete user data: Latitude, Longitude, or Address is missing")
                    }
                } else {
                    Log.e("GohomeActionButton", "Failed to retrieve user data")
                }
            }
        },
        shape = RoundedCornerShape(50),
        containerColor = MaterialTheme.colorScheme.secondary,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.wellcomehome),
            tint = Color.White,
            contentDescription = "Go Home",
            modifier = Modifier.size(36.dp),
        )
    }
}

// 카탈로그 선택 부분 (상단)
@Composable
fun CategorySelector(
    categories: List<IconData>,
    initialSelectedCategories: List<String>,
    onSelectCategory: (List<String>) -> Unit,
) {
    val selectedState = remember { mutableStateOf(initialSelectedCategories.toList()) }

    LazyRow(modifier = Modifier.padding(4.dp)) {
        items(categories) { category ->
            val isSelected = category.label in selectedState.value
            CategorySecond(
                icon = category.icon,
                label = category.labelKo,
                isSelected = isSelected,
                onSelect = {
                    val newSelectedState = selectedState.value.toMutableList()
                    if (isSelected) {
                        newSelectedState.remove(category.label)
                    } else {
                        newSelectedState.add(category.label)
                    }
                    selectedState.value = newSelectedState
                    onSelectCategory(newSelectedState)
                },
            )
        }
    }
}

// 카탈로그 선택 시 화면 구성 - newattraction 포함
@Composable
fun DisplayCustomAttractionsScreen(
    viewModel: Catalog2ViewModel,
    navController: NavController,
    latitude: String?,
    longitude: String?,
    category: String,
) {
    // ViewModel에 현재 위치와 카테고리 설정
    viewModel.currentLatitude = latitude
    viewModel.currentLongitude = longitude
    viewModel.currentCategory = category

    val attractionsData by viewModel.catalog2Data.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val lastVisibleItem = visibleItems.lastOrNull()
                if (lastVisibleItem != null && lastVisibleItem.index == attractionsData?.attractions?.size?.minus(1)) {
                    viewModel.fetchMoreData()
                }
            }
    }

    if (attractionsData != null) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
        ) {
            items(attractionsData!!.attractions) { attraction ->
                NewAttractionItem(attraction, navController)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CustomPrimary)
        }
    }
}

// 카탈로그 선택 시 화면의 카드
@Composable
fun NewAttractionItem(
    attraction: Catalog2Attraction,
    navController: NavController,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RectangleShape,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp)
                .clickable(onClick = { navController.navigate("detail/${attraction.id}") }),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = BuildConfig.S3_BASE_URL + attraction.image_url),
                contentDescription = attraction.attraction_name,
                modifier =
                    Modifier
                        .size(120.dp)
                        .fillMaxSize(1f)
                        .aspectRatio(5f / 4f),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(),
            ) {
                // 텍스트를 나란히 표시하기 위해 Row 사용
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                ) {
                    // 관광지 이름
                    Text(
                        text = attraction.attraction_name,
                        modifier =
                            Modifier
                                .padding(horizontal = 8.dp)
                                .weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 카테고리
                    Text(
                        text = "${attraction.category2}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp), // 이름과 간격 유지
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "위치",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "${attraction.si}, ${attraction.gu} ${attraction.dong}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "운영시간",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = attraction.operation_hours,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "휴무일",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = attraction.closed_days,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "입장료",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = if (attraction.is_entrance_fee) "유료" else "무료", // 입장료 상태에 따라 텍스트 변경
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// 기본 화면 구성
// 카테고리 번역 맵
val categoryTranslations =
    mapOf(
        "nearby" to "내 주변 여행지",
        "exhibition-performance" to "전시/공연",
        "leisure-park" to "레저/공원",
        "culture-famous" to "문화관광/명소",
    )

@Composable
fun DisplayAttractionsScreen(
    viewModel: HomeViewModel,
    navController: NavController,
    latitude: String?,
    longitude: String?,
) {
    if (latitude != null && longitude != null) {
        LaunchedEffect(latitude, longitude) {
            viewModel.loadPlaceData(latitude, longitude)
        }
    }

    val attractionsData by viewModel.placeData.observeAsState()

    if (attractionsData?.attractions?.isNotEmpty() == true) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
        ) {
            attractionsData!!.attractions.forEach { (category, attractions) ->
                val categoryTitle =
                    categoryTranslations[category] ?: category.replace('-', ' ')
                        .replaceFirstChar {
                            if (it.isLowerCase()) {
                                it.titlecase(Locale.getDefault())
                            } else {
                                it.toString()
                            }
                        }

                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = categoryTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (category != "nearby") {
                            TextButton(
                                onClick = { navController.navigate("${NavRoute.SHOWMORE.routeName}/$category") },
                            ) {
                                Text(
                                    text = "더보기 >",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (attractions.isNotEmpty()) {
                        LazyRow(modifier = Modifier.padding(horizontal = 8.dp)) {
                            items(attractions) { attraction ->
                                AttractionItem(attraction, navController)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "근처에 해당 목록이 없습니다.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .padding(8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CustomPrimary)
        }
    }
}

// 기본 화면 구성의 카드 형식
@Composable
fun AttractionItem(
    attraction: Attraction,
    navController: NavController,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .padding(4.dp)
                .width(150.dp)
                .clickable(onClick = { navController.navigate("${NavRoute.DETAIL.routeName}/${attraction.id}") }),
    ) {
        Column {
            Image(
                painter = rememberAsyncImagePainter(model = BuildConfig.S3_BASE_URL + attraction.image_url),
                contentDescription = attraction.attraction_name,
                modifier =
                    Modifier
                        .size(150.dp)
                        .fillMaxSize(1f),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier =
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
            ) {
                Text(
                    text = attraction.attraction_name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val distanceText =
                    if (attraction.distance < 1) {
                        "${(attraction.distance * 1000).toInt()}m"
                    } else {
                        "${attraction.distance}km"
                    }
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${attraction.si} ${attraction.gu}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun startLocationUpdates(
    context: Context,
    currentLocationViewModel: CurrentLocationViewModel,
) {
    if (ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    val locationRequest =
        LocationRequest.create().apply {
            interval = 300000 // 5분
            fastestInterval = 60000 // 1분
            priority = Priority.PRIORITY_HIGH_ACCURACY
            maxWaitTime = 60000 // 1분
        }

    val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isEmpty()) return

                locationResult.locations.lastOrNull()?.let { location ->
                    val latitude = location.latitude.toString()
                    val longitude = location.longitude.toString()
                    currentLocationViewModel.setLocation(latitude, longitude)
                }
            }
        }

    // 초기 위치 요청 (한번만)
    fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
        location?.let {
            val latitude = it.latitude.toString()
            val longitude = it.longitude.toString()
            currentLocationViewModel.setLocation(latitude, longitude)
        } ?: run {
            // 만약 마지막 위치를 가져오지 못하면, 위치 업데이트 요청을 시작합니다.
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            )
        }
    }

    // 정기적인 위치 업데이트 요청
    fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
}
