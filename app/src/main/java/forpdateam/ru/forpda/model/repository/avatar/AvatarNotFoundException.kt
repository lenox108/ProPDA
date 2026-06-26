package forpdateam.ru.forpda.model.repository.avatar

/**
 * Thrown by [AvatarRepository] when no avatar URL can be resolved for a given user identity.
 *
 * Replaces a raw [NullPointerException] so that callers can distinguish "user has no avatar"
 * from unexpected NPEs in coroutine cancellation, dispatch, or framework callbacks.
 */
class AvatarNotFoundException(
    val avatarId: Int? = null,
    val nick: String? = null,
) : RuntimeException(
    buildString {
        append("No avatar/user by")
        if (avatarId != null) append(" id=").append(avatarId)
        if (nick != null) append(" nick=").append(nick)
    }
)
