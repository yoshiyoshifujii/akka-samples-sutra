package com.github.yoshiyoshifujii.akka.sample.persistence.testing

import akka.persistence.testkit._

class SampleSnapshotStoragePolicy extends SnapshotStorage.SnapshotPolicies.PolicyType {

  var count = 1

  override def tryProcess(persistenceId: String, processingUnit: SnapshotOperation): ProcessingResult =
    if (count < 10) {
      count += 1

      processingUnit match {
        case ReadSnapshot(_, payload) if payload.nonEmpty       => ProcessingSuccess
        case WriteSnapshot(meta, _) if meta.sequenceNr > 10     => ProcessingSuccess
        case DeleteSnapshotsByCriteria(_)                       => StorageFailure()
        case DeleteSnapshotByMeta(meta) if meta.sequenceNr < 10 => ProcessingSuccess
        case _                                                  => StorageFailure()
      }
    } else {
      ProcessingSuccess
    }
}
