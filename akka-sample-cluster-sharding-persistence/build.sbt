val akkaVersion = "2.6.8"

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(
    name := "akka-sample-cluster-sharding-persistence",
    organization := "com.github.yoshiyoshifujii.akka.samples",
    version := "0.1",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed"            % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j"                  % akkaVersion,
        "com.typesafe.akka" %% "akka-persistence-typed"      % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed"          % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,
        "ch.qos.logback"     % "logback-classic"             % "1.2.3" excludeAll (
          ExclusionRule(organization = "org.slf4j")
        ),
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
        "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test,
        "com.typesafe.akka" %% "akka-multi-node-testkit"  % akkaVersion % Test,
        "org.scalatest"     %% "scalatest"                % "3.2.0"     % Test,
        "de.huxhorn.sulky"   % "de.huxhorn.sulky.ulid"    % "8.2.0"
      )
  )
