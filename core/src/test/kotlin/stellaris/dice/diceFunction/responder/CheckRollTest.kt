package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.card.CardAbilities
import stellaris.dice.diceFunction.card.PlayerCardData
import stellaris.dice.diceFunction.random.RandomService
import stellaris.dice.diceFunction.template.DiceMessageTemplate
import java.util.Random












class CheckRollTest {


    private class ScriptedRandom(private val units: List<Long>) : Random() {
        private var cursor = 0
        override fun nextLong(): Long {
            val unit = units[cursor % units.size]
            cursor++
            return unit shl 1
        }
    }

    @AfterEach
    fun restore() {
        RandomService.installTestProvider(null)
    }







    @Test
    fun `成功级别 逐档判定`() {
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(1, 60), "掷出 1 是大成功")
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(12, 60), "正好压极难线")
        assertEquals(CheckLevel.HARD_SUCCESS, CheckRule.level(13, 60), "越一档落到困难")
        assertEquals(CheckLevel.HARD_SUCCESS, CheckRule.level(30, 60), "正好压困难线")
        assertEquals(CheckLevel.SUCCESS, CheckRule.level(31, 60), "越一档落到普通成功")
        assertEquals(CheckLevel.SUCCESS, CheckRule.level(60, 60), "正好等于目标")
        assertEquals(CheckLevel.FAILURE, CheckRule.level(61, 60), "越目标即失败")
        assertEquals(CheckLevel.FAILURE, CheckRule.level(99, 60), "高目标下 99 仍只是失败")
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(100, 60), "100 必大失败")
    }


    @Test
    fun `大失败的点数门槛随目标高低变`() {
        assertEquals(CheckLevel.FAILURE, CheckRule.level(95, 40))
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(96, 40), "目标 40 时 96 起算大失败")
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(100, 40))

        assertEquals(CheckLevel.FAILURE, CheckRule.level(96, 50), "目标 50 时 96 不算大失败")
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(100, 50))
    }


    @Test
    fun `极低目标的阈值下限`() {
        assertEquals(CheckLevel.SUCCESS, CheckRule.level(2, 3), "目标 3 时 ⌊3/2⌋=1 → 掷 2 是普通成功")
        assertEquals(CheckLevel.SUCCESS, CheckRule.level(4, 4), "目标 4 时掷 4 是普通成功")
        assertEquals(CheckLevel.HARD_SUCCESS, CheckRule.level(2, 4), "目标 4 时 ⌊4/2⌋=2 → 掷 2 是困难成功")
    }


    @Test
    fun `级别是否算过`() {
        assertTrue(CheckLevel.CRITICAL_SUCCESS.isSuccess)
        assertTrue(CheckLevel.EXTREME_SUCCESS.isSuccess)
        assertTrue(CheckLevel.HARD_SUCCESS.isSuccess)
        assertTrue(CheckLevel.SUCCESS.isSuccess)
        assertTrue(!CheckLevel.FAILURE.isSuccess)
        assertTrue(!CheckLevel.CRITICAL_FAILURE.isSuccess)
    }









    @Test
    fun `房规1 大成功门槛随目标高低变`() {
        val rule = CocHouseRule.ONE
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(3, 60, rule), "满 50：3 是大成功")
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(5, 50, rule), "50 算满 50")
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(6, 60, rule), "6 回到阈值判定")
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(3, 40, rule), "不满 50：3 只是极难成功")
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(1, 40, rule), "不满 50：只有 1 是大成功")

        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(96, 40, rule))
        assertEquals(CheckLevel.FAILURE, CheckRule.level(96, 50, rule))
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(100, 50, rule))
    }


    @Test
    fun `房规2 大成功要看判定结果`() {
        val rule = CocHouseRule.TWO
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(3, 3, rule), "3 ≤ 目标 3 → 大成功")
        assertEquals(CheckLevel.FAILURE, CheckRule.level(4, 3, rule), "4 > 目标 3 → 不是大成功，只算失败")
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(5, 60, rule))
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(96, 60, rule), "96 > 目标 → 大失败")
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(100, 100, rule), "100 恒大失败")
        assertEquals(CheckLevel.SUCCESS, CheckRule.level(99, 100, rule), "99 ≤ 目标 100 → 判定成功，不算大失败")
    }


    @Test
    fun `房规3 无视判定结果`() {
        val rule = CocHouseRule.THREE
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(5, 3, rule), "5 > 目标 3 也报大成功")
        assertEquals(CheckLevel.FAILURE, CheckRule.level(6, 3, rule), "6 起回到判定")
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(96, 100, rule), "96 ≤ 目标 100 也报大失败")
        assertEquals(CheckLevel.SUCCESS, CheckRule.level(95, 100, rule))
    }


    @Test
    fun `房规4 大成功门槛收紧`() {
        val rule = CocHouseRule.FOUR
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(5, 60, rule), "⌊60/10⌋=6 → 1~5 全是大成功")
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(6, 60, rule), "6 不在 1~5 里")
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(5, 40, rule), "⌊40/10⌋=4 → 5 不是大成功")
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(4, 40, rule))
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(1, 8, rule), "⌊8/10⌋=0 → 大成功不可达")

        assertEquals(CheckLevel.FAILURE, CheckRule.level(99, 40, rule), "⌊40/10⌋=4 → 要 ≥100 才大失败")
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(100, 40, rule))
        assertEquals(CheckLevel.FAILURE, CheckRule.level(99, 50, rule), "满 50 时只有 100 大失败")
    }


    @Test
    fun `房规5 大成功与大失败都更严`() {
        val rule = CocHouseRule.FIVE
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(2, 60, rule), "⌊60/5⌋=12 → 1~2 大成功")
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(3, 60, rule))
        assertEquals(CheckLevel.EXTREME_SUCCESS, CheckRule.level(2, 10, rule), "⌊10/5⌋=2 → 2 不小于 2")
        assertEquals(CheckLevel.CRITICAL_SUCCESS, CheckRule.level(1, 10, rule))

        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(99, 60, rule))
        assertEquals(CheckLevel.FAILURE, CheckRule.level(98, 60, rule))
        assertEquals(CheckLevel.CRITICAL_FAILURE, CheckRule.level(96, 40, rule), "不满 50 仍是 96 起")
    }


    @Test
    fun `档位号解析`() {
        assertEquals(CocHouseRule.RULEBOOK, CocHouseRule.parse("0"))
        assertEquals(CocHouseRule.FIVE, CocHouseRule.parse(" 5 "))
        assertNull(CocHouseRule.parse("6"))
        assertNull(CocHouseRule.parse("-1"))
        assertNull(CocHouseRule.parse(""))
        assertNull(CocHouseRule.parse("abc"))
        assertEquals(CocHouseRule.RULEBOOK, CocHouseRule.DEFAULT)
        assertEquals("规则书", CocHouseRule.RULEBOOK.label)
        assertEquals("房规3", CocHouseRule.THREE.label)
    }




    @Test
    fun `检定参数 名字与目标值的两种顺序都认`() {
        CheckArguments.parse("侦查", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertNull(it.explicitTarget, "只写名字时要读卡，不该凭空给个目标")
            assertEquals("", it.reason)
        }
        CheckArguments.parse("侦查50", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertEquals(50, it.explicitTarget)
        }
        CheckArguments.parse("50侦查", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertEquals(50, it.explicitTarget)
        }
        CheckArguments.parse("50", RollRules.RA).let {
            assertEquals("", it.skillName)
            assertEquals(50, it.explicitTarget)
        }
    }






    @Test
    fun `检定参数 带正负号的修饰值`() {
        CheckArguments.parse("侦查+10", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertNull(it.explicitTarget, "带符号是修正值，不是写死目标")
            assertEquals(10, it.targetDelta)
            assertEquals("侦查(+10)", it.displayName("数值"))
        }
        CheckArguments.parse("侦查-10", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertEquals(-10, it.targetDelta)
        }
        val card = cardWith("侦查" to 50).asAbilities()
        assertEquals(
            60,
            CheckArguments.resolveTarget(CheckArguments.parse("侦查+10", RollRules.RA), card)
        )
        assertEquals(
            40,
            CheckArguments.resolveTarget(CheckArguments.parse("侦查-10", RollRules.RA), card)
        )
    }


    @Test
    fun `检定参数 难度前缀`() {
        CheckArguments.parse("困难侦查", RollRules.RA).let {
            assertEquals(CheckArguments.Difficulty.HARD, it.difficulty)
            assertEquals("侦查", it.skillName)
            assertEquals("困难侦查", it.displayName("数值"))
        }
        assertEquals(
            CheckArguments.Difficulty.EXTREME,
            CheckArguments.parse("极难侦查", RollRules.RA).difficulty
        )
        val card = cardWith("侦查" to 60).asAbilities()
        assertEquals(
            30,
            CheckArguments.resolveTarget(CheckArguments.parse("困难侦查", RollRules.RA), card)
        )
        assertEquals(
            12,
            CheckArguments.resolveTarget(CheckArguments.parse("极难侦查", RollRules.RA), card)
        )

        assertEquals(
            35,
            CheckArguments.resolveTarget(CheckArguments.parse("困难侦查+10", RollRules.RA), card)
        )
    }


    @Test
    fun `检定参数 指定卡名`() {
        CheckArguments.parse("夜刃:侦查+10", RollRules.RA).let {
            assertEquals("夜刃", it.cardName)
            assertEquals("侦查", it.skillName)
            assertEquals(10, it.targetDelta)
        }

        assertEquals("夜刃", CheckArguments.parse("夜刃：侦查", RollRules.RA).cardName)
    }


    @Test
    fun `检定参数 次数在后`() {
        CheckArguments.parse("50#2", RollRules.RA).let {
            assertEquals(2, it.repeatTimes)
            assertEquals(50, it.explicitTarget)
            assertNull(it.targetDelta)
        }
        CheckArguments.parse("侦查#3 理由", RollRules.RA).let {
            assertEquals(3, it.repeatTimes)
            assertEquals("侦查", it.skillName)
            assertEquals("理由", it.reason)
        }
        CheckArguments.parse("3斗殴#2", RollRules.RAP).let {
            assertEquals(2, it.repeatTimes)
            assertEquals(3, it.bonusPenaltyCount, "前面的 3 是惩罚骰个数")
            assertEquals("斗殴", it.skillName)
            assertNull(it.explicitTarget)
        }
        assertEquals(1, CheckArguments.parse("侦查", RollRules.RA).repeatTimes)
    }


    @Test
    fun `检定参数 理由可以带空格`() {
        CheckArguments.parse("侦查 仔细 找线索", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertEquals("仔细 找线索", it.reason)
        }
        CheckArguments.parse("侦查50 找线索", RollRules.RA).let {
            assertEquals("侦查", it.skillName)
            assertEquals(50, it.explicitTarget)
            assertEquals("找线索", it.reason)
        }
    }


    @Test
    fun `奖励惩罚个数 与目标值不混`() {
        CheckArguments.parse("50", RollRules.RAP).let {
            assertEquals(1, it.bonusPenaltyCount, "`.rap 50` 的 50 是目标值，不是 50 个惩罚骰")
            assertEquals(50, it.explicitTarget)
        }
        CheckArguments.parse("3斗殴", RollRules.RAP).let {
            assertEquals(3, it.bonusPenaltyCount)
            assertEquals("斗殴", it.skillName)
            assertNull(it.explicitTarget)
        }

        CheckArguments.parse("2 侦查", RollRules.RAB).let {
            assertEquals(2, it.bonusPenaltyCount)
            assertEquals("侦查", it.skillName)
            assertNull(it.explicitTarget)
        }

        CheckArguments.parse("斗殴3", RollRules.RA).let {
            assertEquals(1, it.bonusPenaltyCount)
            assertEquals(3, it.explicitTarget)
        }
    }

    @Test
    fun `目标值加理由 不被误读成奖励骰个数`() {
        CheckArguments.parse("50 理由", RollRules.RAP).let {
            assertEquals(1, it.bonusPenaltyCount, "50 是目标值，不是 50 个惩罚骰")
            assertEquals(50, it.explicitTarget)
            assertEquals("理由", it.reason)
        }
        CheckArguments.parse("10 侦查", RollRules.RAB).let {
            assertEquals(1, it.bonusPenaltyCount, "10 超出 1~9，按目标值处理")
            assertEquals(10, it.explicitTarget)
            assertEquals("侦查", it.reason)
        }
        CheckArguments.parse("2 侦查", RollRules.RAB).let {
            assertEquals(2, it.bonusPenaltyCount)
            assertEquals("侦查", it.skillName)
        }
        CheckArguments.parse("1 困难侦查+10 黑暗环境", RollRules.RAP).let {
            assertEquals(1, it.bonusPenaltyCount)
            assertEquals(CheckArguments.Difficulty.HARD, it.difficulty)
            assertEquals("侦查", it.skillName)
            assertEquals(10, it.targetDelta)
            assertEquals("黑暗环境", it.reason)
        }
    }

    @Test
    fun `rab 50 理由 端到端按目标值和理由处理`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1)))
        val text = checkReply("50 理由", RollRules.RAP)
        assertTrue(text.contains("1p1"), "只该有一个惩罚骰：$text")
        assertTrue(text.contains("/50"), "50 是目标值：$text")
        assertTrue(text.contains("理由"), "理由要回显：$text")
    }


    @Test
    fun `重复标记被剥掉`() {
        CheckArguments.parse("#2斗殴", RollRules.RA).let {
            assertEquals("斗殴", it.skillName)
        }
    }


    @Test
    fun `卡上没有这项技能时按默认值 1`() {
        val empty = CardAbilities.EMPTY
        assertEquals(1, CheckArguments.resolveTarget(CheckArguments.parse("侦查", RollRules.RA), empty))

        val card = cardWith("侦查" to 70)
        assertEquals(
            30,
            CheckArguments.resolveTarget(CheckArguments.parse("侦查30", RollRules.RA), card.asAbilities())
        )
        assertEquals(
            70,
            CheckArguments.resolveTarget(CheckArguments.parse("侦查", RollRules.RA), card.asAbilities())
        )

        assertNull(CheckArguments.resolveTarget(CheckArguments.parse("", RollRules.RA), card.asAbilities()))
    }




    @Test
    fun `检定 写死目标 成功`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertEquals(
            "甲进行的数值检定结果为:1d100=40/50 成功\n",
            checkReply("50", RollRules.RA)
        )
    }


    @Test
    fun `检定 读卡技能值 带理由`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(14)))

        assertEquals(
            "由于找线索，甲进行的侦查检定结果为:1d100=15/50 困难成功\n",
            checkReply("侦查 找线索", RollRules.RA, card = cardWith("侦查" to 50))
        )
    }


    @Test
    fun `检定 失败与大失败`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(59)))
        assertEquals(
            "甲进行的侦查检定结果为:1d100=60/25 失败\n",
            checkReply("侦查", RollRules.RA, card = cardWith("侦查" to 25))
        )
        RandomService.installTestProvider(ScriptedRandom(listOf(99)))
        assertEquals(
            "甲进行的侦查检定结果为:1d100=100/25 大失败\n",
            checkReply("侦查", RollRules.RA, card = cardWith("侦查" to 25))
        )
    }


    @Test
    fun `规则书检定同口径`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertEquals(
            "甲进行的数值检定结果为:1d100=40/50 成功\n",
            checkReply("50", RollRules.RC)
        )
    }





    @Test
    fun `纯净模式 只回结果与级别`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertEquals("1d100=40/50 成功", checkReply("50", RollRules.RA, simplify = true))
    }








    @Test
    fun `奖励惩罚骰 骰式是顶层算子`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertTrue(checkReply("50", RollRules.RAB).contains("1b1=40/50 成功"), "奖励骰该是 1b1")
        assertTrue(checkReply("50", RollRules.RCP).contains("1p1="), "惩罚骰该是 1p1")
        assertTrue(
            checkReply("3斗殴", RollRules.RAP).contains("1p3="),
            "`.rap3斗殴` 的 3 是个数：${checkReply("3斗殴", RollRules.RAP)}"
        )
    }





    @Test
    fun `ra 读档位 rc 恒按规则书`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(4)))

        assertEquals(
            "甲进行的数值检定结果为:1d100=5/3 大成功\n",
            checkReply("3", RollRules.RA, houseRule = CocHouseRule.THREE)
        )
        assertEquals(
            "甲进行的数值检定结果为:1d100=5/3 失败\n",
            checkReply("3", RollRules.RC, houseRule = CocHouseRule.THREE)
        )

        assertTrue(checkReply("3", RollRules.RAH, houseRule = CocHouseRule.THREE).contains("大成功"))
        assertTrue(checkReply("3", RollRules.RCH, houseRule = CocHouseRule.THREE).contains("失败"))
    }


    @Test
    fun `参数不足时回中性提示`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertEquals(DefaultSentences.sentenceIllegal, checkReply("", RollRules.RA))
    }


    @Test
    fun `检定 次数在后 骰两次`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39, 59)))
        assertEquals(
            "甲进行的 数值 多重鉴定结果:\n1d100=40/50 成功\n1d100=60/50 失败\n",
            checkReply("50#2", RollRules.RA)
        )
    }


    @Test
    fun `检定 困难前缀减半目标`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertEquals(
            "甲进行的困难侦查检定结果为:1d100=1/30 大成功\n",
            checkReply("困难侦查", RollRules.RA, card = cardWith("侦查" to 60))
        )
    }


    @Test
    fun `检定 从指定卡读技能值`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertEquals(
            "甲进行的侦查检定结果为:1d100=40/50 成功\n",
            checkReply(
                "副卡:侦查",
                RollRules.RA,
                card = cardWith("侦查" to 10),
                otherCards = mapOf("副卡" to cardWith("侦查" to 50))
            )
        )
    }


    @Test
    fun `奖励骰个数超限回中性提示`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertEquals(
            DefaultSentences.sentenceIllegal,
            checkReply("12斗殴", RollRules.RAB),
            "12 个奖励骰超过引擎上限 9"
        )
    }



    private fun checkReply(
        param: String,
        rule: RollRules,
        card: PlayerCardData? = null,
        simplify: Boolean = false,
        defaultDice: Int = 100,
        houseRule: CocHouseRule = CocHouseRule.DEFAULT,
        otherCards: Map<String, PlayerCardData> = emptyMap()
    ): String {
        val template = DiceMessageTemplate()
        RespondContext.seedTemplate(template, senderNick = "甲", senderId = "10001", groupId = "20002")
        val context = RespondContext(
            param = param,
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = template,
            sentences = DefaultSentences,
            defaultDiceNumber = defaultDice,
            simplify = simplify,
            card = card?.asAbilities() ?: CardAbilities.EMPTY,
            cardProvider = { name -> otherCards[name]?.asAbilities() ?: CardAbilities.EMPTY },
            houseRule = houseRule
        )
        return template.buildAll(DiceResponder.check(context, rule))
    }

    private fun cardWith(vararg properties: Pair<String, Int>): PlayerCardData {
        val card = PlayerCardData.create("10001", "测试卡")
        properties.forEach { card.setProperty(it.first, it.second) }
        return card
    }
}
