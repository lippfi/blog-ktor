# Post Routes API Documentation

## Base URL
All endpoints are prefixed with `/posts`

## Authentication
Some endpoints require authentication via Bearer token.
Endpoints marked with 🔒 require authentication.
Endpoints marked with 🔓 have optional authentication.

## Data Types

### Post
```json
{
  "id": "UUID",
  "uri": "string",
  "avatar": "string",
  "authorNickname": "string",
  "authorLogin": "string",
  "title": "string",
  "text": "string",
  "creationTime": "YYYY-MM-DDTHH:mm:ss",
  "isPreface": true,
  "isEncrypted": false,
  "classes": "string",
  "tags": ["string"],
  "isReactable": true,
  "reactions": [
    {
      "type": "string",
      "count": 0,
      "userReacted": false
    }
  ],
  "isCommentable": true,
  "comments": [
    {
      "id": "UUID",
      "avatar": "string",
      "authorNickname": "string",
      "authorLogin": "string",
      "text": "string",
      "creationTime": "YYYY-MM-DDTHH:mm:ss",
      "isReactable": true,
      "reactions": [
        {
          "type": "string",
          "count": 0,
          "userReacted": false
        }
      ],
      "reactionGroupId": "UUID"
    }
  ],
  "readGroupId": "UUID",
  "commentGroupId": "UUID",
  "reactionGroupId": "UUID",
  "commentReactionGroupId": "UUID"
}
```

### Comment
```json
{
  "id": "UUID",
  "avatar": "string",
  "authorNickname": "string",
  "authorLogin": "string",
  "text": "string",
  "creationTime": "YYYY-MM-DDTHH:mm:ss",
  "isReactable": true,
  "reactions": [
    {
      "type": "string",
      "count": 0,
      "userReacted": false
    }
  ],
  "reactionGroupId": "UUID"
}
```

### TagPolicy
Enum with values:
- `UNION` - Posts that have any of the specified tags (default)
- `INTERSECTION` - Posts that have all of the specified tags

## Endpoints

### 🔓 Get Diary Preface
Retrieves the preface post of a diary.

- **Method**: GET
- **Path**: `/posts/preface`
- **Query Parameters**:
  - `diary` (required): Login of the diary owner
- **Response**: 
  - Success: Post object
  - Not Found: Text message "No preface found"

### 🔓 Get Specific Post
Retrieves a specific post by author and URI.

- **Method**: GET
- **Path**: `/posts`
- **Query Parameters**:
  - `login` (required): Login of the post author
  - `uri` (required): URI of the post
- **Response**: Post object

### 🔓 Search Posts
Searches for posts with various criteria.

- **Method**: GET
- **Path**: `/posts/search`
- **Query Parameters**:
  - `author` (optional): Filter by author login
  - `diary` (optional): Filter by diary login
  - `text` (optional): Search in post text
  - `tags` (optional): Comma-separated list of tags
  - `tagPolicy` (optional): "UNION" or "INTERSECTION" (default: "UNION")
  - `from` (optional): Start date (YYYY-MM-DDTHH:mm:ss)
  - `to` (optional): End date (YYYY-MM-DDTHH:mm:ss)
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
- **Response**: Page of Post objects

### 🔓 Get All Posts
Retrieves all posts with pagination.

- **Method**: GET
- **Path**: `/posts`
- **Query Parameters**:
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
- **Response**: Page of Post objects

### 🔓 Get Discussed Posts
Retrieves posts with most discussions.

- **Method**: GET
- **Path**: `/posts/discussed`
- **Query Parameters**:
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
- **Response**: Page of Post objects

### 🔒 Get Followed Posts
Retrieves posts from followed users.

- **Method**: GET
- **Path**: `/posts/followed`
- **Query Parameters**:
  - `page` (optional): Page number (default: 0)
  - `size` (optional): Items per page (default: 10)
- **Response**: Page of Post objects

### 🔒 Get Post for Editing
Retrieves a post in edit format.

- **Method**: GET
- **Path**: `/posts`
- **Query Parameters**:
  - `id` (required): UUID of the post
- **Response**: 
```json
{
  "id": "UUID",
  "uri": "string",
  "avatar": "string",
  "title": "string",
  "text": "string",
  "readGroupId": "UUID",
  "commentGroupId": "UUID",
  "reactionGroupId": "UUID",
  "commentReactionGroupId": "UUID",
  "tags": ["string"],
  "classes": "string",
  "isEncrypted": false
}
```

### 🔒 Update Post
Updates an existing post.

- **Method**: PUT
- **Path**: `/posts`
- **Request Body**: Same as Get Post for Editing response
- **Response**: Updated Post object

### 🔒 Delete Post
Deletes a post.

- **Method**: DELETE
- **Path**: `/posts`
- **Query Parameters**:
  - `postId` (required): UUID of the post to delete
- **Response**: Text message "Post deleted successfully"

### 🔒 Add Comment
Adds a comment to a post.

- **Method**: POST
- **Path**: `/posts/comment`
- **Request Body**:
```json
{
  "postId": "UUID",
  "avatar": "string",
  "text": "string",
  "parentCommentId": "UUID",
  "reactionGroupId": "UUID"
}
```
- **Response**: Created Comment object

### 🔒 Update Comment
Updates an existing comment.

- **Method**: PUT
- **Path**: `/posts/comment`
- **Request Body**:
```json
{
  "id": "UUID",
  "postId": "UUID",
  "avatar": "string",
  "text": "string"
}
```
- **Response**: Updated Comment object

### 🔒 Delete Comment
Deletes a comment.

- **Method**: DELETE
- **Path**: `/posts/comment`
- **Query Parameters**:
  - `commentId` (required): UUID of the comment to delete
- **Response**: Text message "Comment deleted successfully"

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
  "message": "Missing diary parameter"
}
```

## Notes
1. Posts can be encrypted, in which case the content is only accessible to authorized users.
2. Comments can have reactions and can be nested (replies to other comments).
3. Access to posts and comments is controlled by access groups (readGroupId, commentGroupId, etc.).
4. Tag search supports two modes: UNION (any tag matches) and INTERSECTION (all tags must match).
5. The discussed posts endpoint returns posts sorted by comment count.