package cz.jaro.drawing

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentListTest {
    @Test
    fun allInOne() {
        val list = RecentList<Int>()

        for (a in 0..100) {
            list.add(a)

            val maxSize = (a / 10) + 2
            while (maxSize < list.size)
                list.removeFirst()

            if (a % 40 == 0)
                list.clear()
        }

        assertEquals("Size", list.size, 12)

        for (a in 1..11) {
            list.removeFirst()
        }

        assertEquals("Size", list.size, 1)
        assertEquals("Element", list[0], 100)
    }
}
