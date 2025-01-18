package fi.lipp.blog.model.exceptions

abstract class BlogException(override val message: String, val code: Int) : Exception(message) {
    override fun equals(other: Any?): Boolean {
        return other is BlogException && other.message == this.message && other.code == this.code
    }
    
    override fun hashCode(): Int {
        return 31 * message.hashCode() + code.hashCode()
    }
}