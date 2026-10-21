package stellaris.core.data

import stellaris.core.defines.ResErrorException
import stellaris.core.defines.ResErrorType






















class ResRecursive(
    val resInt: Int = 0,
    val resDetail: String = resInt.toString(),
    val resNumberDetail: String = resInt.toString(),
    val resIntMin: Int = Int.MIN_VALUE,
    val resIntMax: Int = Int.MAX_VALUE,
    val reRoll: (() -> ResRecursive)? = null
) {

    companion object {


        private val EXTREME_EXCEPTION = ResErrorException(ResErrorType.NODE_EXTREME_VAL_INVALID)


        private fun toExtremumFloat(value: Int): Float = when (value) {
            Int.MAX_VALUE -> Float.POSITIVE_INFINITY
            Int.MIN_VALUE -> Float.NEGATIVE_INFINITY
            else -> value.toFloat()
        }











        fun getExtremum(
            a: ResRecursive,
            b: ResRecursive,
            operation: (a: Float, b: Float) -> Float,
            resultCallback: (max: Int, min: Int) -> Unit
        ) {
            val maxA = toExtremumFloat(a.resIntMax)
            val minA = toExtremumFloat(a.resIntMin)
            val maxB = toExtremumFloat(b.resIntMax)
            val minB = toExtremumFloat(b.resIntMin)

            val corners = ArrayList<Float>(4)
            for (pair in arrayOf(maxA to maxB, minA to maxB, minA to minB, maxA to minB)) {
                val value = operation(pair.first, pair.second)
                if (!value.isNaN()) {
                    corners.add(value)
                }
            }
            val max = corners.maxOrNull() ?: throw EXTREME_EXCEPTION
            val min = corners.minOrNull() ?: throw EXTREME_EXCEPTION

            resultCallback(max.toInt(), min.toInt())
        }
    }


    fun clone(): ResRecursive = ResRecursive(
        resInt = resInt,
        resDetail = resDetail,
        resNumberDetail = resNumberDetail,
        resIntMin = resIntMin,
        resIntMax = resIntMax
    )


    fun reRoll(): ResRecursive = reRoll?.invoke() ?: clone()
}
