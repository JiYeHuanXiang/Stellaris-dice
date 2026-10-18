package stellaris.dice.diceFunction.commands

import onedice.defines.DiceException












interface DiceCommandCallback {


    fun onException(exception: DiceException)









    fun onCommand(command: String, param: String, isProtected: Boolean): Boolean


    fun extraCommands(): ArrayList<String> {
        return ArrayList()
    }
}
