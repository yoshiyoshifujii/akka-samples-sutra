val AkkaVersion = "2.6.5"

lazy val `akka-samples-discovery` = project
  .in(file("."))
  .settings(
    organization := "com.github.yoshiyoshifujii.akka.samples",
    version := "1.0",
    scalaVersion := "2.13.2",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    ),
    fork in run := false,
    Global / cancelable := false, // ctrl-c
    // disable parallel tests
    parallelExecution in Test := false,
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),
    logBuffered in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )

