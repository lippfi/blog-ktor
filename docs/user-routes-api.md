# User Routes API Documentation

## Base URL
All endpoints are prefixed with `/user`

## Authentication
Most endpoints require authentication via Bearer token. The token can be obtained through the sign-in endpoint.
Endpoints marked with 🔒 require authentication.

## Endpoints

### Authentication

#### Sign Up
- **Method**: POST
- **Path**: `/user/sign-up`
- **Query Parameters**:
  - `invite-code` (optional): Invitation code
- **Request Body**:
```json
{
  "login": "string",
  "email": "string",
  "password": "string",
  "nickname": "string",
  "timezone": "string",
  "language": "EN | RU | KK | KK_CYRILLIC"
}
```
- **Response**: Text message "User signed up successfully"

#### Sign In
- **Method**: POST
- **Path**: `/user/sign-in`
- **Request Body**:
```json
{
  "login": "string",
  "password": "string"
}
```
- **Response**: Authentication token (string)

### User Information

#### Check Login Availability
- **Method**: GET
- **Path**: `/user/is-login-busy`
- **Query Parameters**:
  - `login` (required): Login to check
- **Response**: Boolean (as string)

#### Check Email Availability
- **Method**: GET
- **Path**: `/user/is-email-busy`
- **Query Parameters**:
  - `email` (required): Email to check
- **Response**: Boolean (as string)

#### Check Nickname Availability
- **Method**: GET
- **Path**: `/user/is-nickname-busy`
- **Query Parameters**:
  - `nickname` (required): Nickname to check
- **Response**: Boolean (as string)

#### 🔒 Create Invite Code
- **Method**: GET
- **Path**: `/user/create-invite-code`
- **Response**: Invitation code (string)

#### 🔒 Update User
- **Method**: POST
- **Path**: `/user/update`
- **Request Body**:
```json
{
  "user": {
    "login": "string",
    "email": "string",
    "password": "string",
    "nickname": "string",
    "timezone": "string",
    "language": "EN | RU | KK | KK_CYRILLIC"
  },
  "oldPassword": "string"
}
```
- **Response**: Text message "User updated successfully"

#### 🔒 Update Additional Info
- **Method**: POST
- **Path**: `/user/update-additional-info`
- **Request Body**:
```json
{
  "sex": "MALE | FEMALE | UNDEFINED",
  "timezone": "string",
  "language": "EN | RU | KK | KK_CYRILLIC",
  "nsfw": "SHOW | HIDE | WARN",
  "birthDate": "YYYY-MM-DD"
}
```
- **Response**: Text message "User info updated successfully"

### Password Management

#### 🔒 Send Password Reset Email
- **Method**: POST
- **Path**: `/user/send-password-reset-email`
- **Request Body**: User identifier (string)
- **Response**: Text message "Password reset email sent"

#### 🔒 Reset Password
- **Method**: POST
- **Path**: `/user/reset-password`
- **Query Parameters**:
  - `code` (required): Reset code
- **Request Body**: New password (string)
- **Response**: Text message "Password reset successfully"

### Avatar Management

#### 🔒 Get Avatars
- **Method**: GET
- **Path**: `/user/avatars`
- **Response**: Array of avatar URLs

#### 🔒 Reorder Avatars
- **Method**: POST
- **Path**: `/user/reorder-avatars`
- **Request Body**: Array of UUID strings
- **Response**: Text message "Avatars reordered successfully"

#### 🔒 Add Avatar
- **Method**: POST
- **Path**: `/user/add-avatar`
- **Request Body**: Multipart form data with image file
- **Response**: Text message "Avatar added successfully"

#### 🔒 Delete Avatar
- **Method**: DELETE
- **Path**: `/user/delete-avatar`
- **Query Parameters**:
  - `uri` (required): Avatar URI to delete
- **Response**: Text message "Avatar deleted successfully"

### Notification Settings

#### 🔒 Update Notification Settings
- **Method**: POST
- **Path**: `/user/notification-settings`
- **Request Body**:
```json
{
  "notifyAboutComments": true,
  "notifyAboutReplies": true,
  "notifyAboutPostReactions": true,
  "notifyAboutCommentReactions": true,
  "notifyAboutPrivateMessages": true,
  "notifyAboutMentions": true
}
```
- **Response**: Text message "Notification settings updated successfully"

### Friend Management

#### 🔒 Send Friend Request
- **Method**: POST
- **Path**: `/user/friend-request`
- **Request Body**:
```json
{
  "toUser": "string",
  "message": "string",
  "label": "string"
}
```
- **Response**: Text message "Friend request sent successfully"

#### 🔒 Accept Friend Request
- **Method**: POST
- **Path**: `/user/friend-request/accept`
- **Query Parameters**:
  - `requestId` (required): UUID of the friend request
  - `label` (optional): Label for the friendship
- **Response**: Text message "Friend request accepted"

#### 🔒 Decline Friend Request
- **Method**: POST
- **Path**: `/user/friend-request/decline`
- **Query Parameters**:
  - `requestId` (required): UUID of the friend request
- **Response**: Text message "Friend request declined"

#### 🔒 Cancel Friend Request
- **Method**: DELETE
- **Path**: `/user/friend-request`
- **Query Parameters**:
  - `requestId` (required): UUID of the friend request
- **Response**: Text message "Friend request cancelled"

#### 🔒 Get Sent Friend Requests
- **Method**: GET
- **Path**: `/user/friend-requests/sent`
- **Response**:
```json
[
  {
    "id": "UUID",
    "user": {
      "login": "string",
      "nickname": "string",
      "avatarUri": "string"
    },
    "message": "string",
    "label": "string",
    "createdAt": "YYYY-MM-DDTHH:mm:ss"
  }
]
```

#### 🔒 Get Received Friend Requests
- **Method**: GET
- **Path**: `/user/friend-requests/received`
- **Response**: Same as "Get Sent Friend Requests"

#### 🔒 Get Friends
- **Method**: GET
- **Path**: `/user/friends`
- **Response**: Array of friend objects

#### 🔒 Remove Friend
- **Method**: DELETE
- **Path**: `/user/friends`
- **Query Parameters**:
  - `login` (required): Login of the friend to remove
- **Response**: Text message "Friend removed successfully"

## Response Status Codes
- 200: Successful operation
- 400: Bad request (missing or invalid parameters)
- 401: Unauthorized (missing or invalid authentication token)
- 404: Resource not found
- 500: Internal server error

## Data Types

### Language
Enum with values:
- `RU` - Russian
- `EN` - English
- `KK` - Kazakh (Latin)
- `KK_CYRILLIC` - Kazakh (Cyrillic)

### Sex
Enum with values:
- `MALE`
- `FEMALE`
- `UNDEFINED`

### NSFWPolicy
Enum with values:
- `SHOW` - Show NSFW content
- `HIDE` - Hide NSFW content
- `WARN` - Show warning before displaying NSFW content