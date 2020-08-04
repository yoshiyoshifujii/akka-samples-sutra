package com.github.yoshiyoshifujii.akka.samples.domain.model

case class ThreadName(value: String) {
  require(value.nonEmpty, "thread name is empty")
}
