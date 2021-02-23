import sbt._

object Dependencies {

  object Cats {
    val catsVersion = "2.0.0"
    val catsCore = "org.typelevel" %% "cats-core" % catsVersion
    val catsEffect = "org.typelevel" %% "cats-effect" % catsVersion
  }

  object Config {
    val pureConfigVersion = "0.12.3"
    val pureConfig = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
  }

  object JWT {
    val jwtScalaVersion = "4.2.0"
    val jwtCirce = "com.pauldijou" %% "jwt-circe" % jwtScalaVersion
  }

  object Testing {
    val scalaTestVersion = "3.1.2"
    val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  }

  object Logging {
    val logbackVersion = "1.2.3"
    val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVersion
  }

  object Circe {
    val circeVersion = "0.13.0"
    val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
    val circeParser = "io.circe" %% "circe-parser" % circeVersion
  }

  object Http4s {
    val http4sVersion = "0.21.16"
    val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion
    val http4sBlaze = "org.http4s" %% "http4s-blaze-server" % http4sVersion
    val http4sBlazeClient =
      "org.http4s" %% "http4s-blaze-client" % http4sVersion
  }

  object Doobie {
    val doobieVersion = "0.9.0"
    val core = "org.tpolecat" %% "doobie-core" % doobieVersion
    val postgres = "org.tpolecat" %% "doobie-postgres" % doobieVersion
    val h2 = "org.tpolecat" %% "doobie-h2" % doobieVersion
    val hikari = "org.tpolecat" %% "doobie-hikari" % doobieVersion
    val specs2 = "org.tpolecat" %% "doobie-specs2" % doobieVersion
  }

  object Fs2 {
    val fs2CoreVersion = "2.5.0"
    val fs2KafkaVersion = "1.3.1"
    val fs2Core = "co.fs2" %% "fs2-core" % fs2CoreVersion
    val fs2Kafka = "com.github.fd4s" %% "fs2-kafka" % fs2KafkaVersion
  }

  object TestContainers {
    val testContainerVersion = "0.39.1"
    val testContainersScalaTest =
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainerVersion % "test"
    val testContainerKafka =
      "com.dimafeng" %% "testcontainers-scala-kafka" % testContainerVersion % "test"

  }
}
