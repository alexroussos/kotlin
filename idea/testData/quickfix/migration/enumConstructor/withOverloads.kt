// "Change to short enum entry super constructor" "true"

enum class SimpleEnum(val z: String = "xxx") {
    FIRST: SimpleEnum()<caret> {
        override fun foo(): String = "abc"
    },
    SECOND() {
        override fun foo(): String = "xyz"
    },
    LAST("13");

    open fun foo(): String = z
}