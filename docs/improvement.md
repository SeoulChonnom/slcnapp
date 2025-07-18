# Project Improvement Areas

This document outlines the identified areas for improvement in the Seoul Chonnom (slcnapp) Spring Boot application.

## 🔴 Critical Security Issues

### 1. **Configuration Security - Exposed Sensitive Information**

**Priority**: CRITICAL  
**File**: `src/main/resources/application.properties`

**Issues**:

- Database credentials hardcoded in plain text (lines 6-8)
- JWT secret key exposed in plain text (line 16)
- Redis password hardcoded (line 35)
- Database URL contains IP addresses instead of environment variables

**Recommendations**:

```properties
# Replace hardcoded values with environment variables
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.jwt.secretKey=${JWT_SECRET_KEY}
spring.data.redis.password=${REDIS_PASSWORD}
```

### 2. **File Upload Security - Path Traversal Vulnerability**

**Priority**: CRITICAL  
**File**: `src/main/java/com/seoulchonnom/slcnapp/trip/service/TripService.java:86`

**Issues**:

- Direct path concatenation without validation
- No protection against path traversal attacks (e.g., `../../../etc/passwd`)
- User-controlled input directly used in file path construction

**Recommendations**:

```java
// Add path validation
private void validatePath(String path) {
    if (path.contains("..") || path.contains("\\") || path.startsWith("/")) {
        throw new FilePathInvalidException();
    }
}

public ImageFile getImageFile(String path) {
    validatePath(path);
    Path filePath = Paths.get(directory).resolve(path).normalize();
    // Ensure the resolved path is still within the upload directory
    if (!filePath.startsWith(Paths.get(directory).normalize())) {
        throw new FilePathInvalidException();
    }
    // ... rest of the method
}
```

### 3. **CORS Configuration - Overly Permissive**

**Priority**: HIGH  
**File**: `src/main/java/com/seoulchonnom/slcnapp/common/config/WebConfig.java`

**Issues**:

- `allowedOrigins("*")` allows requests from any origin
- No credential restrictions specified
- Vulnerable to Cross-Site Request Forgery (CSRF)

**Recommendations**:

```java

@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins("https://yourdomain.com", "https://www.yourdomain.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

## 🟡 High Priority Issues

### 4. **Input Validation - Missing Validation**

**Priority**: HIGH  
**Files**: All controller classes, DTO classes

**Issues**:

- No input validation annotations on request parameters
- Missing `@Valid` annotations on request bodies
- Path parameters not validated for format/type
- No request size limits

**Recommendations**:

```java
// Add validation annotations to DTOs
@NotNull
@Size(min = 1, max = 50)
private String name;

@Email
private String email;

// Add validation to controllers
@PostMapping("/register")
public ResponseEntity<BaseResponse> registerUser(@Valid @RequestBody UserRegisterRequest request) {
    // ...
}

// Add path variable validation
@GetMapping("/{scheduleId}")
public ResponseEntity<BaseResponse> getSchedule(@PathVariable @Pattern(regexp = "^[0-9]+$") String scheduleId) {
    // ...
}
```

### 5. **Exception Handling - Incomplete Coverage**

**Priority**: HIGH  
**File**: `src/main/java/com/seoulchonnom/slcnapp/common/handler/CommonExceptionHandler.java`

**Issues**:

- Missing handlers for domain-specific exceptions
- Generic `Exception` catching in JWT validation
- Inconsistent error response formats
- Some exceptions may leak sensitive information

**Recommendations**:

```java
// Add missing exception handlers
@ExceptionHandler(ScheduleNotFoundException.class)
public ResponseEntity<ErrorResponse> handleScheduleNotFound(ScheduleNotFoundException e) {
    return new ResponseEntity<>(
            ErrorResponse.from(false, "Schedule not found"),
            HttpStatus.NOT_FOUND
    );
}

@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException e) {
    Map<String, String> errors = new HashMap<>();
    e.getBindingResult().getAllErrors().forEach(error -> {
        String fieldName = ((FieldError) error).getField();
        String errorMessage = error.getDefaultMessage();
        errors.put(fieldName, errorMessage);
    });
    return new ResponseEntity<>(
            ErrorResponse.from(false, "Validation failed", errors),
            HttpStatus.BAD_REQUEST
    );
}
```

### 6. **JWT Token Security Issues**

**Priority**: HIGH  
**File**: `src/main/java/com/seoulchonnom/slcnapp/user/JwtTokenProvider.java`

**Issues**:

- Broad exception catching masks security issues
- No token blacklisting mechanism
- Missing JWT ID (jti) claim for token uniqueness
- No token expiration validation logging

**Recommendations**:

```java
// Improve token validation
public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
        return true;
    } catch (ExpiredJwtException e) {
        logger.warn("Expired JWT token: {}", e.getMessage());
        return false;
    } catch (UnsupportedJwtException e) {
        logger.warn("Unsupported JWT token: {}", e.getMessage());
        return false;
    } catch (MalformedJwtException e) {
        logger.warn("Invalid JWT token: {}", e.getMessage());
        return false;
    } catch (SignatureException e) {
        logger.warn("Invalid JWT signature: {}", e.getMessage());
        return false;
    }
}

// Add token blacklisting
@Service
public class TokenBlacklistService {
    private final RedisTemplate<String, String> redisTemplate;

    public void blacklistToken(String token) {
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();
        long ttl = expiration.getTime() - System.currentTimeMillis();
        if (ttl > 0) {
            redisTemplate.opsForValue().set("blacklist:" + token, "true", ttl, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isBlacklisted(String token) {
        return redisTemplate.hasKey("blacklist:" + token);
    }
}
```

## 🟡 Medium Priority Issues

### 7. **Code Quality - High Complexity Methods**

**Priority**: MEDIUM  
**Files**: Multiple service classes

**Issues**:

- `UserService.issueToken()` has multiple responsibilities
- `TripService.registerTrip()` is too long and complex
- Large static factory methods in domain classes

**Recommendations**:

```java
// Extract login validation logic
private void validateLoginAttempt(User user) {
    if (user.getLoginFailCount() >= LOGIN_FAIL_LIMIT_COUNT) {
        throw new UserLoginFailCountOverException();
    }
}

private void handleLoginFailure(User user) {
    user.setLoginFailCount(user.getLoginFailCount() + 1);
    userRepository.save(user);
}

// Split file upload from business logic
@Service
public class FileUploadService {
    public TripFileUploadResult uploadTripFiles(MultipartFile logo, MultipartFile map1, MultipartFile map2) {
        // Handle file upload logic
    }
}
```

### 8. **Performance Issues**

**Priority**: MEDIUM  
**Files**: Entity classes, Service classes

**Issues**:

- Eager loading in User entity can cause N+1 queries
- No caching for frequently accessed data
- Potential memory issues with large file uploads

**Recommendations**:

```java
// Use lazy loading with explicit fetching
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
private List<Authority> authorities;

// Add caching for read-heavy operations
@Cacheable(value = "trips", key = "#date")
public TripInfoResponse getTripByDate(String date) {
    // ...
}

// Add file size validation
@Value("${app.max-file-size:10MB}")
private String maxFileSize;
```

### 9. **Testing Coverage**

**Priority**: MEDIUM  
**Files**: Test directory

**Issues**:

- Only basic application context test exists
- No unit tests for services, controllers, or repositories
- No integration tests for critical flows
- No test coverage reporting

**Recommendations**:

```java
// Add comprehensive unit tests
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterUserSuccessfully() {
        // Test implementation
    }
}

// Add integration tests
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserControllerIntegrationTest {
    // Integration test implementation
}
```

## 🟢 Low Priority Issues

### 10. **Code Documentation**

**Priority**: LOW  
**Files**: All public classes and methods

**Issues**:

- Missing JavaDoc for public methods and classes
- Mixed language comments (Korean/English)
- No API documentation beyond Swagger annotations

**Recommendations**:

```java
/**
 * Service class for managing user authentication and registration.
 * Handles JWT token generation, validation, and user management operations.
 */
@Service
public class UserService {

    /**
     * Registers a new user in the system.
     *
     * @param userRegisterRequest the user registration details
     * @throws InvalidUserException if user data is invalid
     */
    public void registerUser(UserRegisterRequest userRegisterRequest) {
        // ...
    }
}
```

### 11. **API Design Improvements**

**Priority**: LOW  
**Files**: Controller classes

**Issues**:

- No API versioning strategy
- Inconsistent response formats
- Missing HTTP status code variety
- No rate limiting

**Recommendations**:

```java
// Add API versioning
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    // ...
}

// Improve response handling
@PostMapping("/register")
public ResponseEntity<BaseResponse> registerUser(@Valid @RequestBody UserRegisterRequest request) {
    userService.registerUser(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(BaseResponse.from(true, "User registered successfully"));
}
```

### 12. **Configuration Management**

**Priority**: LOW  
**Files**: Configuration classes

**Issues**:

- Hardcoded values in configuration classes
- No environment-specific configurations
- Missing application monitoring and health checks

**Recommendations**:

```java
// Add environment-specific configurations
@Profile("production")
@Configuration
public class ProductionConfig {
    // Production-specific beans
}

// Add health check endpoints
@Component
public class CustomHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check database connectivity, external services, etc.
        return Health.up().withDetail("database", "Available").build();
    }
}
```

## Implementation Priority

1. **Immediate (Critical)**: Fix security vulnerabilities - hardcoded secrets, path traversal, CORS
2. **Week 1**: Add input validation, improve exception handling, enhance JWT security
3. **Week 2**: Add comprehensive testing, refactor complex methods
4. **Week 3**: Performance improvements, caching, documentation
5. **Week 4**: API versioning, monitoring, additional features

## Conclusion

The Seoul Chonnom application has a solid foundation with Spring Boot best practices, but requires immediate attention
to security vulnerabilities and gradual improvement in code quality, testing, and documentation. Addressing these issues
will significantly improve the application's security, maintainability, and reliability.