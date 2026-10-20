package onedice.function

import java.util.Random

object BoundedRandom {


    fun intInRange(random: Random, min: Int, max: Int): Int {
        if (min > max) throw ArithmeticException("bound must be positive: min=$min max=$max")
        val span = max.toLong() - min.toLong()
        return (min.toLong() + boundedLong(random, span + 1L)).toInt()
    }


    fun longInRange(random: Random, min: Long, max: Long): Long {
        if (min > max) throw ArithmeticException("bound must be positive: min=$min max=$max")
        val span = max - min
        if (span < 0) {
            while (true) {
                val r = random.nextLong()
                if (r in min..max) return r
            }
        }
        if (span == Long.MAX_VALUE) {
            return min + (random.nextLong() ushr 1)
        }
        return min + boundedLong(random, span + 1L)
    }


    fun boundedLong(random: Random, bound: Long): Long {
        if (bound <= 0L) throw ArithmeticException("bound must be positive: bound=$bound")
        while (true) {
            val bits = random.nextLong() ushr 1
            val value = bits % bound

            if (bits - value + (bound - 1L) >= 0) return value
        }
    }
}
