package com.github.yoshiyoshifujii.akka.samples.adapter.aggregate

import com.github.yoshiyoshifujii.akka.samples.domain.model.ThreadId

object ShardedThreadAggregate
    extends BaseShardedAggregates[
      ThreadId,
      ThreadPersistentAggregate.Command,
      ThreadPersistentAggregate.Idle.type,
      ThreadPersistentAggregate.Stop.type
    ] {
  override protected def typeKeyName: String                       = "Thread"
  override protected def actorName: String                         = ThreadAggregates.name
  override protected def idle: ThreadPersistentAggregate.Idle.type = ThreadPersistentAggregate.Idle
  override protected def stop: ThreadPersistentAggregate.Stop.type = ThreadPersistentAggregate.Stop
}
