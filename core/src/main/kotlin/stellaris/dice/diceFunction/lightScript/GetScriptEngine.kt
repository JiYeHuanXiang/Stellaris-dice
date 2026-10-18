package stellaris.dice.diceFunction.lightScript

import javax.script.ScriptEngine













object GetScriptEngine {
    fun get(): ScriptEngine? = DiceJsSandbox.create()
}
