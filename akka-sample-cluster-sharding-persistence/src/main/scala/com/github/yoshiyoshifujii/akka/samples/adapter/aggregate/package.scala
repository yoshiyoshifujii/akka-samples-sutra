package com.github.yoshiyoshifujii.akka.samples.adapter

package object aggregate {

  def throwIllegalStateException[Command, Event, State <: BaseState[Command, Event, State], CommandEffect](
      state: State,
      command: Command
  ): CommandEffect =
    throw new IllegalStateException(s"${state.getClass.getName}[$state], ${command.getClass.getName}[$command]")

}
