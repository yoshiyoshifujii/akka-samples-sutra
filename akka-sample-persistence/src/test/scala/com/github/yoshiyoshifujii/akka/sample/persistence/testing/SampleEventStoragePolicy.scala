package com.github.yoshiyoshifujii.akka.sample.persistence.testing

import akka.persistence.testkit._

class SampleEventStoragePolicy extends EventStorage.JournalPolicies.PolicyType {

  var count = 1

  override def tryProcess(persistenceId: String, processingUnit: JournalOperation): ProcessingResult =
    if (count < 10) {
      count += 1
      processingUnit match {
        case ReadEvents(batch) if batch.nonEmpty  => ProcessingSuccess
        case WriteEvents(batch) if batch.size > 1 => ProcessingSuccess
        case ReadSeqNum                           => StorageFailure()
        case DeleteEvents(_)                      => Reject()
        case _                                    => StorageFailure()
      }
    } else {
      ProcessingSuccess
    }
}
