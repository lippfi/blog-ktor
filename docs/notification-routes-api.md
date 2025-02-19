# Notification Routes API Documentation

## Base URL
All endpoints are prefixed with `/notifications` except for mention-related endpoints.

## Authentication
All endpoints require authentication via Bearer token.
All endpoints are marked with 🔒 as they require authentication.

## Data Types

### NotificationType
Enum with values:
- `COMMENT` - New comment on a post
- `NEW_POST` - New post in followed diary
- `COMMENT_REPLY` - Reply to a comment
- `POST_REACTION` - Reaction to a post
- `COMMENT_REACTION` - Reaction to a comment
- `POST_MENTION` - Mention in a post
- `COMMENT_MENTION` - Mention in a comment
- `PRIVATE_MESSAGE` - New private message
- `FRIEND_REQUEST` - New friend request
- `REPOST` - Repost of a post

### Notification
Base structure for all notifications:
```json
{
  "id": "UUID",
  "type": "COMMENT | NEW_POST | COMMENT_REPLY | POST_REACTION | COMMENT_REACTION | POST_MENTION | COMMENT_MENTION | PRIVATE_MESSAGE | FRIEND_REQUEST | REPOST"
}
```

#### Post-related Notifications
For types: NEW_POST, COMMENT, COMMENT_REPLY, POST_REACTION, COMMENT_REACTION, REPOST
```json
{
  "id": "UUID",
  "type": "NEW_POST | COMMENT | COMMENT_REPLY | POST_REACTION | COMMENT_REACTION | REPOST",
  "diaryLogin": "string",
  "postUri": "string"
}
```

#### Friend Request Notification
```json
{
  "id": "UUID",
  "type": "FRIEND_REQUEST",
  "senderLogin": "string",
  "requestId": "UUID"
}
```

#### Private Message Notification
```json
{
  "id": "UUID",
  "type": "PRIVATE_MESSAGE",
  "senderLogin": "string",
  "dialogId": "UUID"
}
```

## Endpoints

### 🔒 Get All Notifications
Retrieves all notifications for the current user.

- **Method**: GET
- **Path**: `/notifications`
- **Response**: Array of Notification objects (various types as described above)

### 🔒 Get Specific Notification
Retrieves a specific notification by ID.

- **Method**: GET
- **Path**: `/notifications`
- **Query Parameters**:
  - `id` (required): UUID of the notification
- **Response**: Notification object (one of the types described above)

### 🔒 Mark Notification as Read
Marks a specific notification as read.

- **Method**: POST
- **Path**: `/notifications/read`
- **Query Parameters**:
  - `id` (required): UUID of the notification
- **Response**: Text message "Notification marked as read"

### 🔒 Mark All Notifications as Read
Marks all notifications as read for the current user.

- **Method**: POST
- **Path**: `/notifications/read-all`
- **Response**: Text message "All notifications marked as read"

### 🔒 Notify About Post Mention
Notifies a user about being mentioned in a post.

- **Method**: POST
- **Path**: `/posts/mentions`
- **Query Parameters**:
  - `postId` (required): UUID of the post
  - `login` (required): Login of the mentioned user
- **Response**: 
  - Success: Text message "User notified about mention in post"
  - Error: Error message describing the failure

### 🔒 Notify About Comment Mention
Notifies a user about being mentioned in a comment.

- **Method**: POST
- **Path**: `/comments/mentions`
- **Query Parameters**:
  - `commentId` (required): UUID of the comment
  - `login` (required): Login of the mentioned user
- **Response**: 
  - Success: Text message "User notified about mention in comment"
  - Error: Error message describing the failure

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
  "message": "Invalid notification ID"
}
```

## Notes
1. All notifications contain a unique ID and type.
2. Different notification types contain different additional fields based on their purpose.
3. Mention notifications can fail if the user doesn't exist or other validation fails.
4. Reading a notification doesn't delete it, it only marks it as read.