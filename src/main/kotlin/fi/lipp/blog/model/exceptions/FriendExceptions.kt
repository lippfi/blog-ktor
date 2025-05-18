package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to send a friend request that already exists.
 */
class FriendRequestAlreadyExistsException : BlogException(
    messageKey = "friend_request_already_exists",
    code = 409
)

/**
 * Exception thrown when a user tries to send a friend request to someone who is already their friend.
 */
class AlreadyFriendsException : BlogException(
    messageKey = "already_friends",
    code = 409
)

/**
 * Exception thrown when a friend request is not found.
 */
class FriendRequestNotFoundException : BlogException(
    messageKey = "friend_request_not_found",
    code = 404
)

/**
 * Exception thrown when a user tries to accept or decline a friend request they did not receive.
 */
class NotRequestRecipientException : BlogException(
    messageKey = "not_request_recipient",
    code = 403
)

/**
 * Exception thrown when a user tries to perform an action that requires friendship with another user.
 */
class NotFriendsException : BlogException(
    messageKey = "not_friends",
    code = 404
)

/**
 * Exception thrown when a user tries to cancel a friend request they did not send.
 */
class NotRequestSenderException : BlogException(
    messageKey = "not_request_sender",
    code = 403
)
