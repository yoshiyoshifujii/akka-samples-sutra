package com.github.yoshiyoshifujii.akka.sample.persistence.schemaEvolution

object Main {

  sealed abstract class SeatType { def code: String }

  object SeatType {

    case object Window  extends SeatType { override val code: String = "W" }
    case object Aisle   extends SeatType { override val code: String = "A" }
    case object Other   extends SeatType { override val code: String = "O" }
    case object Unknown extends SeatType { override val code: String = ""  }

    def fromString(s: String): SeatType =
      s match {
        case Window.code => Window
        case Aisle.code  => Aisle
        case Other.code  => Other
        case _           => Unknown
      }
  }

  case class SeatReserved(letter: String, row: Int)
//  case class SeatReserved(letter: String, row: Int, seatType: SeatType)


}
