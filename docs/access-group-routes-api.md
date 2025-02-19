# Access Group Routes API Documentation

## Base URL
All endpoints are prefixed with `/access-groups` except for `/in-group` endpoint.

## Authentication
All endpoints require authentication via Bearer token except where noted.
Endpoints marked with 🔒 require authentication.
Endpoints marked with 🔓 have optional authentication.

## Data Types

### AccessGroup
```json
{
  "diaryId": "UUID",
  "name": "string",
  "type": "EVERYONE | REGISTERED_USERS | PRIVATE | CUSTOM"
}
```

### AccessGroupType
Enum with values:
- `EVERYONE` - Access for all users (including non-registered)
- `REGISTERED_USERS` - Access for registered users only
- `PRIVATE` - Access for owner only
- `CUSTOM` - Access for specified group of users

## Endpoints

### 🔒 Get Access Groups
Retrieves all access groups for a specific diary.

- **Method**: GET
- **Path**: `/access-groups`
- **Query Parameters**:
  - `diary` (required): Login of the diary owner
- **Response**: Array of AccessGroup objects
```json
[
  {
    "diaryId": "UUID",
    "name": "string",
    "type": "EVERYONE | REGISTERED_USERS | PRIVATE | CUSTOM"
  }
]
```

### 🔒 Create Access Group
Creates a new access group for a diary.

- **Method**: POST
- **Path**: `/access-groups`
- **Query Parameters**:
  - `diary` (required): Login of the diary owner
- **Request Body**: Group name (string)
- **Response**: Text message "Access group created successfully"

### 🔒 Delete Access Group
Deletes an existing access group.

- **Method**: DELETE
- **Path**: `/access-groups`
- **Query Parameters**:
  - `groupId` (required): UUID of the access group to delete
- **Response**: Text message "Access group deleted successfully"

### 🔒 Add User to Group
Adds a user to an access group.

- **Method**: POST
- **Path**: `/access-groups/add-user`
- **Query Parameters**:
  - `groupId` (required): UUID of the access group
- **Request Body**: User login (string)
- **Response**: Text message "User added to group successfully"

### 🔒 Remove User from Group
Removes a user from an access group.

- **Method**: POST
- **Path**: `/access-groups/remove-user`
- **Query Parameters**:
  - `groupId` (required): UUID of the access group
- **Request Body**: User login (string)
- **Response**: Text message "User removed from group successfully"

### 🔓 Check User in Group
Checks if the current user is a member of the specified group. Authentication is optional for this endpoint.

- **Method**: GET
- **Path**: `/in-group`
- **Query Parameters**:
  - `groupId` (required): UUID of the access group to check
- **Response**: Boolean (as string)

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
  "message": "Missing diary login"
}
```