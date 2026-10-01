package com.example

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

private const val OWNER_EMAIL = "yogeshtiwari0620@gmail.com"
private const val OWNER_PHONE = "9699276869"
private const val PLAN_DAYS = 60
private const val TRIAL_DAYS = 30

data class AccessState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val owner: Boolean = false,
    val status: String = "",
    val expiryMillis: Long = 0L,
    val message: String = ""
) {
    val active: Boolean get() = status == "active" && expiryMillis > System.currentTimeMillis()
    val trial: Boolean get() = status == "trial" && expiryMillis > System.currentTimeMillis()
    val canStartTest: Boolean get() = active || trial || owner
    val showAds: Boolean get() = !active && !owner
}

@Composable
fun SubscriptionGate(content: @Composable (Boolean, Boolean) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var state by remember { mutableStateOf(AccessState()) }
    var refresh by remember { mutableIntStateOf(0) }

    fun load() {
        val user = auth.currentUser
        if (user == null) { state = AccessState(false, false); return }
        if (user.email.equals(OWNER_EMAIL, true)) {
            state = AccessState(false, true, true, "owner")
            return
        }
        db.collection("subscriptions").document(user.uid).get()
            .addOnSuccessListener { d ->
                val status = d.getString("status") ?: "pending"
                val expiry = d.getTimestamp("expiryAt")?.toDate()?.time ?: 0L
                val effective = if ((status == "trial" || status == "active") && expiry <= System.currentTimeMillis()) "expired" else status
                state = AccessState(false, true, false, effective, expiry)
            }
            .addOnFailureListener { state = AccessState(false, true, false, "error", 0L, it.message ?: "Firebase error") }
    }

    LaunchedEffect(refresh, auth.currentUser?.uid) { load() }
    LaunchedEffect(state.status, state.expiryMillis) {
        if ((state.status == "trial" || state.status == "active") && state.expiryMillis > 0) {
            while (System.currentTimeMillis() < state.expiryMillis) delay(30_000)
            load()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Checking account…") }
    } else if (!state.signedIn) {
        AuthScreen { refresh++ }
    } else if (state.owner) {
        OwnerPanel { auth.signOut(); refresh++ }
    } else {
        Box(Modifier.fillMaxSize()) {
            content(state.canStartTest, state.showAds)
            if (state.showAds) BannerAd(Modifier.align(Alignment.BottomCenter))
            if (!state.canStartTest) SubscriptionNotice { refresh++ }
        }
    }
}

@Composable
private fun AuthScreen(onDone: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var signup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun createData(uid: String, mail: String) {
        val now = Calendar.getInstance()
        val trialEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, TRIAL_DAYS) }
        val user = mapOf("uid" to uid, "email" to mail, "role" to "user", "createdAt" to now.time, "trialEndsAt" to trialEnd.time)
        val sub = mapOf("uid" to uid, "email" to mail, "status" to "trial", "createdAt" to now.time, "expiryAt" to trialEnd.time)
        db.collection("users").document(uid).set(user)
            .continueWithTask { db.collection("subscriptions").document(uid).set(sub) }
            .addOnSuccessListener { busy = false; onDone() }
            .addOnFailureListener { busy = false; message = it.message ?: "Could not create account data" }
    }

    fun submit() {
        if (email.trim().isBlank() || password.length < 6) { message = "Email aur 6+ character password required"; return }
        busy = true
        if (signup) {
            auth.createUserWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener { createData(it.user!!.uid, email.trim()) }
                .addOnFailureListener { busy = false; message = it.message ?: "Signup failed" }
        } else {
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener { busy = false; onDone() }
                .addOnFailureListener { busy = false; message = it.message ?: "Login failed" }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("WiFi Manager", style = MaterialTheme.typography.headlineMedium)
        Text(if (signup) "Create account • 1 month free" else "Login")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(14.dp))
        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Please wait…" else if (signup) "Sign Up" else "Login") }
        TextButton(onClick = { signup = !signup; message = "" }) { Text(if (signup) "Already have account? Login" else "New user? Sign Up") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SubscriptionNotice(onRefresh: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Subscription required", style = MaterialTheme.typography.titleMedium)
            Text(when (state.status) { "pending" -> "Approval pending • ₹30 / 2 months"; "rejected" -> "Request rejected • ₹30 / 2 months"; "expired" -> "Subscription expired • ₹30 / 2 months"; else -> "₹30 / 2 months" })
            Text("Owner: $OWNER_PHONE")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val text = "Mujhe app ka subscription chahiye. Payment ₹30 / 2 months ke liye kaha karna hai?"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$OWNER_PHONE?text=" + Uri.encode(text))))
                }) { Text("Contact Owner") }
                OutlinedButton(onClick = {
                    val u = FirebaseAuth.getInstance().currentUser ?: return@OutlinedButton
                    FirebaseFirestore.getInstance().collection("paymentRequests").document(u.uid)
                        .set(mapOf("uid" to u.uid, "email" to (u.email ?: ""), "status" to "pending", "requestedAt" to java.util.Date()), SetOptions.merge())
                        .continueWithTask { FirebaseFirestore.getInstance().collection("subscriptions").document(u.uid).set(mapOf("status" to "pending"), SetOptions.merge()) }
                        .addOnCompleteListener { onRefresh() }
                }) { Text("Request Approval") }
            }
        }
    }
}

@Composable
private fun OwnerPanel(onLogout: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var docs by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    fun load() { db.collection("subscriptions").get().addOnSuccessListener { docs = it.documents } }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Owner Panel", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text("Logout") }
        }
        Text("Users / subscriptions")
        if (message.isNotBlank()) Text(message)
        docs.forEach { d ->
            val email = d.getString("email") ?: d.id
            val status = d.getString("status") ?: "pending"
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(email, style = MaterialTheme.typography.titleSmall)
                    Text("Status: $status")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val start = java.util.Date()
                            val end = Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_YEAR, PLAN_DAYS) }.time
                            db.collection("subscriptions").document(d.id).set(mapOf("status" to "active", "startedAt" to start, "expiryAt" to end), SetOptions.merge())
                                .addOnSuccessListener { message = "Approved: $email"; load() }
                        }) { Text("Approve 2 Months") }
                        OutlinedButton(onClick = {
                            db.collection("subscriptions").document(d.id).set(mapOf("status" to "rejected"), SetOptions.merge())
                                .addOnSuccessListener { message = "Rejected: $email"; load() }
                        }) { Text("Disable") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-1835719222780575/4072312143"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
