package stellaris.dice.diceFunction.responder

import stellaris.dice.diceFunction.calculation.DiceCalculation






































object Madness {


    enum class Kind(val title: String, val deckName: String) {


        TEMPORARY("临时性疯狂", "临时症状"),


        LONG_TERM("长期性疯狂", "总结症状")
    }


    class Entry(val label: String, val summary: String)






    val REAL_TIME: List<Entry> = listOf(
        Entry("失忆", "只记得最后一次身处安全地点，之后的事一概想不起来"),
        Entry("假性残疾", "心因性失明、失聪或肢体失控"),
        Entry("暴力倾向", "失去理智，对周围（包括同伴）无差别攻击"),
        Entry("偏执", "认定有人监视、背叛，谁都不能信"),
        Entry("重要之人", "把眼前某人误认成对自己重要的人，并按那层关系行事"),
        Entry("昏厥", "当场昏倒"),
        Entry("逃避行为", "不择手段地逃离现场（哪怕丢下同伴）"),
        Entry("歇斯底里", "大笑、哭喊或嘶吼到无法行动"),
        Entry("恐惧症", "新增一项恐惧症，并当场发作"),
        Entry("躁狂症", "新增一项躁狂症，并当场沉溺其中")
    )






    val SUMMARY: List<Entry> = listOf(
        Entry("失忆", "在陌生地方醒来，连自己是谁都想不起来"),
        Entry("被窃", "醒来发现身上贵重物品不翼而飞"),
        Entry("遍体鳞伤", "醒来满身瘀伤，HP 减半（不算重伤）"),
        Entry("暴力倾向", "发作期间大肆破坏，醒来未必记得"),
        Entry("极端信念", "把自己的某个信念以极端、夸张的方式践行"),
        Entry("重要之人", "不顾一切地接近对自己重要的人"),
        Entry("被收容", "在精神病院病房或警局牢房里醒来"),
        Entry("逃避行为", "在很远的地方醒来，甚至不知道自己怎么到的"),
        Entry("恐惧症", "新增一项恐惧症，并小心翼翼地回避它"),
        Entry("躁狂症", "新增一项躁狂症，并在这段时间里沉溺其中")
    )


    class Result(
        val kind: Kind,

        val tableRoll: Int,
        val entry: Entry,

        val durationRoll: Int
    ) {


        val durationText: String
            get() = "1d10=$durationRoll ${if (kind == Kind.TEMPORARY) "轮" else "小时"}"


        val tableSegment: String
            get() = "1d10=$tableRoll"
    }


    fun roll(kind: Kind): Result {
        val tableRoll = DiceCalculation.rollCalculation("1d10").intNumber
        val durationRoll = DiceCalculation.rollCalculation("1d10").intNumber
        val table = if (kind == Kind.TEMPORARY) REAL_TIME else SUMMARY

        val entry = table[(tableRoll - 1).coerceIn(0, table.size - 1)]
        return Result(kind, tableRoll, entry, durationRoll)
    }
}
