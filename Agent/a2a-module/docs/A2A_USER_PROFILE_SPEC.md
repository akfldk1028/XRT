# A2A User Profile System Specification

## Overview
This document defines the data format and communication protocol agreed upon between the Frontend and Backend agents for the User Profile System.

## Data Format Agreement

### User Profile Object
Both Frontend and Backend agents have agreed on the following user profile data structure:

```json
{
  "id": "string",           // Unique user identifier (UUID or numeric string)
  "name": "string",         // User's full name
  "email": "string",        // User's email address
  "avatar": "string",       // URL to user's avatar image
  "bio": "string",          // User's biography/description
  "joinDate": "datetime",   // When the user joined (ISO 8601 format)
  "role": "string",         // User role (user|admin|developer|designer)
  "createdAt": "datetime"   // Record creation timestamp
}
```

## API Endpoints

### Backend (Port 8021)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Get all users |
| GET | `/api/users/{id}` | Get specific user by ID |
| POST | `/api/users` | Create new user |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |

### Response Format

#### Success Response
```json
{
  "id": "1",
  "name": "John Doe",
  "email": "john.doe@example.com",
  "avatar": "https://ui-avatars.com/api/?name=John+Doe",
  "bio": "Full-stack developer",
  "joinDate": "2024-01-15T10:30:00Z",
  "role": "admin",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Error Response
```json
{
  "detail": "User not found"
}
```

## Frontend Components

### UserProfile Component
- **Location**: `agents/claude_cli/frontend/UserProfile.jsx`
- **Features**:
  - Fetches user data from Backend API
  - Displays complete user profile
  - Handles loading and error states
  - Auto-generates avatar if not provided

### UserInfo Component
- **Location**: `agents/claude_cli/frontend/UserInfo.jsx`
- **Features**:
  - Simple user info display
  - Accepts props directly
  - Lightweight presentation component

## A2A Communication Protocol

### 1. Data Format Negotiation
Frontend and Backend agents communicate to establish data format:

```javascript
// Frontend Request
{
  "request": "data_format",
  "sender": "frontend_agent",
  "purpose": "user_profile_display"
}

// Backend Response
{
  "response": "data_format",
  "sender": "backend_agent",
  "format": { /* User Profile Schema */ },
  "endpoints": { /* Available API endpoints */ }
}
```

### 2. Data Flow
1. Frontend component mounts
2. Frontend sends GET request to Backend API
3. Backend returns user data in agreed format
4. Frontend renders the profile

## Mock Data
Backend initializes with 3 mock users for testing:
- John Doe (admin)
- Jane Smith (designer)  
- Bob Wilson (developer)

## Testing

### Start Backend API
```bash
python agents/claude_cli/backend/user_profile_api.py
```

### Test Integration
```bash
python test_user_profile_system.py
```

### React Integration
```jsx
import UserProfile from './agents/claude_cli/frontend/UserProfile';

function App() {
  return <UserProfile userId="1" />;
}
```

## Benefits of A2A Coordination

1. **Type Safety**: Both agents know exact data structure
2. **Consistency**: Unified data format across system
3. **Maintainability**: Changes coordinated between agents
4. **Scalability**: Easy to add new fields with agreement
5. **Error Prevention**: Mismatched data formats caught early

## Future Enhancements

- [ ] Add WebSocket support for real-time updates
- [ ] Implement data validation on both ends
- [ ] Add pagination for user lists
- [ ] Support batch operations
- [ ] Add user search functionality