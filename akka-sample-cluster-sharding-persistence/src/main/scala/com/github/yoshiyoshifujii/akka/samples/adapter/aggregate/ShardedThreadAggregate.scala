package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import com.github.yoshiyoshifujii.akka.samples.domain.model.ThreadId

object ShardedThreadAggregate
    extends BaseShardedAggregates[
      ThreadId,
      ThreadPersistentAggregate.Command,
      ThreadPersistentAggregate.Idle.type,
      ThreadPersistentAggregate.Stop.type
    ] {

  override val name: String                              = "threads"
  override protected val typeKeyName: String                       = "Threads"
  override protected val actorName: String                         = ThreadAggregates.name
  override protected val idle: ThreadPersistentAggregate.Idle.type = ThreadPersistentAggregate.Idle
  override protected val stop: ThreadPersistentAggregate.Stop.type = ThreadPersistentAggregate.Stop
}
