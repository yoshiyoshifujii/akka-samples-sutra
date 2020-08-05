package com.github.yoshiyoshifujii.akka.samples.adapter

package object aggregate {

  trait StateBase[Command, Event, State] {

    protected def applyEventPartial: PartialFunction[Event, State]

    private lazy val applyEventDefault: PartialFunction[Event, State] =
      PartialFunction.fromFunction(evt =>
        throw new IllegalStateException(s"unexpected event [$evt] in state [EmptyState]")
      )

    def applyEvent(event: Event): State =
      (applyEventPartial orElse applyEventDefault)(event)
  }

}
