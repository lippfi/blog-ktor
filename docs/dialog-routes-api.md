# Dialog Routes API Documentation

## Base URL
All endpoints are prefixed with `/dialog`

## Authentication
All endpoints require authentication via Bearer token.
All endpoints are marked with 🔒 as they require authentication.

## Data Types

### Dialog
```json
{
  "id": "UUID",
  "user": {
    "login": "string",
    "nickname": "string",
    "avatarUri": "string"
  },
  "lastMessage": {
    "id": "UUID",
    "dialogId": "UUID",
    "sender": {
      "login": "string",
      "nickname": "string",
      "avatarUri": "string"
    },
    "content": "string",
    "timestamp": "YYYY-MM-DDTHH:mm:ss",
    "isRead": true
  },
  "isUnread": false
}
```

### Message
```json
{
  "id": "UUID",
  "dialogId": "UUID",
  "sender": {
    "login": "string",
    "nickname": "string",
    "avatarUri": "string"
  },
  "content": "string",
  "timestamp": "YYYY-MM-DDTHH:mm:ss",
  "isRead": true
}
```

### Pageable Parameters
All list endpoints support pagination with the following query parameters:
- `page` (optional, default: 0): Page number
- `size` (optional, default: 10): Number of items per page
- `direction` (optional):
  - "ASC" - Ascending order
  - "DESC" - Descending order (default for dialogs)
  - Default varies by endpoint

## Endpoints

### 🔒 Get Dialog List
Retrieves a paginated list of user's dialogs.

- **Method**: GET
- **Path**: `/dialog/list`
- **Query Parameters**:
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
  - `direction` (optional): Sort direction (default: DESC)
- **Response**: Array of Dialog objects
```json
[
  {
    "id": "UUID",
    "user": {
      "login": "string",
      "nickname": "string",
      "avatarUri": "string"
    },
    "lastMessage": {
      "id": "UUID",
      "dialogId": "UUID",
      "sender": {
        "login": "string",
        "nickname": "string",
        "avatarUri": "string"
      },
      "content": "string",
      "timestamp": "YYYY-MM-DDTHH:mm:ss",
      "isRead": true
    },
    "isUnread": false
  }
]
```

### 🔒 Get Dialog Messages
Retrieves messages from a specific dialog.

- **Method**: GET
- **Path**: `/dialog/dialog-messages`
- **Query Parameters**:
  - `dialogId` (required): UUID of the dialog
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
  - `direction` (optional): Sort direction (default: ASC)
- **Response**: Array of Message objects
```json
[
  {
    "id": "UUID",
    "dialogId": "UUID",
    "sender": {
      "login": "string",
      "nickname": "string",
      "avatarUri": "string"
    },
    "content": "string",
    "timestamp": "YYYY-MM-DDTHH:mm:ss",
    "isRead": true
  }
]
```

### 🔒 Get Messages with User
Retrieves messages exchanged with a specific user.

- **Method**: GET
- **Path**: `/dialog/messages`
- **Query Parameters**:
  - `login` (required): Login of the user
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
  - `direction` (optional): Sort direction (default: ASC)
- **Response**: Array of Message objects (same format as in Get Dialog Messages)

### 🔒 Send Message
Sends a message to a user.

- **Method**: POST
- **Path**: `/dialog/message`
- **Query Parameters**:
  - `login` (required): Login of the recipient
- **Request Body**:
```json
{
  "avatarUri": "string",
  "content": "string"
}
```
- **Response**: Created Message object (same format as in Message data type)

## Response Status Codes
- 200: Successful operation
- 400: Bad request (missing or invalid parameters)
- 401: Unauthorized (missing or invalid authentication token)
- 403: Forbidden (insufficient permissions)
- 404: Resource not found
- 500: Internal server error

## Error Responses
When an error occurs, the response will contain a text message describing the error.
Example:
```json
{
  "message": "dialogId is required"
}
```

## Notes
1. Messages in a dialog are ordered by timestamp.
2. The default sort order for dialog list is DESC (newest first), while for messages it's ASC (oldest first).
3. When sending a message to a user for the first time, a new dialog is automatically created.
4. The `lastMessage` field in Dialog objects may be null if no messages have been exchanged yet.