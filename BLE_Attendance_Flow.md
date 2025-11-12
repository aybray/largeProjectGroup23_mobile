# Bluetooth BLE Attendance System Flow

```mermaid
sequenceDiagram
    participant Teacher as Teacher Device<br/>(Advertiser)
    participant BLE as BLE Radio<br/>(Broadcast)
    participant Student1 as Student Device 1<br/>(Scanner)
    participant Student2 as Student Device 2<br/>(Scanner)
    participant API as Backend API

    Note over Teacher: Teacher starts class
    Teacher->>API: POST /api/preparebroadcast<br/>(classId, userId)
    API-->>Teacher: Returns secret hash
    
    Note over Teacher: Format: {ClassCode}{Section}-{SecretHash}
    Teacher->>BLE: Start BLE Advertising<br/>Broadcast Name: "COP4331001-a1b2c3d4"
    
    loop Continuous Broadcasting
        BLE-->>Student1: Broadcast detected<br/>"COP4331001-a1b2c3d4"
        BLE-->>Student2: Broadcast detected<br/>"COP4331001-a1b2c3d4"
    end
    
    Note over Student1: Student 1 scans for broadcasts
    Student1->>Student1: Check if broadcast matches<br/>classCode + section prefix
    
    alt Broadcast matches expected format
        Student1->>Student1: Extract secret: "a1b2c3d4"<br/>Store broadcast name
        Student1->>Student1: Enable "Mark Here" button
        Note over Student1: User presses "Mark Here"
        Student1->>Student1: Check if broadcast name<br/>already used (duplicate check)
        
        alt First time marking
            Student1->>API: POST /api/markmehere<br/>(userId, classId, secret)
            API-->>Student1: Success: Attendance marked
            Student1->>Student1: Save broadcast name<br/>to SharedPreferences
            Student1->>Student1: Show confirmation screen
        else Already marked for this ping
            Student1->>Student1: Show error:<br/>"Already marked for this ping"
        end
    else Broadcast doesn't match
        Student1->>Student1: Ignore broadcast
    end
    
    Note over Teacher: Teacher generates new ping
    Teacher->>API: POST /api/newsecret<br/>(classId)
    API-->>Teacher: Returns new secret hash
    Teacher->>BLE: Update broadcast name<br/>"COP4331001-xyz789abc"
    
    Note over Student1: New broadcast detected
    BLE-->>Student1: New broadcast:<br/>"COP4331001-xyz789abc"
    Student1->>Student1: Compare with last used:<br/>"COP4331001-a1b2c3d4"
    Note over Student1: Different broadcast name<br/>→ Allow marking again
    Student1->>Student1: Enable "Mark Here" button
    
    Note over Teacher: Teacher stops class
    Teacher->>API: POST /api/endbroadcast<br/>(classId)
    Teacher->>BLE: Stop advertising
    BLE-->>Student1: No more broadcasts
    BLE-->>Student2: No more broadcasts
```

## Simplified Flow Diagram

```mermaid
flowchart TD
    A[Teacher Starts Class] --> B[Backend Generates Secret Hash]
    B --> C[Teacher Device Advertises BLE<br/>Format: ClassCode+Section-Secret]
    C --> D[Student Devices Scan for Broadcasts]
    D --> E{Broadcast Matches<br/>Expected Format?}
    E -->|Yes| F[Extract Secret from Broadcast Name]
    E -->|No| D
    F --> G[Enable Mark Here Button]
    G --> H{Student Presses<br/>Mark Here?}
    H -->|Yes| I{Already Marked<br/>for This Ping?}
    H -->|No| D
    I -->|Yes| J[Show Error:<br/>Already Marked]
    I -->|No| K[Send Secret to Backend API]
    K --> L{Backend Validates<br/>Secret & Enrollment?}
    L -->|Valid| M[Mark Attendance Successfully]
    L -->|Invalid| N[Show Error Message]
    M --> O[Save Broadcast Name<br/>to SharedPreferences]
    O --> P[Show Confirmation Screen]
    J --> D
    N --> D
    P --> Q{Teacher Generates<br/>New Ping?}
    Q -->|Yes| R[New Secret Generated]
    R --> S[Broadcast Name Changes]
    S --> D
    Q -->|No| T[Continue Scanning]
    T --> D
```

## Key Components

### BLE Advertising (Teacher Side)
- **Format**: `{ClassCode}{Section}-{SecretHash}`
- **Example**: `COP4331001-a1b2c3d4`
- **Frequency**: Continuous broadcast every few seconds
- **Range**: ~10-30 meters (indoor)

### BLE Scanning (Student Side)
- **Mode**: Passive scanning (no connection required)
- **Filter**: Checks if broadcast name starts with `{ClassCode}{Section}-`
- **Detection**: Receives broadcast packets without pairing

### Duplicate Prevention
- **Storage**: SharedPreferences (persists across app restarts)
- **Key**: `lastUsedBroadcast_{classId}`
- **Logic**: Compare current broadcast name with last used
- **Result**: Same name = block, Different name = allow

### Security
- **Secret Hash**: Unique per ping/session
- **Validation**: Backend verifies secret matches active session
- **Enrollment Check**: Backend verifies student is enrolled in class

