# Diary Routes API Documentation

## Base URL
All endpoints are prefixed with `/diary`

## Authentication
Some endpoints require authentication via Bearer token.
Endpoints marked with 🔒 require authentication.
Endpoints without a 🔒 mark are publicly accessible.

## Data Types

### DiaryInfo
```json
{
  "name": "string",
  "subtitle": "string",
  "defaultReadGroup": "UUID",
  "defaultCommentGroup": "UUID"
}
```

## Endpoints

### Get Diary Style Text
Retrieves the CSS style text for a diary.

- **Method**: GET
- **Path**: `/diary/style-text`
- **Query Parameters**:
  - `diaryLogin` (required): Login of the diary owner
- **Response**: CSS style text (string)

### Get Diary Style File
Retrieves the URL of the diary's style file.

- **Method**: GET
- **Path**: `/diary/style-file`
- **Query Parameters**:
  - `diaryLogin` (required): Login of the diary owner
- **Response**: 
  - Success: Style file URL (string)
  - Not Found: Text message "Diary style file not found"

### 🔒 Set Diary Style
Sets the CSS style for the user's diary.

- **Method**: POST
- **Path**: `/diary/set-style`
- **Request Body**: CSS style content (string)
- **Response**: Text message "Diary style set successfully"

### 🔒 Update Diary Info
Updates the diary information.

- **Method**: POST
- **Path**: `/diary/update-diary-info`
- **Query Parameters**:
  - `login` (required): Login of the diary owner
- **Request Body**:
```json
{
  "name": "string",
  "subtitle": "string",
  "defaultReadGroup": "UUID",
  "defaultCommentGroup": "UUID"
}
```
- **Response**: Text message "Diary info updated successfully"

## Response Status Codes
- 200: Successful operation
- 400: Bad request (missing or invalid parameters)
- 401: Unauthorized (missing or invalid authentication token)
- 403: Forbidden (insufficient permissions)
- 404: Resource not found (when style file doesn't exist)
- 500: Internal server error

## Error Responses
When an error occurs, the response will contain a text message describing the error.
Example:
```json
{
  "message": "Invalid diary login"
}
```

## Notes
1. The style text and file endpoints are public and can be accessed without authentication.
2. The style file endpoint returns a URL that can be used to download the style file.
3. When setting a diary style, the content should be valid CSS.
4. The defaultReadGroup and defaultCommentGroup in DiaryInfo should be valid UUIDs of existing access groups.