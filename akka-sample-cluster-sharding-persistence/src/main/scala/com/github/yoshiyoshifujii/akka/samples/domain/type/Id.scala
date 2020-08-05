package com.github.yoshiyoshifujii.akka.samples.domain.`type`

trait Id {
  lazy val modelName: String = getClass.getName.stripSuffix("Id")
  def asString: String
}
