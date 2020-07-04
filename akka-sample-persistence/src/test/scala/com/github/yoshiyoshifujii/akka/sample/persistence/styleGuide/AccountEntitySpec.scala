package com.github.yoshiyoshifujii.akka.sample.persistence.styleGuide

import akka.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class AccountEntitySpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("""
         |akka {
         |  actor {
         |    serializers {
         |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
         |    }
         |    serialization-bindings {
         |      "com.github.yoshiyoshifujii.akka.sample.persistence.serialization.CborSerializable" = jackson-cbor
         |    }
         |  }
         |}
         |""".stripMargin).withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyWordSpecLike
    with LogCapturing {

  "AccountEntity" must {
    import AccountEntity._

    "success" in {
      val accountNumber = "account-number-1"

      val eventSourcedTestKit = EventSourcedBehaviorTestKit[Command, Event, Account](
        system,
        AccountEntity(accountNumber, PersistenceId.ofUniqueId(accountNumber))
      )

      val operationResultProbe1 = testKit.createTestProbe[OperationResult]
      val result1               = eventSourcedTestKit.runCommand(CreateAccount(operationResultProbe1.ref))
      result1.eventOfType[AccountCreated.type]
      result1.stateOfType[OpenedAccount].balance shouldBe Zero
      operationResultProbe1.expectMessageType[Confirmed.type]

      val result2 = eventSourcedTestKit.runCommand(Deposit(1_000_000, operationResultProbe1.ref))
      result2.eventOfType[Deposited].amount shouldBe 1_000_000
      result2.stateOfType[OpenedAccount].balance shouldBe 1_000_000
      operationResultProbe1.expectMessageType[Confirmed.type]

      val result3 = eventSourcedTestKit.runCommand(Withdraw(500_000, operationResultProbe1.ref))
      result3.eventOfType[Withdrawn].amount shouldBe 500_000
      result3.stateOfType[OpenedAccount].balance shouldBe 500_000
      operationResultProbe1.expectMessageType[Confirmed.type]

      val result4 = eventSourcedTestKit.runCommand(Withdraw(500_001, operationResultProbe1.ref))
      result4.hasNoEvents should be(true)
      result4.stateOfType[OpenedAccount].balance shouldBe 500_000
      operationResultProbe1.expectMessageType[Rejected]

      val currentBalanceProbe1 = testKit.createTestProbe[CurrentBalance]

      val result5 = eventSourcedTestKit.runCommand(GetBalance(currentBalanceProbe1.ref))
      result5.hasNoEvents should be(true)
      result5.stateOfType[OpenedAccount].balance shouldBe 500_000
      currentBalanceProbe1.expectMessageType[CurrentBalance].balance shouldBe 500_000

      val result6 = eventSourcedTestKit.runCommand(Withdraw(500_000, operationResultProbe1.ref))
      result3.eventOfType[Withdrawn].amount shouldBe 500_000
      result6.stateOfType[OpenedAccount].balance shouldBe 0
      operationResultProbe1.expectMessageType[Confirmed.type]

      val result7 = eventSourcedTestKit.runCommand(CloseAccount(operationResultProbe1.ref))
      result7.eventOfType[AccountClosed.type]
      result7.stateOfType[ClosedAccount.type]
      operationResultProbe1.expectMessageType[Confirmed.type]

    }

  }

}
