# Reaction Routes API Documentation

## Base URL
All endpoints are prefixed with `/reactions`

## Authentication
Some endpoints require authentication via Bearer token. The token can be obtained through the user sign-in endpoint.
Endpoints marked with 🔒 require authentication.

## Endpoints

### Reaction Management

#### Get All Reactions
- **Method**: GET
- **Path**: `/reactions`
- **Response**: Array of reaction objects
```json
[
  {
    "id": "uuid",
    "name": "string",
    "iconUrl": "string",
    "createdBy": "uuid",
    "createdAt": "timestamp"
  }
]
```

#### Get Basic Reactions
- **Method**: GET
- **Path**: `/reactions/basic`
- **Response**: Array of basic reaction objects
```json
[
  {
    "id": "uuid",
    "name": "string",
    "iconUrl": "string"
  }
]
```

#### Search Reactions
- **Method**: GET
- **Path**: `/reactions/search`
- **Query Parameters**:
  - `pattern` (optional): Search pattern, defaults to empty string
- **Response**: Array of matching reaction objects
```json
[
  {
    "id": "uuid",
    "name": "string",
    "iconUrl": "string"
  }
]
```

#### 🔒 Create Reaction
- **Method**: POST
- **Path**: `/reactions/create`
- **Query Parameters**:
  - `name` (required): Name of the reaction
- **Request Body**: Multipart form data with icon file
- **Response**: Created reaction object
```json
{
  "id": "uuid",
  "name": "string",
  "iconUrl": "string",
  "createdBy": "uuid",
  "createdAt": "timestamp"
}
```

#### 🔒 Delete Reaction
- **Method**: DELETE
- **Path**: `/reactions`
- **Query Parameters**:
  - `name` (required): Name of the reaction to delete
- **Response**: Text message "Reaction deleted successfully"

#### 🔒 Get User's Recent Reactions
- **Method**: GET
- **Path**: `/reactions/recent`
- **Query Parameters**:
  - `limit` (optional): Maximum number of reactions to return, defaults to 50
- **Response**: Array of recent reaction objects
```json
[
  {
    "id": "uuid",
    "name": "string",
    "iconUrl": "string",
    "usedAt": "timestamp"
  }
]
```

### Post Reactions

#### Add Post Reaction
- **Method**: POST
- **Path**: `/reactions/post-reaction`
- **Query Parameters**:
  - `login` (required): Diary login
  - `uri` (required): Post URI
  - `id` (required): Reaction UUID
- **Response**: Text message "Reaction added successfully"
- **Authentication**: Optional (affects viewer type)

#### Remove Post Reaction
- **Method**: DELETE
- **Path**: `/reactions/post-reaction`
- **Query Parameters**:
  - `login` (required): Diary login
  - `uri` (required): Post URI
  - `id` (required): Reaction UUID
- **Response**: Text message "Reaction removed successfully"
- **Authentication**: Optional (affects viewer type)

### Comment Reactions

#### Add Comment Reaction
- **Method**: POST
- **Path**: `/reactions/comment-reaction`
- **Query Parameters**:
  - `commentId` (required): Comment UUID
  - `reactionId` (required): Reaction UUID
- **Response**: Text message "Comment reaction added successfully"
- **Authentication**: Optional (affects viewer type)

#### Remove Comment Reaction
- **Method**: DELETE
- **Path**: `/reactions/comment-reaction`
- **Query Parameters**:
  - `commentId` (required): Comment UUID
  - `reactionId` (required): Reaction UUID
- **Response**: Text message "Comment reaction removed successfully"
- **Authentication**: Optional (affects viewer type)

## Error Responses
All endpoints may return the following errors:
- 400 Bad Request: When required parameters are missing or invalid
- 401 Unauthorized: When authentication is required but not provided
- 403 Forbidden: When the user doesn't have permission for the operation
- 404 Not Found: When the requested resource doesn't exist
- 500 Internal Server Error: When an unexpected error occurs

Error responses will contain a text message describing the error.