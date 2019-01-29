package cz.jaro.drawing

class RecentList<T> {

    private var values = kotlin.arrayOfNulls<Any>(1)
    private var from: Int = 0
    private var to: Int = 0

/*
                   0   1   2   3                 Index used in the get/set functions
           0   1   2   3   4   5   6   7   8     Index in the values array
         -------------------------------------
Values   |   |   | A | B | C | D |   |   |   |
         -------------------------------------
                   ^           ^
                   |           |
                   From        To

Invariant: There is always at least one null value in the values array.
*/

    fun add(element: T) {
        if (size == values.size - 1)
            expandArray()

        val to2 = if (to < values.size - 1) to + 1 else 0

        values[to] = element
        to = to2
    }

    fun removeFirst() {
        if (isEmpty())
            throw IllegalStateException("The list is empty")

        if (size < values.size / 4)
            shrinkArray()

        val from2 = if (from < values.size - 1) from + 1 else 0

        values[from] = null
        from = from2
    }

    fun clear() {
        if (from <= to) {
            for (i in from until to) {
                values[i] = null
            }
        } else {
            for (i in from until values.size) {
                values[i] = null
            }
            for (i in 0 until to) {
                values[i] = null
            }
        }

        from = 0
        to = 0
    }

    operator fun get(index: Int): T {
        if (index < 0)
            throw IndexOutOfBoundsException("Index $index may not be negative")
        if (size <= index)
            throw IndexOutOfBoundsException("Index $index is bigger than the array size $size")

        var i = from + index
        if (values.size < i) i -= values.size

        return values[i] as T
    }

    operator fun set(index: Int, element: T) {
        if (index < 0)
            throw IndexOutOfBoundsException("Index $index may not be negative")
        if (size <= index)
            throw IndexOutOfBoundsException("Index $index is bigger than the array size $size")

        var i = from + index
        if (values.size < i) i -= values.size

        values[i] = element
    }

    fun isEmpty(): Boolean {
        return to == from
    }

    val size: Int
        get() {
            val size = to - from
            return if (0 <= size) size else size + values.size
        }

    private fun expandArray() {
        val values2 = kotlin.arrayOfNulls<Any>(2 * values.size)
        copyAndUse(values2)
    }

    private fun shrinkArray() {
        val values2 = kotlin.arrayOfNulls<Any>(values.size / 2)
        copyAndUse(values2)
    }

    private fun copyAndUse(values2: Array<Any?>) {
        var j = 0
        if (from <= to) {
            for (i in from until to) {
                values2[j++] = values[i]
            }
        } else {
            for (i in from until values.size) {
                values2[j++] = values[i]
            }
            for (i in 0 until to) {
                values2[j++] = values[i]
            }
        }

        to = size
        values = values2
        from = 0
    }

    override fun toString(): String {
        val str = StringBuilder()

//        var first = true
//        str.append("[")
//        if (from <= to) {
//            for (i in from until to) {
//                if (first) str.append(", ")
//                first = false
//                str.append(values[i])
//            }
//        } else {
//            for (i in from until values.size) {
//                if (first) str.append(", ")
//                first = false
//                str.append(values[i])
//            }
//            for (i in 0 until to) {
//                if (first) str.append(", ")
//                first = false
//                str.append(values[i])
//            }
//        }
//        str.append("]")

        str.append("[")
        for (i in 0 until values.size) {
            if (0 < i) str.append(", ")
            if (i == from) str.append(">>> ")
            if (i == to) str.append(" <<<")
            str.append(values[i])
        }
        str.append("]")

        return str.toString()
    }
}
