# Storage Routes API Documentation

## Base URL
All endpoints are prefixed with `/storage`

## Authentication
All endpoints require authentication via Bearer token. The token can be obtained through the user sign-in endpoint.
All endpoints are marked with 🔒 as they require authentication.

## Endpoints

### File Upload

#### 🔒 Upload Files
- **Method**: POST
- **Path**: `/storage/upload`
- **Request Body**: Multipart form data containing one or more files
  - Each file part should include:
    - Original filename with extension
    - File content
- **Supported File Types**:
  - Images: .jpg, .jpeg, .png, .gif
  - Videos: .mp4, .webm
  - Audio: .mp3
  - Styles: .css
  - Other file types are marked as FileType.OTHER
- **Response**: Array of stored file objects
```json
[
  {
    "id": "uuid",
    "ownerId": "uuid",
    "extension": "string",
    "type": "IMAGE | VIDEO | AUDIO | STYLE | OTHER",
    "url": "string"
  }
]
```

## Error Responses
All endpoints may return the following errors:
- 400 Bad Request: When the file upload is invalid or missing
- 401 Unauthorized: When authentication is required but not provided
- 403 Forbidden: When the user doesn't have permission for the operation
- 413 Payload Too Large: When the uploaded file exceeds size limits
- 415 Unsupported Media Type: When the file type is not supported
- 500 Internal Server Error: When an unexpected error occurs

Error responses will contain a text message describing the error.

## File Size Limits
- Images: Maximum size depends on server configuration
- Videos: Maximum size depends on server configuration
- Audio: Maximum size depends on server configuration
- Style files: Maximum size depends on server configuration

## Notes
- Files are stored on the server and accessible via the returned URL
- Each file gets a unique UUID upon upload
- Files are associated with the user who uploaded them (ownerId)
- The server automatically detects file type based on extension
- URLs are generated for each file and included in the response
- Multiple files can be uploaded in a single request