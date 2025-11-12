# JWT Integration Plan for Mobile App

## Current State Analysis

### Mobile App (Current)
- ✅ Sends `userId` in request body for all API calls
- ✅ Stores `userId` in Intent extras when navigating between activities
- ✅ No token-based authentication
- ✅ Login/Register responses don't include JWT token yet

### Backend (IntegrateJWT Branch)
- ✅ Generates JWT tokens on login/register
- ✅ Has `/api/checkjwt` endpoint for token validation
- ✅ Has `validateJWT()` utility function
- ⚠️ Routes still accept `userId` from body (not fully migrated to JWT)

---

## Implementation Plan

### Phase 1: Backend Preparation (Complete First)

#### 1.1 Update Backend Routes to Accept JWT
**Files to modify:**
- `classRoutes.js`
- `attendanceRoutes.js`
- `recordsRoutes.js`

**Changes needed:**
1. Create JWT middleware function that:
   - Extracts token from `Authorization: Bearer <token>` header
   - Validates token using `validateJWT()`
   - Extracts user info (id, role) from token
   - Attaches user info to `req.user`
   - Returns 401 if token invalid/missing

2. Apply middleware to protected routes:
   - `/api/fetchclasses` - Extract userId from JWT
   - `/api/createclass` - Extract instructorId from JWT
   - `/api/preparebroadcast` - Extract userId from JWT
   - `/api/endbroadcast` - Extract userId from JWT
   - `/api/newsecret` - Extract userId from JWT
   - `/api/joinclass` - Extract userId from JWT
   - `/api/leaveClass` - Extract userId from JWT
   - `/api/markmehere` - Extract userId from JWT
   - `/api/deleteClass` - Extract userId from JWT
   - `/api/fetchteacherrecords` - Extract userId from JWT
   - `/api/fetchstudentrecords` - Extract userId from JWT
   - `/api/removesecret` - Extract userId from JWT

3. Remove `userId` from request body requirements (get from JWT instead)

**Example middleware:**
```javascript
function jwtMiddleware(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1]; // Bearer <token>
  
  const decoded = validateJWT(token);
  if (decoded.error) {
    return res.status(401).json({ error: decoded.error });
  }
  
  req.user = decoded; // { id, firstName, lastName, role }
  next();
}
```

---

### Phase 2: Mobile App - Core JWT Infrastructure

#### 2.1 Update Data Models
**Files to modify:**
- `LoginResponse.kt`
- `RegisterResponse.kt`

**Changes:**
```kotlin
// LoginResponse.kt
data class LoginResponse(
    val id: String,
    val firstName: String,
    val lastName: String,
    val error: String,
    val role: String,
    val token: String? = null  // Add JWT token field
)

// RegisterResponse.kt
data class RegisterResponse(
    val error: String,
    val token: String? = null  // Add JWT token field
)
```

#### 2.2 Create JWT Token Manager
**New file:** `JwtTokenManager.kt`

**Purpose:** Centralized JWT token storage and retrieval using SharedPreferences

**Features:**
- Store token securely
- Retrieve token
- Clear token (logout)
- Check if token exists
- Get token expiration (optional)

**Implementation:**
```kotlin
object JwtTokenManager {
    private const val PREFS_NAME = "JWT_PREFS"
    private const val KEY_JWT_TOKEN = "jwt_token"
    
    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JWT_TOKEN, token)
            .apply()
    }
    
    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JWT_TOKEN, null)
    }
    
    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_JWT_TOKEN)
            .apply()
    }
    
    fun hasToken(context: Context): Boolean {
        return getToken(context) != null
    }
}
```

#### 2.3 Create JWT Interceptor
**New file:** `JwtInterceptor.kt`

**Purpose:** Automatically add JWT token to all API requests

**Implementation:**
```kotlin
class JwtInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = JwtTokenManager.getToken(context)
        
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        return chain.proceed(newRequest)
    }
}
```

#### 2.4 Update RetrofitClient
**File:** `RetrofitClient.kt`

**Changes:**
- Add `JwtInterceptor` to OkHttpClient
- Pass application context to interceptor

**Updated code:**
```kotlin
object RetrofitClient {
    private const val BASE_URL = "https://lp.ilovenarwhals.xyz/"
    
    // Need application context - can be passed from Application class or Activity
    private var applicationContext: Context? = null
    
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }
    
    private val jwtInterceptor: Interceptor by lazy {
        JwtInterceptor(applicationContext ?: throw IllegalStateException("RetrofitClient not initialized"))
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(jwtInterceptor)  // Add JWT interceptor
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // ... rest of code
}
```

---

### Phase 3: Mobile App - Update Authentication Flow

#### 3.1 Update Login Flow
**File:** `MainActivity.kt`

**Changes:**
1. Store JWT token after successful login
2. Remove `USER_ID` from Intent extras (get from token instead)
3. Handle token storage

**Updated code:**
```kotlin
override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
    if (response.isSuccessful && response.body() != null) {
        val loginResponse = response.body()!!
        
        if (loginResponse.id.isNotEmpty() && loginResponse.id != "-1") {
            // Store JWT token
            loginResponse.token?.let { token ->
                JwtTokenManager.saveToken(this@MainActivity, token)
            }
            
            // Navigate without passing userId in Intent
            if (loginResponse.role.equals("teacher", ignoreCase = true)) {
                val intent = Intent(this@MainActivity, TeacherClassListActivity::class.java)
                // No need to pass USER_ID - will get from JWT
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this@MainActivity, StudentClassListActivity::class.java)
                // No need to pass USER_ID - will get from JWT
                startActivity(intent)
                finish()
            }
        }
    }
}
```

#### 3.2 Update Register Flow
**File:** `RegisterPage.kt` (or similar)

**Changes:**
1. Store JWT token after successful registration
2. Navigate to appropriate class list

---

### Phase 4: Mobile App - Update API Calls

#### 4.1 Remove userId from Request Bodies
**Files to modify:**
- `FetchClassesRequest.kt` - Remove `userId` field
- `PrepareBroadcastRequest.kt` - Remove `userId` field
- `EndBroadcastRequest.kt` - Remove `userId` field
- `NewSecretRequest.kt` - Remove `userId` field
- `JoinClassRequest.kt` - Remove `userId` field
- `LeaveClassRequest.kt` - Remove `userId` field
- `MarkAttendanceRequest.kt` - Remove `userId` field
- `DeleteClassRequest.kt` - Remove `userId` field
- `FetchTeacherRecordsRequest.kt` - Remove `userId` field (if exists)
- `FetchStudentRecordsRequest.kt` - Remove `userId` field
- `RemoveSecretRequest.kt` - Remove `userId` field

**Example:**
```kotlin
// Before
data class FetchClassesRequest(
    val userId: String
)

// After
data class FetchClassesRequest(
    // Empty - userId comes from JWT token
)
```

#### 4.2 Update Activities to Not Pass userId
**Files to modify:**
- `StudentClassListActivity.kt`
- `TeacherClassListActivity.kt`
- `StudentAttendanceActivity.kt`
- `TeacherClassDetailActivity.kt`
- All other activities that use `userId`

**Changes:**
- Remove `userId` from Intent extras
- Remove `userId` from class variables (get from JWT when needed)
- Update API calls to not include `userId` in request body

---

### Phase 5: Mobile App - Handle Token Expiration

#### 5.1 Create Token Validation Utility
**New file:** `JwtValidator.kt`

**Purpose:** Validate JWT token and handle expiration

**Implementation:**
```kotlin
object JwtValidator {
    fun isTokenExpired(token: String): Boolean {
        return try {
            // Decode JWT without verification (just to check expiration)
            val parts = token.split(".")
            if (parts.size != 3) return true
            
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)
            val exp = json.getLong("exp")
            val currentTime = System.currentTimeMillis() / 1000
            
            exp < currentTime
        } catch (e: Exception) {
            true
        }
    }
    
    fun decodeToken(token: String): Map<String, Any>? {
        return try {
            val parts = token.split(".")
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)
            mapOf(
                "id" to json.getString("id"),
                "firstName" to json.getString("firstName"),
                "lastName" to json.getString("lastName"),
                "role" to json.getString("role")
            )
        } catch (e: Exception) {
            null
        }
    }
}
```

#### 5.2 Add Response Interceptor for 401 Errors
**File:** `JwtInterceptor.kt` (update)

**Changes:**
- Intercept 401 responses
- Clear token and redirect to login

**Updated code:**
```kotlin
class JwtInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = JwtTokenManager.getToken(context)
        
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        val response = chain.proceed(newRequest)
        
        // Handle 401 Unauthorized (token expired/invalid)
        if (response.code == 401) {
            JwtTokenManager.clearToken(context)
            // Can broadcast intent to notify activities to redirect to login
            val intent = Intent("com.example.bhereucf.TOKEN_EXPIRED")
            context.sendBroadcast(intent)
        }
        
        return response
    }
}
```

#### 5.3 Update Activities to Listen for Token Expiration
**Files:** All activities that make API calls

**Changes:**
- Register BroadcastReceiver for token expiration
- Redirect to login when token expires

---

### Phase 6: Testing & Validation

#### 6.1 Test Scenarios
1. ✅ Login → Token stored → API calls include token
2. ✅ Register → Token stored → Navigate to class list
3. ✅ API calls work without userId in body
4. ✅ Token expiration → Redirect to login
5. ✅ Logout → Token cleared → Cannot make API calls
6. ✅ App restart → Token persists → Still logged in
7. ✅ Invalid token → 401 response → Redirect to login

#### 6.2 Backend Validation
1. ✅ All routes validate JWT token
2. ✅ User info extracted from token (not body)
3. ✅ 401 returned for invalid/missing tokens
4. ✅ Role-based access control works

---

## Implementation Order

### Step 1: Backend (Do First)
1. Create JWT middleware function
2. Update routes to use middleware
3. Remove userId from request body requirements
4. Test with Postman/curl

### Step 2: Mobile - Infrastructure
1. Create `JwtTokenManager.kt`
2. Create `JwtInterceptor.kt`
3. Update `RetrofitClient.kt`
4. Update data models (`LoginResponse`, `RegisterResponse`)

### Step 3: Mobile - Authentication
1. Update `MainActivity.kt` login flow
2. Update register flow
3. Test login/register stores token

### Step 4: Mobile - API Updates
1. Remove userId from all request data classes
2. Update all activities to not use userId from Intent
3. Test API calls work with JWT

### Step 5: Mobile - Error Handling
1. Add token expiration handling
2. Add 401 response handling
3. Add logout functionality

---

## Key Considerations

### Security
- ✅ Tokens stored in SharedPreferences (not encrypted, but acceptable for this use case)
- ✅ Consider encrypting tokens for production (Android Keystore)
- ✅ Token expiration (3 hours) handled

### Backward Compatibility
- ⚠️ Backend should support both old (userId in body) and new (JWT) methods during transition
- ⚠️ Or coordinate deployment so mobile and backend update together

### User Experience
- ✅ Seamless token refresh (if implemented)
- ✅ Clear error messages for expired tokens
- ✅ Auto-redirect to login on token expiration

---

## Files Summary

### New Files to Create
1. `JwtTokenManager.kt` - Token storage/retrieval
2. `JwtInterceptor.kt` - Add token to requests, handle 401
3. `JwtValidator.kt` - Token validation utilities (optional)

### Files to Modify
**Backend:**
- `classRoutes.js` - Add JWT middleware
- `attendanceRoutes.js` - Add JWT middleware
- `recordsRoutes.js` - Add JWT middleware

**Mobile:**
- `RetrofitClient.kt` - Add JWT interceptor
- `MainActivity.kt` - Store token on login
- `LoginResponse.kt` - Add token field
- `RegisterResponse.kt` - Add token field
- All request data classes - Remove userId
- All activities - Remove userId usage

---

## Estimated Effort

- **Backend:** 2-3 hours (middleware + route updates)
- **Mobile Infrastructure:** 1-2 hours (managers + interceptors)
- **Mobile Authentication:** 1 hour (login/register updates)
- **Mobile API Updates:** 2-3 hours (remove userId from all calls)
- **Error Handling:** 1 hour (401 handling)
- **Testing:** 2-3 hours

**Total:** ~10-12 hours

