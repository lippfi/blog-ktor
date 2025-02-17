package fi.lipp.blog.model.exceptions

class FriendRequestAlreadyExistsException : BlogException(
    "Friend request already exists",
    409
)

class AlreadyFriendsException : BlogException(
    "Users are already friends",
    409
)

class FriendRequestNotFoundException : BlogException(
    "Friend request not found",
    404
)

class NotRequestRecipientException : BlogException(
    "User is not the recipient of this friend request",
    403
)

class NotFriendsException : BlogException(
    "Users are not friends",
    404
)

class NotRequestSenderException : BlogException(
    "User is not the sender of this friend request",
    403
)
