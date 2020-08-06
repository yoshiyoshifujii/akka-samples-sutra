package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import com.github.yoshiyoshifujii.akka.samples.domain.model.ThreadId

object ThreadAggregates extends BaseAggregates[ThreadId, ThreadPersistentAggregate.Command] {
  override def name: String = "threads"
}
